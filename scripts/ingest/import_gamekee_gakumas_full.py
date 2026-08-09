#!/usr/bin/env python3
"""Merge the local GameKee Gakumas corpus into an existing knowledge base.

The source corpus is the v4 semantic chunks under
``output/gamekee-gakumas-semantic-v4``.  Documents already present in the
target knowledge base are skipped unless their chunk count is lower than the
local corpus; in that case the existing document is reused and missing chunks
are appended.  Re-running the importer is safe because chunk IDs are stable
per document/index/content and existing content hashes are checked first.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import random
import re
import sys
import threading
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import requests


DEFAULT_KB_ID = "2084920454895685632"
RETRY_STATUSES = {408, 429, 500, 502, 503, 504}


def utf8_stdout() -> None:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="backslashreplace")


def sha256_hex(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def stable_chunk_id(doc_id: str, index: int, content: str) -> str:
    digest = sha256_hex(f"{doc_id}\n{index}\n{content}")
    return digest[:20]


def api_result(response: requests.Response) -> Any:
    response.raise_for_status()
    payload = response.json()
    if str(payload.get("code")) not in {"0", "None"} and payload.get("code") != 0:
        raise RuntimeError(payload.get("message") or f"API failed: {payload}")
    return payload.get("data")


def request_json(
    session: requests.Session,
    method: str,
    url: str,
    *,
    headers: dict[str, str] | None = None,
    retries: int = 6,
    timeout: float | tuple[float, float] | None = None,
    **kwargs: Any,
) -> Any:
    last_error: Exception | None = None
    request_timeout = timeout if timeout is not None else (30, 240)
    for attempt in range(retries):
        try:
            response = session.request(
                method,
                url,
                headers=headers,
                timeout=request_timeout,
                **kwargs,
            )
            if response.status_code in RETRY_STATUSES:
                time.sleep(min(30, 1.5 ** attempt + random.uniform(0, 1)))
                continue
            return api_result(response)
        except requests.Timeout as exc:
            last_error = exc
        except requests.ConnectionError as exc:
            last_error = exc
        except requests.HTTPError as exc:
            status = exc.response.status_code if exc.response is not None else None
            if status in RETRY_STATUSES:
                last_error = exc
                time.sleep(min(30, 1.5 ** attempt + random.uniform(0, 1)))
                continue
            raise
        if attempt + 1 < retries:
            time.sleep(min(30, 1.5 ** attempt + random.uniform(0, 1)))
    raise RuntimeError(f"request failed after {retries} attempts: {last_error}")


def login(session: requests.Session, api_base: str, username: str, password: str) -> str:
    data = request_json(
        session,
        "POST",
        f"{api_base}/auth/login",
        json={"username": username, "password": password},
        timeout=30,
    )
    token = (data or {}).get("token")
    if not token:
        raise RuntimeError("login succeeded without an access token")
    return str(token)


def list_documents(
    session: requests.Session,
    api_base: str,
    token: str,
    kb_id: str,
) -> dict[str, dict[str, Any]]:
    headers = {"Authorization": token}
    documents: dict[str, dict[str, Any]] = {}
    current = 1
    page_size = 200
    while True:
        data = request_json(
            session,
            "GET",
            f"{api_base}/knowledge-base/{kb_id}/docs",
            headers=headers,
            params={"current": current, "size": page_size},
        ) or {}
        records = data.get("records") or []
        for record in records:
            documents[record["docName"]] = {
                "id": record["id"],
                "chunkCount": int(record.get("chunkCount") or 0),
            }
        if len(records) < page_size:
            break
        current += 1
    return documents


def list_chunk_hashes(
    session: requests.Session,
    api_base: str,
    token: str,
    doc_id: str,
) -> set[str]:
    headers = {"Authorization": token}
    hashes: set[str] = set()
    current = 1
    page_size = 200
    while True:
        data = request_json(
            session,
            "GET",
            f"{api_base}/knowledge-base/docs/{doc_id}/chunks",
            headers=headers,
            params={"current": current, "size": page_size},
        ) or {}
        records = data.get("records") or []
        for record in records:
            content_hash = record.get("contentHash")
            hashes.add(content_hash or sha256_hex(str(record.get("content") or "")))
        if len(records) < page_size:
            break
        current += 1
    return hashes



def extract_source_time(markdown_path: Path) -> str | None:
    """从 markdown 头部解析源端发布时间/更新时间（ISO8601），无则返回 None。"""
    try:
        text = markdown_path.read_text(encoding="utf-8", errors="replace")
    except Exception:
        return None
    for pattern in (
        r"页面更新时间[：:]\s*([0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9:]{8}[+-][0-9:]{5})",
        r"发布时间[：:]\s*([0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9:]{8}[+-][0-9:]{5})",
        r"更新日期[：:]\s*([0-9]{4}-[0-9]{2}-[0-9]{2})",
    ):
        m = re.search(pattern, text)
        if m:
            return m.group(1)
    return None

def upload_document(
    session: requests.Session,
    api_base: str,
    token: str,
    kb_id: str,
    item: dict[str, Any],
    docs_dir: Path,
) -> str:
    headers = {"Authorization": token}
    file_name = str(item["file"])
    markdown_path = docs_dir / file_name
    if not markdown_path.is_file():
        raise FileNotFoundError(f"missing markdown source: {markdown_path}")
    form_data = {
        "sourceType": "file",
        "processMode": "chunk",
        "chunkStrategy": "fixed_size",
        "chunkConfig": json.dumps(
            {"chunkSize": 1800, "overlapSize": 0},
            ensure_ascii=False,
        ),
    }
    source_time = extract_source_time(markdown_path)
    if source_time:
        form_data["sourceTime"] = source_time

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
    return str(doc_id)


def import_document(
    api_base: str,
    token: str,
    kb_id: str,
    item: dict[str, Any],
    docs_dir: Path,
    corpus_dir: Path,
    existing: dict[str, dict[str, Any]] | None,
) -> dict[str, Any]:
    session = requests.Session()
    headers = {"Authorization": token}
    file_name = str(item["file"])
    chunk_file = str(item["semanticChunksFile"])
    chunks = json.loads((corpus_dir / chunk_file).read_text(encoding="utf-8"))
    expected = len(chunks)
    existing_doc = (existing or {}).get(file_name)

    if existing_doc is not None and existing_doc["chunkCount"] >= expected:
        return {
            "file": file_name,
            "title": item.get("title"),
            "status": "skipped",
            "chunkCount": expected,
            "created": 0,
            "skippedExisting": expected,
            "error": None,
        }

    if existing_doc is not None:
        doc_id = existing_doc["id"]
        reused = True
    else:
        doc_id = upload_document(session, api_base, token, kb_id, item, docs_dir)
        reused = False

    existing_hashes = list_chunk_hashes(session, api_base, token, doc_id)
    created = 0
    skipped_existing = 0
    for index, content in enumerate(chunks):
        content_hash = sha256_hex(str(content))
        if content_hash in existing_hashes:
            skipped_existing += 1
            continue
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
        created += 1

    return {
        "file": file_name,
        "title": item.get("title"),
        "status": "reused" if reused else "imported",
        "docId": doc_id,
        "chunkCount": expected,
        "created": created,
        "skippedExisting": skipped_existing,
        "error": None,
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--api-base", default="http://117.72.203.70:9090/api/koawa-agent")
    parser.add_argument("--username", default="admin")
    parser.add_argument("--password-env", default="RAGENT_PILOT_PASSWORD")
    parser.add_argument("--kb-id", default=DEFAULT_KB_ID)
    parser.add_argument("--corpus", type=Path, default=Path("output/gamekee-gakumas-semantic-v4"))
    parser.add_argument("--docs-dir", type=Path, default=Path("output/gamekee-gakumas-wiki"))
    parser.add_argument("--report", type=Path, default=Path("output/eval/gakumas-gamekee-full-import-report.json"))
    parser.add_argument("--workers", type=int, default=3)
    parser.add_argument("--limit", type=int, default=0, help="Import at most N documents after skipping.")
    parser.add_argument("--dry-run", action="store_true")
    parser.set_defaults(skip_existing=True)
    parser.add_argument("--no-skip-existing", dest="skip_existing", action="store_false")
    return parser.parse_args()


def main() -> int:
    utf8_stdout()
    args = parse_args()
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
    existing_docs = list_documents(login_session, api_base, token, args.kb_id)
    login_session.close()
    if args.skip_existing:
        todo = [
            item
            for item in items
            if item["file"] in existing_docs
            and existing_docs[item["file"]]["chunkCount"]
            < int(item.get("semanticChunkCount") or 0)
            or item["file"] not in existing_docs
        ]
        partial = [
            item
            for item in items
            if item["file"] in existing_docs
            and existing_docs[item["file"]]["chunkCount"]
            < int(item.get("semanticChunkCount") or 0)
        ]
    else:
        todo = list(items)
        partial = []

    print(
        f"corpus={len(items)} existing={len(existing_docs)} "
        f"to_import={len(todo)} partial={len(partial)}"
    )
    if args.limit > 0:
        todo = todo[: args.limit]
    if args.dry_run:
        for item in todo[:50]:
            print(f"would import {item['file']} chunks={item.get('semanticChunkCount')}")
        return 0

    started_at = datetime.now(timezone.utc).isoformat()
    results: list[dict[str, Any]] = []
    lock = threading.Lock()
    completed = 0

    def run_item(item: dict[str, Any]) -> dict[str, Any]:
        nonlocal completed
        try:
            result = import_document(
                api_base,
                token,
                args.kb_id,
                item,
                args.docs_dir,
                args.corpus,
                existing_docs,
            )
        except Exception as exc:
            result = {
                "file": str(item.get("file")),
                "title": item.get("title"),
                "status": "failed",
                "chunkCount": int(item.get("semanticChunkCount") or 0),
                "created": 0,
                "skippedExisting": 0,
                "error": str(exc)[:1000],
            }
        with lock:
            completed += 1
            if completed % 10 == 0 or result["status"] == "failed":
                print(
                    f"[{completed}/{len(todo)}] {result['status']} "
                    f"{result['file']} created={result.get('created', 0)} "
                    f"error={result.get('error') or ''}",
                    flush=True,
                )
        return result

    with ThreadPoolExecutor(max_workers=args.workers) as executor:
        futures = [executor.submit(run_item, item) for item in todo]
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
            "total": len(todo),
            "imported": sum(1 for r in results if r["status"] == "imported"),
            "reused": sum(1 for r in results if r["status"] == "reused"),
            "skipped": sum(1 for r in results if r["status"] == "skipped"),
            "failed": sum(1 for r in results if r["status"] == "failed"),
            "chunksCreated": sum(int(r.get("created") or 0) for r in results),
            "chunksSkipped": sum(int(r.get("skippedExisting") or 0) for r in results),
        },
    }
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report["summary"], ensure_ascii=False))
    return 0 if report["summary"]["failed"] == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
