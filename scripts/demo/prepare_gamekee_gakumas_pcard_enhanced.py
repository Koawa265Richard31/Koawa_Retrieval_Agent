#!/usr/bin/env python3
"""Enhance GameKee Gakumas P-card corpus with release time and card arts.

For every P-card document (detected by the ``fight-info`` component), this
script rewrites the markdown so each chunk can answer card questions well:

- ``实装时间`` (card release time) resolved from the card-art CDN path date
  (GameKee CDN uses 0-based months; verified against game launch 2024-05-16).
- ``卡面（觉醒前）`` / ``卡面（觉醒后）`` labelled card arts extracted from the
  ``tab-info`` component ("卡图" / "卡图（觉醒后）") so answers return both forms.
- ``头像缩略图`` (256x256 character icon) as a compact thumbnail.
- Existing sections (角色卡信息 / 数据信息 / スキルカード / Pアイテム /
  Pアイドルアビリティ) are kept as rendered by ``render_document``.

Output goes to a fresh corpus directory (markdown + semantic chunks +
manifest with ``releaseDate`` / ``sourceTime``), import-ready.
"""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import re
import sys
from pathlib import Path
from typing import Any

from prepare_gamekee_gakumas_characters import (
    PAGE_URL,
    absolute_url,
    content_json_to_markdown,
)
from prepare_gamekee_gakumas_semantic_corpus import IMAGE_RE, semantic_chunks


CDN_DATE_RE = re.compile(r"/(\d{4})/(\d{1,2})/(\d{1,2})/")


def card_release_date(raw: dict[str, Any]) -> str | None:
    """Resolve the card release date from the card-art CDN path (0-based month).

    GameKee's CDN path uses 0-based months: ``2024/4/16`` is 2024-05-16, the
    day the game launched (the character icon date). Card arts are uploaded on
    the day the card debuts, so the first card-art date is the release time.
    """
    thumbs = [absolute_url(each) for each in (raw.get("thumb_list") or [])]
    urls = thumbs or []
    # Prefer the labelled tab-info art (觉醒前), then thumb_list.
    tab_art = extract_tab_art(raw.get("content_json") or "")
    if tab_art.get("pre"):
        urls.insert(0, tab_art["pre"])
    for url in urls:
        match = CDN_DATE_RE.search(url or "")
        if not match:
            continue
        year, month0, day = int(match.group(1)), int(match.group(2)), int(match.group(3))
        if 1 <= month0 + 1 <= 12 and 1 <= day <= 31 and year >= 2024:
            return f"{year}-{month0 + 1:02d}-{day:02d}"
    return None


def extract_tab_art(raw: str) -> dict[str, str]:
    """Extract 觉醒前/觉醒后 card art URLs from the tab-info component."""
    result: dict[str, str] = {"pre": "", "post": ""}
    if not raw:
        return result
    try:
        parsed = json.loads(raw)
    except Exception:
        return result
    nodes = parsed.get("children") if isinstance(parsed, dict) else parsed

    def walk(node: Any) -> None:
        if isinstance(node, dict):
            if node.get("type") == "tab-info":
                for tab in (node.get("data") or {}).get("tabList") or []:
                    title = str(tab.get("title") or "")
                    content = tab.get("content") or []
                    urls = [absolute_url(u) for u in content if isinstance(u, str) and u]
                    if urls:
                        if "觉醒后" in title:
                            result["post"] = urls[0]
                        elif "卡图" in title:
                            result["pre"] = urls[0]
            for value in node.values():
                walk(value)
        elif isinstance(node, list):
            for value in node:
                walk(value)

    walk(nodes)
    return result


def is_p_card(raw: dict[str, Any]) -> bool:
    return '"type":"fight-info"' in str(raw.get("content_json") or "")


def build_enhanced_markdown(raw: dict[str, Any], *, fetched_at: str, release_date: str | None) -> tuple[str, list[str]]:
    content_id = int(raw["id"])
    title = str(raw["title"]).strip()
    source_url = PAGE_URL.format(content_id=content_id)
    updated_at = dt.datetime.fromtimestamp(int(raw.get("updated_at") or 0), tz=dt.timezone.utc).isoformat()
    thumb_urls = [absolute_url(each) for each in (raw.get("thumb_list") or []) if absolute_url(each)]
    tab_art = extract_tab_art(raw.get("content_json") or "")
    body = content_json_to_markdown(raw.get("content_json") or "", profile_only=False)

    lines = [
        f"# {title}",
        f"来源：{source_url}",
        f"GameKee内容ID：{content_id}",
    ]
    if release_date:
        lines.append(f"实装时间：{release_date}")
    lines.extend([
        f"页面更新时间：{updated_at}",
        f"抓取时间：{fetched_at}",
        "采集范围：角色资料页；默认不采集评论区、社区帖、外链剧情正文。",
    ])

    # Card arts with explicit 觉醒前/觉醒后 labels.
    arts: list[tuple[str, str]] = []
    if tab_art.get("pre"):
        arts.append(("卡面（觉醒前）", tab_art["pre"]))
    if tab_art.get("post"):
        arts.append(("卡面（觉醒后）", tab_art["post"]))
    # Fallback: thumb_list arts (large 1080x1920 images) if tab-info missing.
    if not arts:
        for url in thumb_urls:
            if "w_1080" in url or "h_1920" in url:
                arts.append(("卡面", url))
                if len(arts) >= 2:
                    break
    for label, url in arts:
        lines.append(f"## {label}\n\n![{title} {label}]({url})")

    # Thumbnail: the 256x256 character icon.
    icon = next((u for u in thumb_urls if "w_256" in u), None)
    if icon:
        lines.append(f"## 头像缩略图\n\n![{title} 头像]({icon})")

    if body:
        lines.append(body)
    markdown = "\n\n".join(lines).strip() + "\n"
    images = list(dict.fromkeys(re.findall(r"!\[[^\]]*]\((https?://[^)]+)\)", markdown)))
    return markdown, images


def build(args: argparse.Namespace) -> None:
    source_manifest = json.loads((args.input / "manifest.json").read_text(encoding="utf-8"))
    args.output.mkdir(parents=True, exist_ok=True)
    fetched_at = str(source_manifest.get("fetchedAt") or "")
    documents: list[dict[str, Any]] = []
    skipped = 0
    for item in source_manifest.get("documents", []):
        raw = json.loads((args.input / item["rawDetail"]).read_text(encoding="utf-8"))
        if not is_p_card(raw):
            skipped += 1
            continue
        release_date = card_release_date(raw)
        markdown, images = build_enhanced_markdown(raw, fetched_at=fetched_at, release_date=release_date)
        file_name = str(item["file"])
        (args.output / file_name).write_text(markdown, encoding="utf-8")

        prefix = "\n".join((
            "类型：p_card",
            f"标题：{item['title']}",
            f"实装时间：{release_date or '未知'}",
            f"GameKee内容ID：{item['contentId']}",
            f"来源：{item['sourceUrl']}",
        ))
        chunks = semantic_chunks(markdown, prefix, "p_card", args.max_chunk_chars, args.overlap_chars)
        chunk_file = Path(file_name).with_suffix(".semantic-chunks.json").name
        (args.output / chunk_file).write_text(json.dumps(chunks, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        source_time = f"{release_date}T00:00:00+00:00" if release_date else None
        documents.append({
            **item,
            "pageType": "p_card",
            "releaseDate": release_date,
            "sourceTime": source_time,
            "semanticChunksFile": chunk_file,
            "semanticChunkCount": len(chunks),
            "images": images,
            "sha256": hashlib.sha256(markdown.encode("utf-8")).hexdigest(),
        })
        print(f"enhanced {item['contentId']} {item['title']} release={release_date} chunks={len(chunks)}")

    (args.output / "manifest.json").write_text(json.dumps({
        "schemaVersion": 3,
        "source": "gamekee",
        "gameAlias": "gakumas",
        "derivedFrom": str(args.input / "manifest.json"),
        "enhancement": "p_card: release time from card-art CDN date + labelled 觉醒前/觉醒后 arts + thumbnail",
        "documents": documents,
    }, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"prepared {len(documents)} P-card documents (skipped {skipped}) in {args.output}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, default=Path("output/gamekee-gakumas-wiki"))
    parser.add_argument("--output", type=Path, default=Path("output/gamekee-gakumas-pcard-v1"))
    parser.add_argument("--max-chunk-chars", type=int, default=1800)
    parser.add_argument("--overlap-chars", type=int, default=120)
    return parser.parse_args()


if __name__ == "__main__":
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="backslashreplace")
    build(parse_args())
