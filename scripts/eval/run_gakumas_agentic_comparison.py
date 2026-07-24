#!/usr/bin/env python3
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements. See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0.

"""Compare Single Pass and forced Agentic Retrieval on the Gakumas fixture."""

from __future__ import annotations

import argparse
import json
import math
import re
import time
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--questions",
        type=Path,
        default=Path("resources/eval/agentic-retrieval/v1/gakumas-questions.json"),
    )
    parser.add_argument(
        "--base-url", default="http://127.0.0.1:9090/api/koawa-agent"
    )
    parser.add_argument("--env-file", type=Path, default=Path("deploy/.env"))
    parser.add_argument(
        "--output",
        type=Path,
        default=Path(
            "resources/eval/agentic-retrieval/v1/gakumas-comparison-summary.json"
        ),
    )
    parser.add_argument("--timeout-seconds", type=float, default=180.0)
    return parser.parse_args()


def load_env(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip().strip("'\"")
    return values


def login(base_url: str, env_file: Path, timeout: float) -> str:
    password = load_env(env_file).get("ADMIN_PASSWORD")
    if not password:
        raise RuntimeError("ADMIN_PASSWORD is missing")
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


def evaluate(
    base_url: str, token: str, question: str, mode: str, timeout: float
) -> dict[str, Any]:
    query = urllib.parse.urlencode({"question": question, "mode": mode})
    request = urllib.request.Request(
        f"{base_url.rstrip('/')}/rag/eval?{query}",
        headers={"Authorization": token, "Accept": "application/json"},
    )
    started = time.monotonic()
    with urllib.request.urlopen(request, timeout=timeout) as response:
        payload = json.loads(response.read())
    wall_ms = round((time.monotonic() - started) * 1000)
    if str(payload.get("code")) != "0":
        raise RuntimeError(str(payload.get("message")))
    result = payload["data"]
    result["clientWallLatencyMs"] = wall_ms
    return result


def normalize_source(value: str) -> str:
    value = re.sub(r"\.[^.]+$", "", value.strip()).lower()
    return re.sub(r"^\d+[-_ ]*", "", value)


def source_recall(expected: list[str], actual: list[str]) -> float | None:
    if not expected:
        return None
    normalized_actual = [normalize_source(item) for item in actual]
    hits = sum(
        any(
            normalize_source(reference) == candidate
            or normalize_source(reference) in candidate
            or candidate in normalize_source(reference)
            for candidate in normalized_actual
        )
        for reference in expected
    )
    return round(hits / len(expected), 4)


def nearest_rank_p95(values: list[int]) -> int:
    return sorted(values)[max(0, math.ceil(len(values) * 0.95) - 1)]


def summarize(cases: list[dict[str, Any]]) -> dict[str, Any]:
    comparable = [case for case in cases if case["expectedSources"]]
    single_recall = [case["single"]["sourceRecall"] for case in comparable]
    active_recall = [case["active"]["sourceRecall"] for case in comparable]
    single_latency = [case["single"]["latencyMs"] for case in cases]
    active_latency = [case["active"]["latencyMs"] for case in cases]
    return {
        "caseCount": len(cases),
        "comparableCaseCount": len(comparable),
        "singleAverageSourceRecall": round(sum(single_recall) / len(single_recall), 4),
        "activeAverageSourceRecall": round(sum(active_recall) / len(active_recall), 4),
        "singleP95LatencyMs": nearest_rank_p95(single_latency),
        "activeP95LatencyMs": nearest_rank_p95(active_latency),
        "averageAgenticExtraLatencyMs": round(
            sum(a - s for a, s in zip(active_latency, single_latency)) / len(cases)
        ),
        "activeFallbackRate": round(
            sum(case["active"]["agenticFallbackToSinglePass"] for case in cases)
            / len(cases),
            4,
        ),
        "productionRouteHitRate": round(
            sum(case["active"]["wouldRouteAgentic"] for case in cases) / len(cases),
            4,
        ),
        "citationCatalogValid": all(
            case["active"]["citationCatalogValid"] for case in cases
        ),
        "aclCoverage": "not_exercised",
        "aclCoverageReason": (
            "Fixture has no imported restricted document and this run uses the admin "
            "token; the ACL prompt is retained but cannot prove ordinary-user denial."
        ),
    }


def compact(result: dict[str, Any], expected: list[str]) -> dict[str, Any]:
    retrieved_chunks = set(result.get("retrievedChunkIds") or [])
    citation_chunks = result.get("citationChunkIds") or []
    return {
        "retrievedDocIds": result.get("retrievedDocIds") or [],
        "retrievedChunkIds": result.get("retrievedChunkIds") or [],
        "sourceRecall": source_recall(expected, result.get("retrievedDocIds") or []),
        "latencyMs": int(result.get("latencyMs") or 0),
        "clientWallLatencyMs": int(result.get("clientWallLatencyMs") or 0),
        "subIntents": result.get("subIntents") or [],
        "wouldRouteAgentic": bool(result.get("wouldRouteAgentic")),
        "complexityScore": result.get("complexityScore"),
        "complexityReasons": result.get("complexityReasons") or [],
        "agenticStopReason": result.get("agenticStopReason"),
        "agenticIterations": result.get("agenticIterations"),
        "agenticSufficient": result.get("agenticSufficient"),
        "agenticFallbackToSinglePass": bool(
            result.get("agenticFallbackToSinglePass")
        ),
        "citationIds": result.get("citationIds") or [],
        "citationChunkIds": citation_chunks,
        "citationCatalogValid": all(
            chunk_id in retrieved_chunks for chunk_id in citation_chunks
        ),
        "conflictedTaskIds": result.get("conflictedTaskIds") or [],
    }


def main() -> int:
    args = parse_args()
    questions = json.loads(args.questions.read_text(encoding="utf-8"))
    token = login(args.base_url, args.env_file, args.timeout_seconds)
    results: list[dict[str, Any]] = []
    for index, case in enumerate(questions, start=1):
        print(f"[{index}/{len(questions)}] {case['id']}: single", flush=True)
        single_raw = evaluate(
            args.base_url, token, case["question"], "single", args.timeout_seconds
        )
        print(f"[{index}/{len(questions)}] {case['id']}: active", flush=True)
        active_raw = evaluate(
            args.base_url, token, case["question"], "active", args.timeout_seconds
        )
        expected = case.get("expectedSources") or []
        results.append(
            {
                "id": case["id"],
                "label": case["label"],
                "question": case["question"],
                "expectedSources": expected,
                "mustReportConflict": bool(case.get("mustReportConflict")),
                "mustRefuseRestrictedEvidence": bool(
                    case.get("mustRefuseRestrictedEvidence")
                ),
                "single": compact(single_raw, expected),
                "active": compact(active_raw, expected),
            }
        )
    report = {
        "schemaVersion": "1.0",
        "dataset": str(args.questions).replace("\\", "/"),
        "comparisonSemantics": {
            "single": "rewrite + intent resolution + one retrieval",
            "active": "same initial retrieval followed by forced Agentic orchestration",
            "wouldRouteAgentic": "whether production complexity rules would select active",
        },
        "summary": summarize(results),
        "cases": results,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    print(json.dumps(report["summary"], ensure_ascii=False, indent=2))
    print(f"report: {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
