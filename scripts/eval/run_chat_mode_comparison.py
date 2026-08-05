#!/usr/bin/env python3
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements. See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0.

"""Compare final chat answers from RAG and full Agent loop modes.

This runner calls the production SSE endpoint:

    GET /rag/v3/chat?question=...&executionMode=RAG
    GET /rag/v3/chat?question=...&executionMode=AGENT

It is intentionally separate from the agentic-retrieval eval scripts because
those exercise retrieval-only `/rag/eval`; this script evaluates the final
user-visible answer, including clarification behavior and Markdown images.
"""

from __future__ import annotations

import argparse
import json
import re
import socket
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any


IMAGE_RE = re.compile(r"!\[[^\]\n]*]\([^\s)]+\)")
CLARIFICATION_TERMS = ["请补充", "请明确", "需要你进一步", "澄清", "你想了解哪"]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--cases",
        type=Path,
        default=Path("resources/eval/chat-mode-comparison/v1/gakumas-chat-cases.json"),
    )
    parser.add_argument(
        "--base-url",
        default="http://127.0.0.1:9090/api/koawa-agent",
    )
    parser.add_argument("--env-file", type=Path, default=Path("deploy/.env"))
    parser.add_argument("--token", help="Optional Authorization token.")
    parser.add_argument(
        "--collection-name",
        help="Optional collection scope for RAG requests.",
    )
    parser.add_argument("--timeout-seconds", type=float, default=240.0)
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("resources/eval/chat-mode-comparison/v1/latest-summary.json"),
    )
    parser.add_argument("--case-id", action="append", help="Run only the specified case id. Repeatable.")
    parser.add_argument("--limit", type=int, help="Run only the first N cases after filtering.")
    parser.add_argument("--validate-only", action="store_true")
    return parser.parse_args()


def load_env(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    if not path.exists():
        return values
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip().strip("'\"")
    return values


def load_cases(path: Path) -> dict[str, Any]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    if payload.get("schemaVersion") != "1.0":
        raise ValueError("unsupported schemaVersion")
    cases = payload.get("cases")
    if not isinstance(cases, list) or not cases:
        raise ValueError("cases must be a non-empty list")
    seen: set[str] = set()
    for case in cases:
        case_id = case.get("id")
        if not case_id or case_id in seen:
            raise ValueError(f"duplicate or missing case id: {case_id}")
        seen.add(case_id)
        if not case.get("question"):
            raise ValueError(f"{case_id}: question is required")
        if not case.get("category"):
            raise ValueError(f"{case_id}: category is required")
    return payload


def login(base_url: str, env_file: Path, timeout: float) -> str:
    password = load_env(env_file).get("ADMIN_PASSWORD")
    if not password:
        raise RuntimeError("ADMIN_PASSWORD is missing; pass --token or provide deploy/.env")
    request = urllib.request.Request(
        f"{base_url.rstrip('/')}/auth/login",
        data=json.dumps({"username": "admin", "password": password}).encode(),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=timeout) as response:
        payload = json.loads(response.read())
    token = payload.get("data", {}).get("token")
    if str(payload.get("code")) != "0" or not token:
        raise RuntimeError(f"login failed: {payload.get('message')}")
    return str(token)


def request_sse(
    base_url: str,
    token: str,
    question: str,
    mode: str,
    conversation_id: str,
    timeout_seconds: float,
    collection_name: str | None,
) -> dict[str, Any]:
    params = {
        "question": question,
        "conversationId": conversation_id[:20],
        "executionMode": mode,
    }
    if collection_name:
        params["collectionName"] = collection_name
    query = urllib.parse.urlencode(params)
    request = urllib.request.Request(
        f"{base_url.rstrip('/')}/rag/v3/chat?{query}",
        headers={"Authorization": token, "Accept": "text/event-stream"},
    )
    started = time.monotonic()
    raw_parts: list[str] = []
    done = False
    error: str | None = None
    try:
        with urllib.request.urlopen(request, timeout=timeout_seconds) as response:
            while True:
                try:
                    raw = response.readline()
                except socket.timeout:
                    error = "socket timeout"
                    break
                if not raw:
                    break
                line = raw.decode("utf-8", errors="replace")
                raw_parts.append(line)
                if "[DONE]" in line:
                    done = True
                    break
                if time.monotonic() - started > timeout_seconds - 2:
                    error = "client deadline reached"
                    break
    except urllib.error.HTTPError as exc:
        error = f"http {exc.code}: {exc.read().decode(errors='replace')[:300]}"
    except Exception as exc:  # noqa: BLE001 - report client-side eval failures
        error = repr(exc)
    raw_text = "".join(raw_parts)
    answer = extract_answer(raw_text)
    latency_ms = round((time.monotonic() - started) * 1000)
    return {
        "mode": mode,
        "done": done or "[DONE]" in raw_text,
        "latencyMs": latency_ms,
        "rawBytes": len(raw_text.encode("utf-8")),
        "answer": answer,
        "answerChars": len(answer),
        "imageCount": len(IMAGE_RE.findall(answer)),
        "asksClarification": any(term in answer for term in CLARIFICATION_TERMS),
        "error": error,
    }


def extract_answer(raw_text: str) -> str:
    chunks: list[str] = []
    for line in raw_text.splitlines():
        if not line.startswith("data:"):
            continue
        data = line[5:].strip()
        if not data or data == "[DONE]":
            continue
        try:
            obj = json.loads(data)
        except json.JSONDecodeError:
            continue
        if obj.get("type") == "response" and obj.get("delta") is not None:
            chunks.append(str(obj["delta"]))
    return "".join(chunks)


def evaluate_answer(case: dict[str, Any], result: dict[str, Any]) -> dict[str, Any]:
    answer = result["answer"]
    required_terms = case.get("requiredTerms") or []
    required_term_groups = case.get("requiredTermGroups") or []
    required_any = case.get("requiredTermsAny") or []
    forbidden_terms = case.get("forbiddenTerms") or []
    expected_entities = case.get("expectedEntities") or []
    min_image_count = int(case.get("minImageCount") or 0)

    missing_required = [term for term in required_terms if term not in answer]
    missing_required_groups = [
        group
        for group in required_term_groups
        if not isinstance(group, list) or not any(str(term) in answer for term in group)
    ]
    missing_entities = [term for term in expected_entities if term not in answer]
    forbidden_hits = [term for term in forbidden_terms if term in answer]
    required_any_hit = not required_any or any(term in answer for term in required_any)

    checks = {
        "done": bool(result["done"]),
        "noError": not result.get("error"),
        "notClarification": not result["asksClarification"],
        "entitiesPresent": not missing_entities,
        "requiredTermsPresent": not missing_required,
        "requiredTermGroupsPresent": not missing_required_groups,
        "requiredAnyPresent": required_any_hit,
        "forbiddenTermsAbsent": not forbidden_hits,
        "imageCountEnough": result["imageCount"] >= min_image_count,
    }
    passed = all(checks.values())
    return {
        "passed": passed,
        "checks": checks,
        "missingEntities": missing_entities,
        "missingRequiredTerms": missing_required,
        "missingRequiredTermGroups": missing_required_groups,
        "requiredAny": required_any,
        "requiredAnyHit": required_any_hit,
        "forbiddenHits": forbidden_hits,
        "imageCount": result["imageCount"],
        "latencyMs": result["latencyMs"],
        "answerChars": result["answerChars"],
        "preview": re.sub(r"\s+", " ", answer)[:500],
        "error": result.get("error"),
    }


def summarize(results: list[dict[str, Any]]) -> dict[str, Any]:
    modes = ["RAG", "AGENT"]
    summary: dict[str, Any] = {"caseCount": len(results)}
    for mode in modes:
        mode_results = [case["modes"][mode] for case in results]
        pass_count = sum(1 for item in mode_results if item["passed"])
        latencies = [item["latencyMs"] for item in mode_results if item["latencyMs"]]
        summary[mode] = {
            "passCount": pass_count,
            "passRate": round(pass_count / len(mode_results), 4) if mode_results else 0,
            "avgLatencyMs": round(sum(latencies) / len(latencies)) if latencies else None,
            "clarificationCount": sum(
                1 for item in mode_results if not item["checks"]["notClarification"]
            ),
            "imageFailures": sum(
                1 for item in mode_results if not item["checks"]["imageCountEnough"]
            ),
        }
    regressions = [
        case["id"]
        for case in results
        if case["modes"]["RAG"]["passed"] and not case["modes"]["AGENT"]["passed"]
    ]
    agent_wins = [
        case["id"]
        for case in results
        if not case["modes"]["RAG"]["passed"] and case["modes"]["AGENT"]["passed"]
    ]
    latency_pairs = [
        case["modes"]["AGENT"]["latencyMs"] - case["modes"]["RAG"]["latencyMs"]
        for case in results
        if case["modes"]["AGENT"]["latencyMs"] and case["modes"]["RAG"]["latencyMs"]
    ]
    summary["agentRegressions"] = regressions
    summary["agentWins"] = agent_wins
    summary["avgAgentExtraLatencyMs"] = (
        round(sum(latency_pairs) / len(latency_pairs)) if latency_pairs else None
    )
    return summary


def main() -> int:
    args = parse_args()
    dataset = load_cases(args.cases)
    cases = dataset["cases"]
    if args.case_id:
        selected = set(args.case_id)
        cases = [case for case in cases if case["id"] in selected]
        missing = selected - {case["id"] for case in cases}
        if missing:
            raise ValueError(f"unknown case id(s): {', '.join(sorted(missing))}")
    if args.limit is not None:
        if args.limit < 1:
            raise ValueError("--limit must be >= 1")
        cases = cases[: args.limit]
    if args.validate_only:
        print(json.dumps({"valid": True, "caseCount": len(cases)}, ensure_ascii=False))
        return 0

    token = args.token or login(args.base_url, args.env_file, args.timeout_seconds)
    results: list[dict[str, Any]] = []
    for index, case in enumerate(cases, start=1):
        print(f"[{index}/{len(cases)}] {case['id']}", flush=True)
        modes: dict[str, Any] = {}
        for mode in dataset.get("modes") or ["RAG", "AGENT"]:
            raw = request_sse(
                args.base_url,
                token,
                case["question"],
                mode,
                f"cmp{index}{mode.lower()}",
                args.timeout_seconds,
                args.collection_name,
            )
            modes[mode] = evaluate_answer(case, raw)
            print(
                f"  {mode}: passed={modes[mode]['passed']} "
                f"latencyMs={modes[mode]['latencyMs']} "
                f"images={modes[mode]['imageCount']}",
                flush=True,
            )
        results.append(
            {
                "id": case["id"],
                "category": case["category"],
                "difficulty": case.get("difficulty"),
                "agentExpectedValue": case.get("agentExpectedValue"),
                "question": case["question"],
                "modes": modes,
            }
        )

    report = {
        "schemaVersion": "1.0",
        "dataset": str(args.cases).replace("\\", "/"),
        "collectionName": args.collection_name,
        "generatedAt": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "summary": summarize(results),
        "cases": results,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(json.dumps(report["summary"], ensure_ascii=False, indent=2))
    print(f"report: {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
