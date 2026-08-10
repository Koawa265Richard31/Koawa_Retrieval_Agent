#!/usr/bin/env python3
"""Crawl the public GameKee Gakumas wiki by catalog content IDs.

The wiki's ``<content-id>.html`` URLs are not a numeric sequence.  This
collector obtains IDs from the public entry catalog, so card pages (including
P cards) are discovered from the catalog rather than guessed from a range.
It keeps the raw HTML and API response for traceability, then produces a
Markdown corpus and deterministic overlapping chunks for RAG ingestion.
"""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import re
import sys
import time
from pathlib import Path
from typing import Any

import requests

from prepare_gamekee_gakumas_characters import render_document, safe_filename, split_chunks


GAME_ALIAS = "gakumas"
CATALOG_API = "https://www.gamekee.com/v1/entry/query-entry-list-from-cdn"
DETAIL_API = "https://www.gamekee.com/v1/content/detail/{content_id}"
PAGE_URL = "https://www.gamekee.com/gakumas/{content_id}.html"
USER_AGENT = "ragent-gamekee-gakumas-catalog-crawler/1.0"


def session() -> requests.Session:
    client = requests.Session()
    client.headers.update({
        "User-Agent": USER_AGENT,
        "game-alias": GAME_ALIAS,
        "Referer": f"https://www.gamekee.com/{GAME_ALIAS}/",
    })
    return client


def get_json(client: requests.Session, url: str) -> dict[str, Any]:
    response = client.get(url, timeout=30)
    response.raise_for_status()
    payload = response.json()
    if payload.get("code") not in (0, "0", None):
        raise RuntimeError(f"GameKee API failed: {payload}")
    return payload


def catalog_content_ids(client: requests.Session) -> list[int]:
    """Return every public content ID referenced by the wiki entry catalog."""
    payload = get_json(client, CATALOG_API)
    entries = (payload.get("data") or {}).get("dict") or []
    content_ids = {
        int(entry["c_id"])
        for entry in entries
        if isinstance(entry, dict) and str(entry.get("c_id") or "").isdigit()
    }
    if not content_ids:
        raise RuntimeError("GameKee catalog contained no content IDs")
    return sorted(content_ids)


def fetch_detail(client: requests.Session, content_id: int) -> dict[str, Any]:
    payload = get_json(client, DETAIL_API.format(content_id=content_id))
    data = payload.get("data") or {}
    if not data.get("title"):
        raise RuntimeError(f"content {content_id}: missing title")
    return data


def fetch_html(client: requests.Session, content_id: int) -> str:
    response = client.get(PAGE_URL.format(content_id=content_id), timeout=30)
    response.raise_for_status()
    return response.text


def write_document(output: Path, index: int, data: dict[str, Any], html: str,
                   fetched_at: str, chunk_chars: int, overlap_chars: int) -> dict[str, Any]:
    content_id = int(data["id"])
    title = str(data["title"])
    stem = safe_filename(index, title, content_id, "")
    raw_dir = output / "raw"
    raw_dir.mkdir(parents=True, exist_ok=True)
    (raw_dir / f"{stem}.html").write_text(html, encoding="utf-8")
    (raw_dir / f"{stem}.json").write_text(
        json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    markdown, images = render_document(data, profile_only=False, fetched_at=fetched_at)
    markdown_path = output / f"{stem}.md"
    markdown_path.write_text(markdown, encoding="utf-8")
    chunks = split_chunks(markdown, max_chars=chunk_chars, overlap_chars=overlap_chars)
    chunks_path = output / f"{stem}.chunks.json"
    chunks_path.write_text(json.dumps(chunks, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return {
        "contentId": content_id,
        "title": title,
        "sourceUrl": PAGE_URL.format(content_id=content_id),
        "file": markdown_path.name,
        "chunksFile": chunks_path.name,
        "rawHtml": str((raw_dir / f"{stem}.html").relative_to(output)),
        "rawDetail": str((raw_dir / f"{stem}.json").relative_to(output)),
        "chunkCount": len(chunks),
        "images": images,
        "sha256": hashlib.sha256(markdown.encode("utf-8")).hexdigest(),
    }


def load_completed(manifest_path: Path) -> dict[int, dict[str, Any]]:
    if not manifest_path.exists():
        return {}
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    return {int(item["contentId"]): item for item in manifest.get("documents") or []}


def _manifest_index(item: dict[str, Any]) -> int:
    """从清单条目文件名解析原序号（{index}-{contentId}-{title}.md），供重采复用保持文件名稳定。"""
    name = str(item.get("file") or "")
    match = re.match(r"^(\d+)-", name)
    return int(match.group(1)) if match else 0


def write_manifest(path: Path, fetched_at: str, documents: dict[int, dict[str, Any]], failures: dict[int, str]) -> None:
    path.write_text(json.dumps({
        "schemaVersion": 1,
        "source": "gamekee",
        "gameAlias": GAME_ALIAS,
        "fetchedAt": fetched_at,
        "discovery": "public entry catalog c_id mapping; no sequential HTML IDs",
        "documents": [documents[key] for key in sorted(documents)],
        "failures": [{"contentId": key, "error": failures[key]} for key in sorted(failures)],
    }, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def crawl(args: argparse.Namespace) -> None:
    args.output.mkdir(parents=True, exist_ok=True)
    manifest_path = args.output / "manifest.json"
    # 常驻读取已有清单：定点重采/全量刷新时可复用原文件序号，保证文档名稳定
    completed = load_completed(manifest_path)
    index_by_id = {cid: _manifest_index(item) for cid, item in completed.items()}
    failures: dict[int, str] = {}
    fetched_at = dt.datetime.now(dt.timezone.utc).isoformat()
    client = session()
    content_ids = catalog_content_ids(client)
    if args.ids:
        requested = sorted({int(token) for token in args.ids.split(",") if token.strip().isdigit()})
        if not requested:
            raise SystemExit("--ids requires at least one numeric content ID")
        content_ids = requested
    elif args.max_documents:
        content_ids = content_ids[:args.max_documents]
    print(f"catalog discovered {len(content_ids)} content IDs" if not args.ids else f"targeted re-crawl of {len(content_ids)} content IDs")

    for position, content_id in enumerate(content_ids, 1):
        if args.resume and content_id in completed:
            continue
        index = index_by_id.get(content_id, position)
        try:
            data = fetch_detail(client, content_id)
            html = fetch_html(client, content_id)
            completed[content_id] = write_document(
                args.output, index, data, html, fetched_at, args.chunk_chars, args.overlap_chars)
            print(f"[{index}/{len(content_ids)}] {content_id} {data['title']}")
        except Exception as exc:  # Keep the rest of a long catalog crawl progressing.
            failures[content_id] = str(exc)
            print(f"[{index}/{len(content_ids)}] failed {content_id}: {exc}")
        write_manifest(manifest_path, fetched_at, completed, failures)
        time.sleep(args.delay_seconds)
    # A resumed run may have no pending IDs. Still clear stale failure records
    # from a previous interrupted run once every catalog ID is represented.
    write_manifest(manifest_path, fetched_at, completed, failures)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, default=Path("output/gamekee-gakumas-wiki"))
    parser.add_argument("--chunk-chars", type=int, default=1200)
    parser.add_argument("--overlap-chars", type=int, default=120)
    parser.add_argument("--delay-seconds", type=float, default=0.6)
    parser.add_argument("--max-documents", type=int, help="Limit a run for a smoke test.")
    parser.add_argument("--ids", type=str, help="Comma-separated GameKee content IDs to (re)crawl only.")
    parser.add_argument("--resume", action="store_true", help="Reuse documents listed in an existing manifest.")
    return parser.parse_args()


if __name__ == "__main__":
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="backslashreplace")
    crawl(parse_args())
