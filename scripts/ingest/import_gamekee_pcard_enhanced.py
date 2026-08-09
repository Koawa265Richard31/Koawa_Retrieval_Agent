#!/usr/bin/env python3
"""Replace GameKee Gakumas P-card documents in a knowledge base.

The enhanced P-card corpus (``output/gamekee-gakumas-pcard-v1``) adds the
card release time (``实装时间``), labelled 觉醒前/觉醒后 card arts and a
thumbnail.  Existing P-card documents in the target KB are deleted first
(which also removes their vectors) and re-uploaded with ``sourceTime`` =
the card release date so time-weighting ranks by actual release time.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import threading
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import requests

from import_gamekee_gakumas_full import (
    DEFAULT_KB_ID,
    api_result,
    login,
    list_documents,
    request_json,
    stable_chunk_id,
)


def utf8_stdout() -> None:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="backslashreplace")


def replace_document(
    api_base: str,
    token: str,
    kb_id: str,
    item: dict[str, Any],
    corpus_dir: Path,
    existing: dict[str, dict[str, Any]],
) -> dict[str, Any]:
    session = requests.Session()
    headers = {"Authorization": token}
    file_name = str(item["file"])
    markdown_path = corpus_dir / file_name
    if not markdown_path.is_file():
        raise FileNotFoundError(f"missing markdown: {markdown_path}")
    chunk_file = str(item["semanticChunksFile"])
    chunks = json.loads((corpus_dir / chunk_file).read_text(encoding="utf-8"))

    # 1. Remove the old document (also clears its vectors).
    old = existing.get(file_name)
    if old is not None:
        request_json(
            session, "DELETE", f"{api_base}/knowledge-base/docs/{old['id']}", headers=headers, timeout=60
        )

    # 2. Upload the enhanced markdown with the release time.
    form_data = {
        "sourceType": "file",
        "processMode": "chunk",
        "chunkStrategy": "fixed_size",
        "chunkConfig": json.dumps({"chunkSize": 1800, "overlapSize": 0}, ensure_ascii=False),
    }
    if item.get("sourceTime"):
        form_data["sourceTime"] = item["sourceTime"]
    with markdown_path.open("rb") as file_obj:
        created = request_json(
            session,
            "POST",
            f"{api_base}/knowledge-base/{kb_id}/docs/upload",
            headers=headers,
            data=form_data,
            files={"file": (file_name, file_obj, "text/markdown")},
        )
    doc_id = (created or {}).get("id")
    if not doc_id:
        raise RuntimeError(f"upload returned no document id: {file_name}")
    doc_id = str(doc_id)

    # 3. Add chunks.
    created_count = 0
    for index, content in enumerate(chunks):
        payload = {
            "index": index,
            "content": content,
            "chunkId": stable_chunk_id(doc_id, index, str(content)),
        }
        request_json(
            session,
            "POST",
            f"{api_base}/knowledge-base/docs/{doc_id}/chunks",
            headers={**headers, "Content-Type": "application/json"},
            json=payload,
        )
        created_count += 1
    return {
        "file": file_name,
        "title": item.get("title"),
        "releaseDate": item.get("releaseDate"),
        "docId": doc_id,
        "chunkCount": created_count,
    }


def main() -> int:
    utf8_stdout()
    parser = argparse.ArgumentParser()
    parser.add_argument("--api-base", default="http://117.72.203.70:9090/api/koawa-agent")
    parser.add_argument("--username", default="admin")
    parser.add_argument("--password-env", default="RAGENT_PILOT_PASSWORD")
    parser.add_argument("--kb-id", default=DEFAULT_KB_ID)
    parser.add_argument("--corpus", type=Path, default=Path("output/gamekee-gakumas-pcard-v1"))
    parser.add_argument("--report", type=Path, default=Path("output/eval/gakumas-pcard-enhanced-import-report.json"))
    parser.add_argument("--workers", type=int, default=3)
    parser.add_argument("--limit", type=int, default=0)
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    password = os.environ.get(args.password_env)
    if not password:
        raise SystemExit(f"{args.password_env} is required")
    if not (args.corpus / "manifest.json").is_file():
        raise SystemExit(f"corpus manifest not found: {args.corpus / 'manifest.json'}")

    api_base = args.api_base.rstrip("/")
    manifest = json.loads((args.corpus / "manifest.json").read_text(encoding="utf-8"))
    items = list(manifest.get("documents") or [])
    login_session = requests.Session()
    token = login(login_session, api_base, args.username, password)
    existing = list_documents(login_session, api_base, token, args.kb_id)
    login_session.close()
    print(f"corpus={len(items)} existing_total={len(existing)}")
    if args.limit > 0:
        items = items[: args.limit]
    if args.dry_run:
        for item in items[:20]:
            print(f"would replace {item['file']} release={item.get('releaseDate')} chunks={item.get('semanticChunkCount')}")
        return 0

    started_at = datetime.now(timezone.utc).isoformat()
    results: list[dict[str, Any]] = []
    lock = threading.Lock()
    completed = 0

    def run_item(item: dict[str, Any]) -> dict[str, Any]:
        nonlocal completed
        try:
            result = replace_document(api_base, token, args.kb_id, item, args.corpus, existing)
            result["status"] = "replaced"
        except Exception as exc:
            result = {
                "file": str(item.get("file")),
                "title": item.get("title"),
                "status": "failed",
                "error": str(exc)[:1000],
            }
        with lock:
            completed += 1
            if completed % 10 == 0 or result["status"] == "failed":
                print(f"[{completed}/{len(items)}] {result['status']} {result.get('file')} error={result.get('error') or ''}", flush=True)
        return result

    with ThreadPoolExecutor(max_workers=args.workers) as executor:
        futures = [executor.submit(run_item, item) for item in items]
        for future in as_completed(futures):
            results.append(future.result())

    report = {
        "startedAt": started_at,
        "finishedAt": datetime.now(timezone.utc).isoformat(),
        "apiBase": api_base,
        "kbId": args.kb_id,
        "corpus": str(args.corpus),
        "documents": results,
        "summary": {
            "total": len(items),
            "replaced": sum(1 for r in results if r["status"] == "replaced"),
            "failed": sum(1 for r in results if r["status"] == "failed"),
        },
    }
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report["summary"], ensure_ascii=False))
    return 0 if report["summary"]["failed"] == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
