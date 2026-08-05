#!/usr/bin/env python3
"""Prepare GameKee corpus with structural card detection.

The v1 page text may mention P cards inside a guide. This version reads the
saved API ``content_json`` and uses GameKee card components instead: ``fight-
info`` identifies P cards, while a standalone ``skill-info`` identifies a
support card. It makes no network or model calls.
"""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
from typing import Any

from prepare_gamekee_gakumas_semantic_corpus import (
    IMAGE_RE,
    GUIDE_MARKERS,
    identity_prefix,
    semantic_chunks,
)
from prepare_gamekee_gakumas_characters import render_document


def classify(title: str, markdown: str, content_json: str) -> str:
    sample = f"{title}\n{markdown[:3000]}".lower()
    if '"type":"fight-info"' in content_json:
        return "p_card"
    if '"type":"skill-info"' in content_json:
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


def build(args: argparse.Namespace) -> None:
    source_manifest = json.loads((args.input / "manifest.json").read_text(encoding="utf-8"))
    args.output.mkdir(parents=True, exist_ok=True)
    documents: list[dict[str, Any]] = []
    for item in source_manifest.get("documents", []):
        raw = json.loads((args.input / item["rawDetail"]).read_text(encoding="utf-8"))
        markdown, _ = render_document(
            raw, profile_only=False, fetched_at=str(source_manifest.get("fetchedAt") or ""))
        (args.output / item["file"]).write_text(markdown, encoding="utf-8")
        page_type = classify(str(item["title"]), markdown, str(raw.get("content_json") or ""))
        prefix = identity_prefix(page_type, str(item["title"]), int(item["contentId"]), str(item["sourceUrl"]))
        chunks = semantic_chunks(markdown, prefix, page_type, args.max_chunk_chars, args.overlap_chars)
        chunk_file = Path(item["file"]).with_suffix(".semantic-chunks.json").name
        (args.output / chunk_file).write_text(json.dumps(chunks, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        documents.append({
            **item,
            "pageType": page_type,
            "semanticChunksFile": chunk_file,
            "semanticChunkCount": len(chunks),
            "images": list(dict.fromkeys(IMAGE_RE.findall(markdown))),
            "sha256": hashlib.sha256(markdown.encode("utf-8")).hexdigest(),
        })
    (args.output / "manifest.json").write_text(json.dumps({
        "schemaVersion": 2,
        "source": "gamekee",
        "gameAlias": "gakumas",
        "derivedFrom": str(args.input / "manifest.json"),
        "chunking": {"strategy": "identity prefix + heading sections + overlapping fallback",
                     "maxChunkChars": args.max_chunk_chars, "overlapChars": args.overlap_chars,
                     "cardPagesAtomicWhenFit": True},
        "documents": documents,
    }, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"prepared {len(documents)} documents in {args.output}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, default=Path("output/gamekee-gakumas-wiki"))
    parser.add_argument("--output", type=Path, default=Path("output/gamekee-gakumas-semantic-v2"))
    parser.add_argument("--max-chunk-chars", type=int, default=1800)
    parser.add_argument("--overlap-chars", type=int, default=120)
    return parser.parse_args()


if __name__ == "__main__":
    build(parse_args())
