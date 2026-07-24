#!/usr/bin/env python3
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements. See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0.

"""Validate and execute the Agentic Retrieval AR0 baseline dataset."""

from __future__ import annotations

import argparse
import json
import math
import os
import statistics
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Any


REQUIRED_CASE_FIELDS = {
    "id",
    "category",
    "complexity",
    "question",
    "expected_subquestions",
    "reference_doc_ids",
    "required_facts",
    "should_refuse",
}


@dataclass(frozen=True)
class CaseResult:
    case_id: str
    category: str
    complexity: str
    should_refuse: bool
    reference_doc_ids: list[str]
    retrieved_doc_ids: list[str]
    sub_intents: list[str]
    latency_ms: int
    recall_at_5: float | None
    reciprocal_rank: float | None
    all_reference_docs_hit: bool | None
    retrieval_empty: bool
    error: str | None


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Run the ragent Agentic Retrieval AR0 baseline."
    )
    parser.add_argument(
        "--dataset",
        type=Path,
        default=Path("resources/eval/agentic-retrieval/v1/cases.json"),
    )
    parser.add_argument(
        "--base-url",
        default="http://127.0.0.1:9090/api/koawa-agent",
        help="Application base URL including context path.",
    )
    parser.add_argument(
        "--token",
        default=os.getenv("RAGENT_EVAL_TOKEN", ""),
        help="Optional Authorization token. Prefer automatic local login.",
    )
    parser.add_argument(
        "--env-file",
        type=Path,
        default=Path("deploy/.env"),
        help="Used for admin login when no token is supplied.",
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=Path("output/eval/agentic-retrieval"),
    )
    parser.add_argument("--timeout-seconds", type=float, default=180.0)
    parser.add_argument(
        "--validate-only",
        action="store_true",
        help="Validate dataset structure and referenced local documents.",
    )
    return parser.parse_args()


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


def login(base_url: str, env_file: Path, timeout_seconds: float) -> str:
    password = load_env(env_file).get("ADMIN_PASSWORD")
    if not password:
        raise RuntimeError("ADMIN_PASSWORD is missing from the env file")
    request = urllib.request.Request(
        f"{base_url.rstrip('/')}/auth/login",
        data=json.dumps({"username": "admin", "password": password}).encode("utf-8"),
        headers={"Content-Type": "application/json", "Accept": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=timeout_seconds) as response:
        payload = json.loads(response.read().decode("utf-8"))
    if str(payload.get("code")) != "0" or not payload.get("data", {}).get("token"):
        raise RuntimeError(f"login failed: {payload.get('message')}")
    return str(payload["data"]["token"])


def load_dataset(path: Path) -> dict[str, Any]:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError as exc:
        raise ValueError(f"dataset does not exist: {path}") from exc
    except json.JSONDecodeError as exc:
        raise ValueError(f"dataset is not valid JSON: {exc}") from exc


def validate_dataset(dataset: dict[str, Any], repository_root: Path) -> list[str]:
    errors: list[str] = []
    if dataset.get("schema_version") != "1.0":
        errors.append("schema_version must be 1.0")
    if not dataset.get("dataset_id"):
        errors.append("dataset_id is required")

    documents = dataset.get("documents")
    if not isinstance(documents, list) or not documents:
        errors.append("documents must be a non-empty list")
        documents = []

    known_doc_ids: set[str] = set()
    for index, document in enumerate(documents):
        if not isinstance(document, dict):
            errors.append(f"documents[{index}] must be an object")
            continue
        doc_id = document.get("doc_id")
        doc_path = document.get("path")
        if not isinstance(doc_id, str) or not doc_id.strip():
            errors.append(f"documents[{index}].doc_id is required")
        elif doc_id in known_doc_ids:
            errors.append(f"duplicate document id: {doc_id}")
        else:
            known_doc_ids.add(doc_id)
        if not isinstance(doc_path, str) or not doc_path.strip():
            errors.append(f"documents[{index}].path is required")
        elif not (repository_root / doc_path).is_file():
            errors.append(f"document path does not exist: {doc_path}")

    cases = dataset.get("cases")
    if not isinstance(cases, list):
        errors.append("cases must be a list")
        return errors
    if len(cases) < 20:
        errors.append(f"at least 20 cases are required, found {len(cases)}")

    case_ids: set[str] = set()
    refusal_count = 0
    agentic_count = 0
    for index, case in enumerate(cases):
        if not isinstance(case, dict):
            errors.append(f"cases[{index}] must be an object")
            continue
        missing = REQUIRED_CASE_FIELDS - set(case)
        if missing:
            errors.append(f"cases[{index}] missing fields: {sorted(missing)}")
        case_id = case.get("id")
        if not isinstance(case_id, str) or not case_id.strip():
            errors.append(f"cases[{index}].id is required")
        elif case_id in case_ids:
            errors.append(f"duplicate case id: {case_id}")
        else:
            case_ids.add(case_id)
        complexity = case.get("complexity")
        if complexity not in {"simple", "agentic"}:
            errors.append(f"{case_id or index}: complexity must be simple or agentic")
        elif complexity == "agentic":
            agentic_count += 1
        minimum_subquestions = 2 if complexity == "agentic" else 1
        if not isinstance(case.get("expected_subquestions"), list) or len(
            case.get("expected_subquestions", [])
        ) < minimum_subquestions:
            errors.append(
                f"{case_id or index}: at least {minimum_subquestions} "
                "subquestions required"
            )
        reference_doc_ids = case.get("reference_doc_ids")
        if not isinstance(reference_doc_ids, list):
            errors.append(f"{case_id or index}: reference_doc_ids must be a list")
            reference_doc_ids = []
        unknown = set(reference_doc_ids) - known_doc_ids
        if unknown:
            errors.append(
                f"{case_id or index}: unknown reference docs: {sorted(unknown)}"
            )
        should_refuse = case.get("should_refuse")
        if not isinstance(should_refuse, bool):
            errors.append(f"{case_id or index}: should_refuse must be boolean")
        elif should_refuse:
            refusal_count += 1
            if reference_doc_ids:
                errors.append(
                    f"{case_id or index}: refusal case cannot have reference docs"
                )
            if not case.get("refusal_reason"):
                errors.append(f"{case_id or index}: refusal_reason is required")
        elif not reference_doc_ids:
            errors.append(
                f"{case_id or index}: answerable case needs reference docs"
            )
        if not isinstance(case.get("required_facts"), list):
            errors.append(f"{case_id or index}: required_facts must be a list")

    if refusal_count < 3:
        errors.append(f"at least 3 refusal cases are required, found {refusal_count}")
    if agentic_count < 20:
        errors.append(f"at least 20 agentic cases are required, found {agentic_count}")
    return errors


def request_eval(
    base_url: str,
    question: str,
    token: str,
    timeout_seconds: float,
) -> dict[str, Any]:
    query = urllib.parse.urlencode({"question": question})
    url = f"{base_url.rstrip('/')}/rag/eval?{query}"
    headers = {"Accept": "application/json"}
    if token:
        headers["Authorization"] = token
    request = urllib.request.Request(url, headers=headers, method="GET")
    with urllib.request.urlopen(request, timeout=timeout_seconds) as response:
        payload = json.loads(response.read().decode("utf-8"))
    if str(payload.get("code")) != "0":
        raise RuntimeError(
            f"eval endpoint returned code={payload.get('code')}: "
            f"{payload.get('message') or payload.get('msg')}"
        )
    data = payload.get("data")
    if not isinstance(data, dict):
        raise RuntimeError("eval endpoint returned no data object")
    return data


def reciprocal_rank(retrieved: list[str], references: list[str]) -> float:
    reference_set = set(references)
    for index, doc_id in enumerate(retrieved[:5], start=1):
        if doc_id in reference_set:
            return 1.0 / index
    return 0.0


def execute_case(
    case: dict[str, Any],
    base_url: str,
    token: str,
    timeout_seconds: float,
) -> CaseResult:
    started = time.monotonic()
    try:
        data = request_eval(base_url, case["question"], token, timeout_seconds)
        latency_ms = int(data.get("latencyMs") or ((time.monotonic() - started) * 1000))
        retrieved = [
            value
            for value in data.get("retrievedDocIds", [])
            if isinstance(value, str) and value
        ]
        sub_intents = [
            value
            for value in data.get("subIntents", [])
            if isinstance(value, str) and value
        ]
        references = list(case["reference_doc_ids"])
        if case["should_refuse"]:
            recall = None
            rank = None
            all_hit = None
        else:
            top_five = set(retrieved[:5])
            recall = len(top_five.intersection(references)) / len(references)
            rank = reciprocal_rank(retrieved, references)
            all_hit = set(references).issubset(top_five)
        return CaseResult(
            case_id=case["id"],
            category=case["category"],
            complexity=case["complexity"],
            should_refuse=case["should_refuse"],
            reference_doc_ids=references,
            retrieved_doc_ids=retrieved,
            sub_intents=sub_intents,
            latency_ms=latency_ms,
            recall_at_5=recall,
            reciprocal_rank=rank,
            all_reference_docs_hit=all_hit,
            retrieval_empty=not retrieved,
            error=None,
        )
    except (urllib.error.URLError, TimeoutError, ValueError, RuntimeError) as exc:
        return CaseResult(
            case_id=case["id"],
            category=case["category"],
            complexity=case["complexity"],
            should_refuse=case["should_refuse"],
            reference_doc_ids=list(case["reference_doc_ids"]),
            retrieved_doc_ids=[],
            sub_intents=[],
            latency_ms=int((time.monotonic() - started) * 1000),
            recall_at_5=None,
            reciprocal_rank=None,
            all_reference_docs_hit=None,
            retrieval_empty=True,
            error=str(exc),
        )


def percentile(values: list[int], percentile_value: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    rank = max(1, math.ceil(percentile_value * len(ordered)))
    return float(ordered[rank - 1])


def summarize(dataset: dict[str, Any], results: list[CaseResult]) -> dict[str, Any]:
    successful = [result for result in results if result.error is None]
    answerable = [
        result for result in successful if not result.should_refuse
    ]
    refusal = [result for result in successful if result.should_refuse]
    recalls = [
        result.recall_at_5
        for result in answerable
        if result.recall_at_5 is not None
    ]
    ranks = [
        result.reciprocal_rank
        for result in answerable
        if result.reciprocal_rank is not None
    ]
    latencies = [result.latency_ms for result in successful]
    summary = {
        "dataset_id": dataset["dataset_id"],
        "case_count": len(results),
        "successful_case_count": len(successful),
        "error_count": len(results) - len(successful),
        "answerable_case_count": len(answerable),
        "refusal_case_count": len(refusal),
        "recall_at_5": statistics.fmean(recalls) if recalls else None,
        "mrr_at_5": statistics.fmean(ranks) if ranks else None,
        "all_reference_docs_hit_rate": (
            statistics.fmean(
                1.0 if result.all_reference_docs_hit else 0.0
                for result in answerable
            )
            if answerable
            else None
        ),
        "no_answer_retrieval_empty_rate": (
            statistics.fmean(
                1.0 if result.retrieval_empty else 0.0 for result in refusal
            )
            if refusal
            else None
        ),
        "average_latency_ms": (
            statistics.fmean(latencies) if latencies else None
        ),
        "p95_latency_ms": percentile(latencies, 0.95) if latencies else None,
        "average_sub_intent_count": (
            statistics.fmean(len(result.sub_intents) for result in successful)
            if successful
            else None
        ),
    }
    summary["by_complexity"] = {
        complexity: summarize_group(
            [result for result in successful if result.complexity == complexity]
        )
        for complexity in ("simple", "agentic")
    }
    return summary


def summarize_group(results: list[CaseResult]) -> dict[str, Any]:
    answerable = [result for result in results if not result.should_refuse]
    refusal = [result for result in results if result.should_refuse]
    recalls = [
        result.recall_at_5
        for result in answerable
        if result.recall_at_5 is not None
    ]
    ranks = [
        result.reciprocal_rank
        for result in answerable
        if result.reciprocal_rank is not None
    ]
    return {
        "case_count": len(results),
        "recall_at_5": statistics.fmean(recalls) if recalls else None,
        "mrr_at_5": statistics.fmean(ranks) if ranks else None,
        "no_answer_retrieval_empty_rate": (
            statistics.fmean(
                1.0 if result.retrieval_empty else 0.0 for result in refusal
            )
            if refusal
            else None
        ),
        "average_latency_ms": (
            statistics.fmean(result.latency_ms for result in results)
            if results
            else None
        ),
        "average_sub_intent_count": (
            statistics.fmean(len(result.sub_intents) for result in results)
            if results
            else None
        ),
    }


def result_to_dict(result: CaseResult) -> dict[str, Any]:
    return {
        "case_id": result.case_id,
        "category": result.category,
        "complexity": result.complexity,
        "should_refuse": result.should_refuse,
        "reference_doc_ids": result.reference_doc_ids,
        "retrieved_doc_ids": result.retrieved_doc_ids,
        "sub_intents": result.sub_intents,
        "latency_ms": result.latency_ms,
        "recall_at_5": result.recall_at_5,
        "reciprocal_rank": result.reciprocal_rank,
        "all_reference_docs_hit": result.all_reference_docs_hit,
        "retrieval_empty": result.retrieval_empty,
        "error": result.error,
    }


def format_percent(value: float | None) -> str:
    return "N/A" if value is None else f"{value * 100:.2f}%"


def build_markdown(
    dataset: dict[str, Any],
    summary: dict[str, Any],
    results: list[CaseResult],
) -> str:
    lines = [
        f"# {dataset['dataset_id']} 基线报告",
        "",
        "## 汇总",
        "",
        f"- 用例数：{summary['case_count']}",
        f"- 成功执行：{summary['successful_case_count']}",
        f"- 执行错误：{summary['error_count']}",
        f"- Recall@5：{format_percent(summary['recall_at_5'])}",
        f"- MRR@5：{format_percent(summary['mrr_at_5'])}",
        (
            "- 全部目标文档命中率："
            f"{format_percent(summary['all_reference_docs_hit_rate'])}"
        ),
        (
            "- 无答案问题空召回率："
            f"{format_percent(summary['no_answer_retrieval_empty_rate'])}"
        ),
        f"- 平均延迟：{summary['average_latency_ms'] or 0:.2f} ms",
        f"- P95 延迟：{summary['p95_latency_ms'] or 0:.2f} ms",
        (
            "- 平均子问题数："
            f"{summary['average_sub_intent_count'] or 0:.2f}"
        ),
        "",
        "## 按复杂度分组",
        "",
        "| 分组 | 用例数 | Recall@5 | MRR@5 | 平均延迟 | 平均子问题数 |",
        "| --- | ---: | ---: | ---: | ---: | ---: |",
    ]
    for complexity, group in summary["by_complexity"].items():
        lines.append(
            f"| {complexity} | {group['case_count']} | "
            f"{format_percent(group['recall_at_5'])} | "
            f"{format_percent(group['mrr_at_5'])} | "
            f"{group['average_latency_ms'] or 0:.2f} ms | "
            f"{group['average_sub_intent_count'] or 0:.2f} |"
        )
    lines.extend(
        [
        "",
        "## 明细",
        "",
        "| 用例 | 分组 | 类别 | Recall@5 | RR | 目标全命中 | 延迟 | 错误 |",
        "| --- | --- | --- | ---: | ---: | --- | ---: | --- |",
        ]
    )
    for result in results:
        recall = (
            "N/A"
            if result.recall_at_5 is None
            else f"{result.recall_at_5:.2f}"
        )
        rank = (
            "N/A"
            if result.reciprocal_rank is None
            else f"{result.reciprocal_rank:.2f}"
        )
        all_hit = (
            "N/A"
            if result.all_reference_docs_hit is None
            else str(result.all_reference_docs_hit)
        )
        error = (result.error or "").replace("|", "\\|")
        lines.append(
            f"| {result.case_id} | {result.complexity} | {result.category} | {recall} | "
            f"{rank} | {all_hit} | {result.latency_ms} ms | {error} |"
        )
    lines.extend(
        [
            "",
            "## 解释限制",
            "",
            "- 本报告只评估检索证据，不评估最终答案文本。",
            "- 无答案问题暂以“没有召回文档”作为保守代理指标。",
            "- required_facts 的语义覆盖将在 AR1 EvidenceEvaluator 中评估。",
            "- 模型调用次数和 Token 需由 Trace 接入后补充。",
            "",
        ]
    )
    return "\n".join(lines)


def main() -> int:
    args = parse_args()
    repository_root = Path(__file__).resolve().parents[2]
    dataset_path = (
        args.dataset
        if args.dataset.is_absolute()
        else repository_root / args.dataset
    )
    dataset = load_dataset(dataset_path)
    validation_errors = validate_dataset(dataset, repository_root)
    if validation_errors:
        for error in validation_errors:
            print(f"VALIDATION_ERROR={error}", file=sys.stderr)
        return 2

    print(f"DATASET_VALID=true")
    print(f"DATASET_ID={dataset['dataset_id']}")
    print(f"CASE_COUNT={len(dataset['cases'])}")
    if args.validate_only:
        return 0

    env_file = (
        args.env_file
        if args.env_file.is_absolute()
        else repository_root / args.env_file
    )
    token = args.token or login(args.base_url, env_file, args.timeout_seconds)
    results: list[CaseResult] = []
    for case in dataset["cases"]:
        result = execute_case(
            case,
            args.base_url,
            token,
            args.timeout_seconds,
        )
        results.append(result)
        status = "ERROR" if result.error else "OK"
        print(f"CASE={result.case_id} STATUS={status}", flush=True)

    summary = summarize(dataset, results)
    output_dir = (
        args.output_dir
        if args.output_dir.is_absolute()
        else repository_root / args.output_dir
    )
    output_dir.mkdir(parents=True, exist_ok=True)
    json_path = output_dir / "baseline.json"
    markdown_path = output_dir / "baseline.md"
    report = {
        "schema_version": "1.0",
        "generated_at_epoch_ms": int(time.time() * 1000),
        "base_url": args.base_url,
        "summary": summary,
        "results": [result_to_dict(result) for result in results],
    }
    json_path.write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    markdown_path.write_text(
        build_markdown(dataset, summary, results),
        encoding="utf-8",
    )

    print(f"REPORT_JSON={json_path}")
    print(f"REPORT_MARKDOWN={markdown_path}")
    print(f"RECALL_AT_5={summary['recall_at_5']}")
    print(f"MRR_AT_5={summary['mrr_at_5']}")
    print(f"ERROR_COUNT={summary['error_count']}")
    return 1 if summary["error_count"] else 0


if __name__ == "__main__":
    raise SystemExit(main())
