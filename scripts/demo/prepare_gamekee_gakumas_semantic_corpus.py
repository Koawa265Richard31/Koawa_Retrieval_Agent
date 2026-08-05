#!/usr/bin/env python3
"""Build an import-ready semantic corpus from the raw GameKee crawl.

This is deliberately offline: it never calls an embedding or chat model.  It
preserves the original crawl, adds stable page identity to every chunk, keeps
short card pages atomic, and splits long guides at headings before applying a
small overlapping fallback window.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
from pathlib import Path
from typing import Any


IMAGE_RE = re.compile(r"!\[[^\]\r\n]*]\([^\s)]+(?:\s+\"[^\"]*\")?\)")
HEADING_RE = re.compile(r"(?=^#{1,3}\s+)", re.MULTILINE)
P_CARD_MARKERS = ("p卡", "p 卡", "produce card", "プロデュースカード")
SUPPORT_CARD_MARKERS = ("s卡", "s 卡", "support card", "サポートカード")
GUIDE_MARKERS = ("攻略", "配队", "编队", "培养", "玩法", "机制", "思路")


def normalize(value: str) -> str:
    return re.sub(r"\s+", " ", value).strip()


def classify(title: str, markdown: str) -> str:
    sample = f"{title}\n{markdown[:3000]}".lower()
    if any(marker in sample for marker in P_CARD_MARKERS):
        return "p_card"
    if any(marker in sample for marker in SUPPORT_CARD_MARKERS):
        return "support_card"
    if "官方四格" in title or "漫画" in title:
        return "comic"
    if any(marker in title for marker in GUIDE_MARKERS):
        return "guide"
    if "角色名 |" in markdown or "个人简介" in markdown:
        return "character"
    if any(marker in sample for marker in GUIDE_MARKERS):
        return "mechanic_or_guide"
    return "wiki_page"


def identity_prefix(page_type: str, title: str, content_id: int, source_url: str) -> str:
    return "\n".join((
        f"类型：{page_type}",
        f"标题：{title}",
        f"GameKee内容ID：{content_id}",
        f"来源：{source_url}",
    ))


def window_split(text: str, max_chars: int, overlap_chars: int) -> list[str]:
    if len(text) <= max_chars:
        return [text]
    pieces: list[str] = []
    step = max(1, max_chars - overlap_chars)
    for start in range(0, len(text), step):
        piece = text[start:start + max_chars].strip()
        if piece:
            pieces.append(piece)
        if start + max_chars >= len(text):
            break
    return pieces


def semantic_chunks(markdown: str, prefix: str, page_type: str, max_chars: int, overlap_chars: int) -> list[str]:
    body = markdown.strip()
    # Card pages are normally compact. Keeping them whole prevents a card art,
    # skill and numerical table from being separated during retrieval.
    if page_type in {"p_card", "support_card"} and len(prefix) + len(body) <= max_chars:
        return [f"{prefix}\n\n{body}"]

    sections = [section.strip() for section in HEADING_RE.split(body) if section.strip()]
    if not sections:
        sections = [body]
    chunks: list[str] = []
    current = ""
    for section in sections:
        candidate = f"{current}\n\n{section}".strip() if current else section
        if len(prefix) + len(candidate) <= max_chars:
            current = candidate
            continue
        if current:
            chunks.append(f"{prefix}\n\n{current}")
            current = ""
        for piece in window_split(section, max_chars - len(prefix) - 2, overlap_chars):
            chunks.append(f"{prefix}\n\n{piece}")
    if current:
        chunks.append(f"{prefix}\n\n{current}")
    return chunks


def build(args: argparse.Namespace) -> None:
    source_manifest = json.loads((args.input / "manifest.json").read_text(encoding="utf-8"))
    args.output.mkdir(parents=True, exist_ok=True)
    documents: list[dict[str, Any]] = []
    for item in source_manifest.get("documents", []):
        markdown = (args.input / item["file"]).read_text(encoding="utf-8")
        page_type = classify(str(item["title"]), markdown)
        prefix = identity_prefix(page_type, str(item["title"]), int(item["contentId"]), str(item["sourceUrl"]))
        chunks = semantic_chunks(markdown, prefix, page_type, args.max_chunk_chars, args.overlap_chars)
        images = list(dict.fromkeys(IMAGE_RE.findall(markdown)))
        chunk_file = Path(item["file"]).with_suffix(".semantic-chunks.json").name
        (args.output / chunk_file).write_text(json.dumps(chunks, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        documents.append({
            **item,
            "pageType": page_type,
            "semanticChunksFile": chunk_file,
            "semanticChunkCount": len(chunks),
            "images": images,
            "sha256": hashlib.sha256(markdown.encode("utf-8")).hexdigest(),
        })
    manifest = {
        "schemaVersion": 2,
        "source": "gamekee",
        "gameAlias": "gakumas",
        "derivedFrom": str(args.input / "manifest.json"),
        "chunking": {
            "strategy": "identity prefix + heading sections + overlapping fallback",
            "maxChunkChars": args.max_chunk_chars,
            "overlapChars": args.overlap_chars,
            "cardPagesAtomicWhenFit": True,
        },
        "documents": documents,
    }
    (args.output / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"prepared {len(documents)} documents in {args.output}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, default=Path("output/gamekee-gakumas-wiki"))
    parser.add_argument("--output", type=Path, default=Path("output/gamekee-gakumas-semantic"))
    parser.add_argument("--max-chunk-chars", type=int, default=1800)
    parser.add_argument("--overlap-chars", type=int, default=120)
    return parser.parse_args()


if __name__ == "__main__":
    build(parse_args())
