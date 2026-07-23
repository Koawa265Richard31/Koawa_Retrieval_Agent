#!/usr/bin/env python3
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements. See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0.

"""Run an authenticated Agent SSE smoke test without printing credentials."""

from __future__ import annotations

import argparse
import json
import sys
import urllib.parse
import urllib.request
from pathlib import Path


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


def request_json(url: str, payload: dict[str, str]) -> dict:
    request = urllib.request.Request(
        url,
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=15) as response:
        return json.load(response)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://127.0.0.1:9090/api/koawa-agent")
    parser.add_argument("--env-file", default=".env")
    parser.add_argument(
        "--question",
        default="请查询北京未来3天的天气，并根据查询结果给出简短出行建议。",
    )
    args = parser.parse_args()

    env = load_env(Path(args.env_file))
    password = env.get("ADMIN_PASSWORD")
    if not password:
        raise RuntimeError("ADMIN_PASSWORD is missing from the env file")

    login = request_json(
        f"{args.base_url.rstrip('/')}/auth/login",
        {"username": "admin", "password": password},
    )
    token = login.get("data", {}).get("token")
    if not token:
        raise RuntimeError(f"login failed: code={login.get('code')}, message={login.get('message')}")

    query = urllib.parse.urlencode({"question": args.question, "deepThinking": "false"})
    request = urllib.request.Request(
        f"{args.base_url.rstrip('/')}/rag/v3/chat?{query}",
        headers={"Authorization": token, "Accept": "text/event-stream"},
    )

    event_name = ""
    task_id = ""
    conversation_id = ""
    answer_parts: list[str] = []
    saw_done = False
    with urllib.request.urlopen(request, timeout=150) as response:
        for raw_line in response:
            line = raw_line.decode("utf-8").rstrip("\r\n")
            if line.startswith("event:"):
                event_name = line[6:].strip()
                continue
            if not line.startswith("data:"):
                continue
            data = line[5:].strip()
            if data == "[DONE]":
                saw_done = True
                continue
            try:
                payload = json.loads(data)
            except json.JSONDecodeError:
                continue
            if event_name == "meta":
                task_id = str(payload.get("taskId", ""))
                conversation_id = str(payload.get("conversationId", ""))
            elif event_name == "message" and payload.get("type") == "response":
                answer_parts.append(str(payload.get("delta", "")))

    answer = "".join(answer_parts).strip()
    print(f"SMOKE_DONE={str(saw_done).lower()}")
    print(f"TASK_ID={task_id}")
    print(f"CONVERSATION_ID={conversation_id}")
    print(f"ANSWER={answer}")
    return 0 if saw_done and task_id and answer else 1


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as error:
        print(f"SMOKE_ERROR={type(error).__name__}: {error}", file=sys.stderr)
        sys.exit(1)
