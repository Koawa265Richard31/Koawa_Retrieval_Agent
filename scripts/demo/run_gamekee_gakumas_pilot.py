#!/usr/bin/env python3
"""Run a deliberately small GameKee KB import experiment.

Credentials are read from environment variables and never written to the
report.  The report records HTTP status and error summaries so a quota/API
failure can be analysed without blindly retrying embedding requests.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import requests


PILOT_CONTENT_IDS = (622396, 622562, 622564, 622546, 624112)


def api_data(response: requests.Response) -> Any:
    response.raise_for_status()
    payload = response.json()
    if str(payload.get("code")) not in {"0", "None"} and payload.get("code") != 0:
        raise RuntimeError(payload.get("message") or "API returned an unsuccessful response")
    return payload.get("data")


def error_summary(exc: Exception) -> str:
    text = str(exc).replace("\n", " ").strip()
    return text[:500] or exc.__class__.__name__


def login(session: requests.Session, api_base: str, username: str, password: str) -> str:
    data = api_data(session.post(f"{api_base}/auth/login", json={"username": username, "password": password}, timeout=30))
    token = (data or {}).get("token")
    if not token:
        raise RuntimeError("login succeeded without an access token")
    return str(token)


def run(args: argparse.Namespace) -> dict[str, Any]:
    password = os.environ.get("RAGENT_PILOT_PASSWORD")
    if not password:
        raise SystemExit("RAGENT_PILOT_PASSWORD is required")
    manifest = json.loads((args.corpus / "manifest.json").read_text(encoding="utf-8"))
    documents = {int(item["contentId"]): item for item in manifest.get("documents", [])}
    selected = [documents[content_id] for content_id in PILOT_CONTENT_IDS if content_id in documents]
    if len(selected) != len(PILOT_CONTENT_IDS):
        raise SystemExit("pilot corpus is missing one or more selected content IDs")

    api_base = args.api_base.rstrip("/")
    session = requests.Session()
    token = login(session, api_base, args.username, password)
    headers = {"Authorization": token}
    kb_id = api_data(session.post(f"{api_base}/knowledge-base", headers=headers, json={
        "name": args.kb_name,
        "embeddingModel": args.embedding_model,
        "collectionName": args.collection_name,
    }, timeout=30))
    report: dict[str, Any] = {
        "startedAt": datetime.now(timezone.utc).isoformat(),
        "apiBase": api_base,
        "knowledgeBase": {"id": kb_id, "name": args.kb_name, "collectionName": args.collection_name},
        "maxChunksPerDocument": args.max_chunks_per_document,
        "documents": [],
        "stoppedOnError": False,
    }

    for item in selected:
        entry: dict[str, Any] = {"contentId": item["contentId"], "title": item["title"], "pageType": item["pageType"], "chunks": []}
        try:
            markdown_path = args.corpus / item["file"]
            with markdown_path.open("rb") as file_obj:
                doc = api_data(session.post(
                    f"{api_base}/knowledge-base/{kb_id}/docs/upload", headers=headers,
                    data={"sourceType": "file", "processMode": "chunk", "chunkStrategy": "fixed_size",
                          "chunkConfig": json.dumps({"chunkSize": 1800, "overlapSize": 0})},
                    files={"file": (markdown_path.name, file_obj, "text/markdown")}, timeout=60))
            entry["documentId"] = doc["id"]
            chunks = json.loads((args.corpus / item["semanticChunksFile"]).read_text(encoding="utf-8"))
            for index, content in enumerate(chunks[:args.max_chunks_per_document]):
                try:
                    created = api_data(session.post(
                        f"{api_base}/knowledge-base/docs/{doc['id']}/chunks", headers=headers,
                        json={"index": index, "content": content}, timeout=120))
                    entry["chunks"].append({"index": index, "status": "created", "chunkId": (created or {}).get("id")})
                except Exception as exc:
                    entry["chunks"].append({"index": index, "status": "failed", "error": error_summary(exc)})
                    report["stoppedOnError"] = True
                    break
        except Exception as exc:
            entry["status"] = "failed"
            entry["error"] = error_summary(exc)
            report["stoppedOnError"] = True
        report["documents"].append(entry)
        if report["stoppedOnError"]:
            break

    report["finishedAt"] = datetime.now(timezone.utc).isoformat()
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return report


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--api-base", required=True)
    parser.add_argument("--username", default="admin")
    parser.add_argument("--kb-name", default="gakumas-gamekee-pilot-v1")
    # The current backend creates an object-storage bucket using this value.
    # Keep it DNS/S3 compatible: lowercase letters, digits and hyphens only.
    parser.add_argument("--collection-name", default="gakumas-gamekee-pilot-v1")
    parser.add_argument("--embedding-model", default="Qwen/Qwen3-Embedding-8B")
    parser.add_argument("--corpus", type=Path, default=Path("output/gamekee-gakumas-semantic-v4"))
    parser.add_argument("--max-chunks-per-document", type=int, default=3)
    parser.add_argument("--report", type=Path, default=Path("D:/codexCliTest/gakumas-gamekee-pilot-report.json"))
    return parser.parse_args()


if __name__ == "__main__":
    try:
        result = run(parse_args())
        print(json.dumps({"knowledgeBase": result["knowledgeBase"], "stoppedOnError": result["stoppedOnError"]}, ensure_ascii=False))
    except Exception as exc:
        print(f"pilot failed: {error_summary(exc)}", file=sys.stderr)
        raise SystemExit(1)
