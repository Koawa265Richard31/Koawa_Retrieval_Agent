#!/usr/bin/env python3
"""Run the scoped GameKee pilot questions through RAG and AgentLoop chat."""

from __future__ import annotations

import argparse
import json
import os
import re
import time
from pathlib import Path

import requests


CASES = [
    ("simple-stats", "花海咲季 Fighting My Way 的初始 Vo、Da、Vi 和体力是多少？"),
    ("simple-tokkun3", "Fighting My Way 的特训3阶段技能效果是什么？"),
    ("simple-luna-image", "月村手毬 Luna say maybe 的卡图和推荐效果是什么？"),
    ("complex-card-comparison", "比较 Fighting My Way 与 Luna say maybe 的初始属性、育成最大属性和技能效果；只基于已收录资料说明差异。"),
    ("complex-card-composition", "Fighting My Way 的数值成长、技能卡、P物品和偶像能力如何组合？请区分原始效果与强化效果。"),
    ("complex-inheritance", "结合继承机制详解，说明新手第一次育成前需要关注哪些继承准备条件；没有收录的内容明确说缺失。"),
    ("complex-first-training", "结合新手攻略和 Fighting My Way 的卡牌资料，给出第一次育成前的准备清单，并标记哪些项目来自攻略、哪些来自卡牌。"),
]
IMAGE_RE = re.compile(r"!\[[^\]]*\]\(([^)]+)\)")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--password-env", default="RAGENT_PILOT_PASSWORD")
    parser.add_argument("--collection-name", default="gakumas-gamekee-pilot-v3")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--timeout", type=int, default=180)
    return parser.parse_args()


def login(session: requests.Session, base_url: str, password: str) -> str:
    response = session.post(
        f"{base_url}/auth/login",
        json={"username": "admin", "password": password},
        timeout=30,
    )
    response.raise_for_status()
    payload = response.json()
    token = payload.get("data", {}).get("token")
    if payload.get("code") != "0" or not token:
        raise RuntimeError(f"login failed: {payload.get('message')}")
    return str(token)


def request_chat(session: requests.Session, base_url: str, token: str, case_id: str,
                 question: str, mode: str, collection_name: str, timeout: int) -> dict:
    started = time.monotonic()
    response = session.get(
        f"{base_url}/rag/v3/chat",
        params={
            "question": question,
            "conversationId": f"p{mode.lower()}{case_id}"[:20],
            "executionMode": mode,
            "collectionName": collection_name,
        },
        headers={"Authorization": token, "Accept": "text/event-stream"},
        stream=True,
        timeout=(30, timeout),
    )
    response.raise_for_status()
    raw_lines: list[str] = []
    answer: list[str] = []
    for raw_line in response.iter_lines(decode_unicode=True):
        line = raw_line or ""
        raw_lines.append(line)
        if not line.startswith("data:"):
            continue
        data = line[5:].strip()
        if data == "[DONE]":
            break
        try:
            event = json.loads(data)
        except json.JSONDecodeError:
            continue
        if event.get("type") == "response" and event.get("delta") is not None:
            answer.append(str(event["delta"]))
    text = "".join(answer)
    return {
        "mode": mode,
        "elapsedSeconds": round(time.monotonic() - started, 2),
        "answer": text,
        "answerChars": len(text),
        "imageUrls": IMAGE_RE.findall(text),
        "rawSse": "\n".join(raw_lines),
    }


def main() -> int:
    args = parse_args()
    password = os.environ.get(args.password_env)
    if not password:
        raise SystemExit(f"{args.password_env} is required")
    base_url = args.base_url.rstrip("/")
    session = requests.Session()
    token = login(session, base_url, password)
    results = []
    for case_id, question in CASES:
        item = {"id": case_id, "question": question, "results": []}
        for mode in ("RAG", "AGENT"):
            print(f"{case_id} {mode}", flush=True)
            try:
                item["results"].append(request_chat(
                    session, base_url, token, case_id, question, mode,
                    args.collection_name, args.timeout))
            except Exception as exc:  # Preserve individual failures for review.
                item["results"].append({"mode": mode, "error": str(exc)[:500]})
        results.append(item)
    report = {
        "collectionName": args.collection_name,
        "generatedAt": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "cases": results,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    for item in results:
        for result in item["results"]:
            print(item["id"], result["mode"], result.get("answerChars", 0),
                  len(result.get("imageUrls", [])), result.get("error", ""), flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
