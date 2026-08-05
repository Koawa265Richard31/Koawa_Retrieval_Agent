#!/usr/bin/env python3
"""Fetch selected GameKee Gakumas character pages and prepare KB chunks.

The script intentionally targets an explicit allow-list of content ids instead
of crawling the wiki. It keeps image URLs as Markdown images so retrieved chunks
can be rendered by the existing chat Markdown renderer.
"""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import re
import time
from pathlib import Path
from typing import Any

import requests


GAME_ALIAS = "gakumas"
DETAIL_API = "https://www.gamekee.com/v1/content/detail/{content_id}"
PAGE_URL = "https://www.gamekee.com/gakumas/{content_id}.html"
DEFAULT_CONTENT_IDS = [
    623218,
    623555,
    623551,
    624398,
    624399,
    624402,
    618476,
    624400,
    618473,
    623552,
    622416,
    624860,
    681855,
]
STOP_SECTION_KEYWORDS = ("角色MV", "相关漫画", "游戏内单格漫画", "相关剧情", "四格漫画")
USER_AGENT = "ragent-gamekee-gakumas-character-kb/1.0"


def absolute_url(url: str | None) -> str:
    if not url:
        return ""
    url = url.strip()
    if url.startswith("//"):
        return "https:" + url
    return url


def normalize_ws(text: str) -> str:
    return re.sub(r"\s+", " ", text).strip()


def safe_filename(index: int, title: str, content_id: int, suffix: str = ".md") -> str:
    stem = "".join(ch if ch.isalnum() else "-" for ch in title).strip("-")
    if not stem:
        stem = str(content_id)
    return f"{index:02d}-{content_id}-{stem}{suffix}"


def fetch_detail(session: requests.Session, content_id: int) -> dict[str, Any]:
    response = session.get(
        DETAIL_API.format(content_id=content_id),
        headers={
            "User-Agent": USER_AGENT,
            "game-alias": GAME_ALIAS,
            "Referer": PAGE_URL.format(content_id=content_id),
        },
        timeout=30,
    )
    response.raise_for_status()
    payload = response.json()
    if payload.get("code") != 0:
        raise RuntimeError(f"GameKee API failed for {content_id}: {payload}")
    data = payload.get("data") or {}
    if not data.get("title"):
        raise RuntimeError(f"GameKee API returned empty title for {content_id}")
    return data


def node_text(node: Any, *, include_images: bool = True) -> str:
    if node is None:
        return ""
    if isinstance(node, str):
        return node
    if isinstance(node, list):
        return "".join(node_text(each, include_images=include_images) for each in node)
    if not isinstance(node, dict):
        return ""

    node_type = node.get("type")
    if node_type == "image":
        src = absolute_url(node.get("src"))
        if not include_images or not src:
            return ""
        alt = normalize_ws(str(node.get("alt") or "图片"))
        return f"![{alt}]({src})"
    if node_type == "link":
        label = normalize_ws(node_text(node.get("children"), include_images=False)) or node.get("url") or "链接"
        url = absolute_url(node.get("url"))
        return f"[{label}]({url})" if url else label
    if "text" in node:
        return str(node.get("text") or "")
    return node_text(node.get("children"), include_images=include_images)


def relation_info_to_lines(data: dict[str, Any]) -> list[str]:
    title = normalize_ws(str(data.get("title") or ""))
    lines = [f"### {title}"] if title else []
    for group in data.get("list") or []:
        group_title = normalize_ws(node_text((group.get("title") or {}).get("data"), include_images=False))
        if group_title:
            lines.append(f"- {group_title}")
        for item in group.get("content") or []:
            name = normalize_ws(str(item.get("name") or ""))
            href = absolute_url(item.get("jumpHref"))
            avatar = absolute_url(item.get("avatar"))
            if name and href:
                lines.append(f"  - [{name}]({href})")
            elif name:
                lines.append(f"  - {name}")
            if avatar:
                lines.append(f"    ![{name or '相关图片'}]({avatar})")
    return lines


def simple_editor_text(value: Any) -> str:
    if isinstance(value, dict) and "data" in value:
        return normalize_ws(node_text(value.get("data")))
    return normalize_ws(node_text(value))


def skill_info_to_lines(data: dict[str, Any]) -> list[str]:
    title = simple_editor_text(data.get("title")) or normalize_ws(str(data.get("title") or ""))
    lines = [f"## {title}"] if title else []
    for skill in data.get("skillList") or []:
        name = simple_editor_text(skill.get("name"))
        label = normalize_ws(str(skill.get("label") or ""))
        desc = simple_editor_text(skill.get("desc"))
        more = simple_editor_text(skill.get("moreInfo"))
        more_label = normalize_ws(str(skill.get("moreBtn") or ""))
        if name:
            lines.append(f"### {name}")
        if label:
            lines.append(f"- 标签 | {label}")
        if desc:
            lines.append(f"- 效果 | {desc}")
        if more:
            lines.append(f"- {more_label or '强化效果'} | {more}")
    return lines


def fight_info_to_lines(data: dict[str, Any]) -> list[str]:
    title = normalize_ws(str(data.get("title") or "数据信息"))
    lines = [f"## {title}"]
    for level in data.get("levelList") or []:
        level_name = normalize_ws(str(level.get("level") or ""))
        values: list[str] = []
        for item in level.get("levelInfoList") or []:
            key = simple_editor_text(item.get("title"))
            value = simple_editor_text(item.get("content"))
            if key and value:
                values.append(f"{key} | {value}")
        if values:
            lines.append(f"### {level_name}" if level_name else "### 数值")
            lines.extend(f"- {value}" for value in values)
    return lines


def character_profile_to_lines(data: dict[str, Any]) -> list[str]:
    lines: list[str] = []
    title = normalize_ws(str(data.get("title") or "个人简介"))
    name = simple_editor_text(data.get("name"))
    if title:
        lines.append(f"## {title}")
    if name:
        lines.append(f"- 角色名 | {name}")
    for idx, image in enumerate(data.get("imageList") or data.get("imagesList") or [], 1):
        url = absolute_url(image if isinstance(image, str) else image.get("url") or image.get("src"))
        if url:
            lines.append(f"![{name or '角色'} 图片 {idx}]({url})")
    attrs: list[str] = []
    for attr in data.get("attrList") or []:
        key = simple_editor_text(attr.get("title"))
        value = simple_editor_text(attr.get("content"))
        if key and value:
            attrs.append(f"{key} | {value}")
    if attrs:
        lines.extend(f"- {attr}" for attr in attrs)
    desc_title = simple_editor_text(data.get("descTitle"))
    desc = simple_editor_text(data.get("desc"))
    if desc_title:
        lines.append(f"## {desc_title}")
    if desc:
        lines.append(desc)
    return lines


def table_to_lines(table: dict[str, Any], *, profile_only: bool) -> tuple[list[str], bool]:
    lines: list[str] = []
    should_stop = False
    for row in table.get("children") or []:
        if row.get("type") != "table-row":
            continue
        cells: list[str] = []
        for cell in row.get("children") or []:
            if cell.get("isMerged"):
                continue
            text = normalize_ws(node_text(cell.get("children")))
            for embedded in cell.get("data") or []:
                if embedded.get("type") == "relation-info" and not profile_only:
                    lines.extend(relation_info_to_lines(embedded.get("data") or {}))
            if text:
                cells.append(text)
        if not cells:
            continue
        row_text = " | ".join(cells)
        if profile_only and any(keyword in row_text for keyword in STOP_SECTION_KEYWORDS):
            should_stop = True
            break
        if len(cells) == 1:
            value = cells[0]
            if value.startswith("!["):
                lines.append(value)
            else:
                lines.append(f"## {value}" if len(value) <= 30 else value)
        else:
            lines.append("- " + row_text)
    return lines, should_stop


def content_json_to_markdown(raw: str, *, profile_only: bool) -> str:
    if not raw:
        return ""
    parsed = json.loads(raw)
    if isinstance(parsed, dict):
        nodes = parsed.get("children") or []
    else:
        nodes = parsed
    lines: list[str] = []
    for node in nodes:
        if not isinstance(node, dict):
            text = normalize_ws(node_text(node))
            if text:
                lines.append(text)
            continue
        node_type = node.get("type")
        if node_type == "illustrated-book":
            # Card pages wrap their visible components in an illustrated-book node.
            for component in node.get("data") or []:
                if not isinstance(component, dict):
                    continue
                component_type = component.get("type")
                component_data = component.get("data") or {}
                if component_type == "character-profile":
                    lines.extend(character_profile_to_lines(component_data))
                elif component_type == "fight-info":
                    lines.extend(fight_info_to_lines(component_data))
                elif component_type == "skill-info":
                    lines.extend(skill_info_to_lines(component_data))
            continue
        if node_type == "character-profile":
            lines.extend(character_profile_to_lines(node.get("data") or {}))
            continue
        if node_type == "table":
            table_lines, should_stop = table_to_lines(node, profile_only=profile_only)
            lines.extend(table_lines)
            if should_stop:
                break
            continue
        if node_type == "skill-info":
            lines.extend(skill_info_to_lines(node.get("data") or {}))
            continue
        if node_type == "fight-info":
            lines.extend(fight_info_to_lines(node.get("data") or {}))
            continue
        text = normalize_ws(node_text(node))
        if text:
            if profile_only and any(keyword in text for keyword in STOP_SECTION_KEYWORDS):
                break
            lines.append(text)
    compact: list[str] = []
    previous = ""
    for line in lines:
        if line and line != previous:
            compact.append(line)
        previous = line
    return "\n\n".join(compact).strip()


def render_document(data: dict[str, Any], *, profile_only: bool, fetched_at: str) -> tuple[str, list[str]]:
    content_id = int(data["id"])
    title = str(data["title"]).strip()
    source_url = PAGE_URL.format(content_id=content_id)
    updated_at = dt.datetime.fromtimestamp(int(data.get("updated_at") or 0), tz=dt.timezone.utc).isoformat()
    thumb_urls = [absolute_url(each) for each in (data.get("thumb_list") or []) if absolute_url(each)]
    body = content_json_to_markdown(data.get("content_json") or "", profile_only=profile_only)

    image_lines = []
    for idx, url in enumerate(dict.fromkeys(thumb_urls), 1):
        image_lines.append(f"![{title} 图片 {idx}]({url})")

    parts = [
        f"# {title}",
        f"来源：{source_url}",
        f"GameKee内容ID：{content_id}",
        f"页面更新时间：{updated_at}",
        f"抓取时间：{fetched_at}",
        "采集范围：角色资料页；默认不采集评论区、社区帖、外链剧情正文。",
    ]
    if image_lines:
        parts.append("## 图片\n\n" + "\n\n".join(image_lines))
    if body:
        parts.append(body)
    markdown = "\n\n".join(parts).strip() + "\n"
    body_images = re.findall(r"!\[[^\]]*]\((https?://[^)]+)\)", markdown)
    return markdown, list(dict.fromkeys([*thumb_urls, *body_images]))


def split_chunks(markdown: str, *, max_chars: int, overlap_chars: int) -> list[str]:
    blocks = [block.strip() for block in re.split(r"\n{2,}", markdown) if block.strip()]
    chunks: list[str] = []
    current = ""
    for block in blocks:
        candidate = f"{current}\n\n{block}".strip() if current else block
        if len(candidate) <= max_chars:
            current = candidate
            continue
        if current:
            chunks.append(current)
        if len(block) <= max_chars:
            current = block
            continue
        for start in range(0, len(block), max_chars - overlap_chars):
            piece = block[start:start + max_chars].strip()
            if piece:
                chunks.append(piece)
        current = ""
    if current:
        chunks.append(current)
    if overlap_chars <= 0 or len(chunks) <= 1:
        return chunks
    with_overlap = [chunks[0]]
    for idx in range(1, len(chunks)):
        tail = chunks[idx - 1][-overlap_chars:].strip()
        with_overlap.append(f"{tail}\n\n{chunks[idx]}" if tail else chunks[idx])
    return with_overlap


def write_corpus(args: argparse.Namespace) -> dict[str, Any]:
    output = args.output
    output.mkdir(parents=True, exist_ok=True)
    fetched_at = dt.datetime.now(dt.timezone.utc).isoformat()
    session = requests.Session()
    documents = []
    for index, content_id in enumerate(args.content_ids, 1):
        data = fetch_detail(session, content_id)
        markdown, images = render_document(data, profile_only=not args.include_related, fetched_at=fetched_at)
        filename = safe_filename(index, data["title"], content_id)
        path = output / filename
        path.write_text(markdown, encoding="utf-8")
        chunks = split_chunks(markdown, max_chars=args.chunk_chars, overlap_chars=args.overlap_chars)
        chunk_filename = safe_filename(index, data["title"], content_id, ".chunks.json")
        (output / chunk_filename).write_text(
            json.dumps(chunks, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
        documents.append({
            "contentId": content_id,
            "title": data["title"],
            "sourceUrl": PAGE_URL.format(content_id=content_id),
            "updatedAt": dt.datetime.fromtimestamp(int(data.get("updated_at") or 0), tz=dt.timezone.utc).isoformat(),
            "file": filename,
            "chunksFile": chunk_filename,
            "chunkCount": len(chunks),
            "images": images,
            "sha256": hashlib.sha256(markdown.encode("utf-8")).hexdigest(),
        })
        print(f"prepared {content_id} {data['title']}: {len(chunks)} chunks, {len(images)} images")
        time.sleep(args.delay_seconds)
    manifest = {
        "schemaVersion": 1,
        "source": "gamekee",
        "gameAlias": GAME_ALIAS,
        "fetchedAt": fetched_at,
        "profileOnly": not args.include_related,
        "documents": documents,
    }
    (output / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return manifest


def api_headers(token: str | None) -> dict[str, str]:
    headers = {}
    if token:
        headers["Authorization"] = token
    return headers


def api_result(response: requests.Response) -> Any:
    response.raise_for_status()
    payload = response.json()
    if payload.get("code") not in (0, "0", None):
        raise RuntimeError(f"API failed: {payload}")
    return payload.get("data")


def import_via_api(args: argparse.Namespace, manifest: dict[str, Any]) -> None:
    if not args.api_base:
        return
    session = requests.Session()
    headers = api_headers(args.token)
    api_base = args.api_base.rstrip("/")
    kb_id = args.kb_id
    if not kb_id and args.create_kb_name:
        payload = {
            "name": args.create_kb_name,
            "embeddingModel": args.embedding_model,
            "collectionName": args.collection_name,
        }
        kb_id = api_result(session.post(
            f"{api_base}/knowledge-base",
            headers={**headers, "Content-Type": "application/json"},
            data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
            timeout=60,
        ))
        print(f"created knowledge base {args.create_kb_name} -> kbId={kb_id}")
    if not kb_id:
        raise SystemExit("--kb-id or --create-kb-name is required with --api-base")
    for doc in manifest["documents"]:
        md_path = args.output / doc["file"]
        chunks = json.loads((args.output / doc["chunksFile"]).read_text(encoding="utf-8"))
        with md_path.open("rb") as file_obj:
            data = {
                "sourceType": "file",
                "processMode": "chunk",
                "chunkStrategy": "fixed_size",
                "chunkConfig": json.dumps({"chunkSize": args.chunk_chars, "overlapSize": args.overlap_chars}, ensure_ascii=False),
            }
            files = {"file": (doc["file"], file_obj, "text/markdown")}
            created = api_result(session.post(
                f"{api_base}/knowledge-base/{kb_id}/docs/upload",
                headers=headers,
                data=data,
                files=files,
                timeout=60,
            ))
        doc_id = created["id"]
        print(f"uploaded document {doc['title']} -> docId={doc_id}")
        for index, content in enumerate(chunks):
            payload = {
                "index": index,
                "content": content,
            }
            api_result(session.post(
                f"{api_base}/knowledge-base/docs/{doc_id}/chunks",
                headers={**headers, "Content-Type": "application/json"},
                data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
                timeout=120,
            ))
        print(f"imported chunks for {doc['title']}: {len(chunks)}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, default=Path("resources/demo/gakumas-kb/gamekee-characters"))
    parser.add_argument("--content-ids", type=int, nargs="+", default=DEFAULT_CONTENT_IDS)
    parser.add_argument("--include-related", action="store_true", help="Include related story/comic link sections.")
    parser.add_argument("--chunk-chars", type=int, default=1200)
    parser.add_argument("--overlap-chars", type=int, default=120)
    parser.add_argument("--delay-seconds", type=float, default=0.4)
    parser.add_argument("--api-base", help="Example: http://localhost:9090/api/koawa-agent")
    parser.add_argument("--kb-id", help="Existing knowledge base id. Required with --api-base.")
    parser.add_argument("--create-kb-name", help="Create a knowledge base through the API when --kb-id is absent.")
    parser.add_argument("--collection-name", default="gakumas_gamekee_characters")
    parser.add_argument("--embedding-model", default="qwen-emb-8b")
    parser.add_argument("--token", help="Authorization token if the API requires login.")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    manifest = write_corpus(args)
    import_via_api(args, manifest)


if __name__ == "__main__":
    main()
