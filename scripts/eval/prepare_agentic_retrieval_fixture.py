#!/usr/bin/env python3
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements. See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0.

"""Create and index the isolated AR0 knowledge-base fixture through public APIs."""

from __future__ import annotations

import argparse
import json
import secrets
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any


FIXTURE_NAME = "agentic-retrieval-eval-v1"
COLLECTION_NAME = "agentic-retrieval-eval-v1"


def load_env(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in "'\"":
            value = value[1:-1]
        values[key.strip()] = value
    return values


def request_json(
    url: str,
    method: str = "GET",
    payload: dict[str, Any] | None = None,
    token: str = "",
    content_type: str = "application/json",
) -> dict[str, Any]:
    data = None if payload is None else json.dumps(payload).encode("utf-8")
    headers = {"Accept": "application/json"}
    if data is not None:
        headers["Content-Type"] = content_type
    if token:
        headers["Authorization"] = token
    request = urllib.request.Request(url, data=data, headers=headers, method=method)
    with urllib.request.urlopen(request, timeout=60) as response:
        result = json.loads(response.read().decode("utf-8"))
    if str(result.get("code")) != "0":
        raise RuntimeError(
            f"{method} {url} failed: code={result.get('code')} "
            f"message={result.get('message')}"
        )
    return result


def multipart_body(file_path: Path) -> tuple[bytes, str]:
    boundary = f"----ragent-{secrets.token_hex(12)}"
    fields = {
        "sourceType": "file",
        "processMode": "chunk",
        "chunkStrategy": "structure_aware",
        "chunkConfig": json.dumps(
            {
                "targetChars": 1200,
                "maxChars": 1800,
                "minChars": 300,
                "overlapChars": 120,
            },
            separators=(",", ":"),
        ),
    }
    parts: list[bytes] = []
    for name, value in fields.items():
        parts.extend(
            [
                f"--{boundary}\r\n".encode(),
                (
                    f'Content-Disposition: form-data; name="{name}"\r\n\r\n'
                    f"{value}\r\n"
                ).encode("utf-8"),
            ]
        )
    parts.extend(
        [
            f"--{boundary}\r\n".encode(),
            (
                f'Content-Disposition: form-data; name="file"; '
                f'filename="{file_path.name}"\r\n'
            ).encode("utf-8"),
            b"Content-Type: text/markdown; charset=utf-8\r\n\r\n",
            file_path.read_bytes(),
            b"\r\n",
            f"--{boundary}--\r\n".encode(),
        ]
    )
    return b"".join(parts), f"multipart/form-data; boundary={boundary}"


def upload_document(base_url: str, kb_id: str, path: Path, token: str) -> str:
    body, content_type = multipart_body(path)
    request = urllib.request.Request(
        f"{base_url}/knowledge-base/{kb_id}/docs/upload",
        data=body,
        headers={
            "Accept": "application/json",
            "Authorization": token,
            "Content-Type": content_type,
        },
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=60) as response:
        result = json.loads(response.read().decode("utf-8"))
    if str(result.get("code")) != "0":
        raise RuntimeError(f"upload {path.name} failed: {result.get('message')}")
    return str(result["data"]["id"])


def page_data(base_url: str, path: str, token: str) -> dict[str, Any]:
    result = request_json(f"{base_url}{path}", token=token)
    data = result.get("data")
    if not isinstance(data, dict):
        raise RuntimeError(f"{path} returned no page data")
    return data


def find_or_create_kb(
    base_url: str, token: str, fixture_name: str, collection_name: str
) -> str:
    query = urllib.parse.urlencode({"name": fixture_name, "current": 1, "size": 20})
    records = page_data(base_url, f"/knowledge-base?{query}", token).get("records", [])
    for record in records:
        if record.get("name") == fixture_name:
            return str(record["id"])
    result = request_json(
        f"{base_url}/knowledge-base",
        method="POST",
        payload={
            "name": fixture_name,
            "embeddingModel": "siliconflow-embedding",
            "collectionName": collection_name,
        },
        token=token,
    )
    return str(result["data"])


def existing_documents(base_url: str, kb_id: str, token: str) -> dict[str, dict]:
    query = urllib.parse.urlencode({"current": 1, "size": 100})
    records = page_data(
        base_url, f"/knowledge-base/{kb_id}/docs?{query}", token
    ).get("records", [])
    return {str(record["docName"]): record for record in records}


def wait_for_index(base_url: str, doc_id: str, token: str, deadline: float) -> None:
    while time.monotonic() < deadline:
        result = request_json(
            f"{base_url}/knowledge-base/docs/{doc_id}",
            token=token,
        )
        document = result.get("data") or {}
        status = document.get("status")
        if status == "success":
            print(
                f"DOCUMENT={document.get('docName')} STATUS=success "
                f"CHUNKS={document.get('chunkCount')}"
            )
            return
        if status == "failed":
            raise RuntimeError(f"document indexing failed: {document.get('docName')}")
        time.sleep(2)
    raise TimeoutError(f"document indexing timed out: {doc_id}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--base-url", default="http://127.0.0.1:9090/api/koawa-agent"
    )
    parser.add_argument("--env-file", type=Path, default=Path("deploy/.env"))
    parser.add_argument(
        "--dataset",
        type=Path,
        default=Path("resources/eval/agentic-retrieval/v1/cases.json"),
    )
    parser.add_argument("--timeout-seconds", type=float, default=600)
    parser.add_argument("--knowledge-base-name", default=FIXTURE_NAME)
    parser.add_argument("--collection-name", default=COLLECTION_NAME)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    root = Path(__file__).resolve().parents[2]
    env_path = args.env_file if args.env_file.is_absolute() else root / args.env_file
    dataset_path = args.dataset if args.dataset.is_absolute() else root / args.dataset
    password = load_env(env_path).get("ADMIN_PASSWORD")
    if not password:
        raise RuntimeError("ADMIN_PASSWORD is missing from the env file")
    base_url = args.base_url.rstrip("/")
    login = request_json(
        f"{base_url}/auth/login",
        method="POST",
        payload={"username": "admin", "password": password},
    )
    token = str(login["data"]["token"])
    kb_id = find_or_create_kb(
        base_url, token, args.knowledge_base_name, args.collection_name)
    existing = existing_documents(base_url, kb_id, token)
    dataset = json.loads(dataset_path.read_text(encoding="utf-8"))
    pending: list[str] = []
    for document in dataset["documents"]:
        file_path = root / document["path"]
        current = existing.get(file_path.name)
        if current and current.get("status") == "success":
            print(f"DOCUMENT={file_path.name} STATUS=reused")
            continue
        if current:
            doc_id = str(current["id"])
        else:
            doc_id = upload_document(base_url, kb_id, file_path, token)
        request_json(
            f"{base_url}/knowledge-base/docs/{doc_id}/chunk",
            method="POST",
            token=token,
        )
        pending.append(doc_id)
    deadline = time.monotonic() + args.timeout_seconds
    for doc_id in pending:
        wait_for_index(base_url, doc_id, token, deadline)
    print(f"FIXTURE_READY=true")
    print(f"KNOWLEDGE_BASE_ID={kb_id}")
    print(f"DOCUMENT_COUNT={len(dataset['documents'])}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:
        print(f"FIXTURE_ERROR={type(error).__name__}: {error}", file=sys.stderr)
        raise SystemExit(1)
