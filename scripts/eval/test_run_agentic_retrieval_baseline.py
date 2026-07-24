#!/usr/bin/env python3
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements. See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0.

"""Unit tests for the AR0 baseline metrics and response contract."""

from __future__ import annotations

import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


MODULE_PATH = Path(__file__).with_name("run_agentic_retrieval_baseline.py")
SPEC = importlib.util.spec_from_file_location("agentic_retrieval_baseline", MODULE_PATH)
assert SPEC and SPEC.loader
baseline = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = baseline
SPEC.loader.exec_module(baseline)


class FakeResponse:
    def __init__(self, payload: dict) -> None:
        self.payload = payload

    def __enter__(self) -> "FakeResponse":
        return self

    def __exit__(self, *_args: object) -> None:
        return None

    def read(self) -> bytes:
        return json.dumps(self.payload).encode("utf-8")


class BaselineTests(unittest.TestCase):
    def test_request_eval_accepts_string_success_code(self) -> None:
        payload = {"code": "0", "data": {"retrievedDocIds": ["doc-a"]}}
        with patch.object(
            baseline.urllib.request,
            "urlopen",
            return_value=FakeResponse(payload),
        ):
            data = baseline.request_eval("http://localhost/api", "question", "", 1)
        self.assertEqual(["doc-a"], data["retrievedDocIds"])

    def test_reciprocal_rank_is_limited_to_top_five(self) -> None:
        retrieved = ["x1", "x2", "target", "x4", "x5", "target-2"]
        self.assertEqual(1 / 3, baseline.reciprocal_rank(retrieved, ["target"]))
        self.assertEqual(0.0, baseline.reciprocal_rank(retrieved, ["target-2"]))

    def test_summary_separates_answerable_and_refusal_cases(self) -> None:
        results = [
            baseline.CaseResult(
                "a",
                "cross_doc",
                "agentic",
                False,
                ["doc-a"],
                ["doc-a"],
                ["one", "two"],
                100,
                1.0,
                1.0,
                True,
                False,
                None,
            ),
            baseline.CaseResult(
                "b",
                "no_answer",
                "agentic",
                True,
                [],
                [],
                ["one", "two"],
                300,
                None,
                None,
                None,
                True,
                None,
            ),
        ]
        summary = baseline.summarize({"dataset_id": "test"}, results)
        self.assertEqual(1.0, summary["recall_at_5"])
        self.assertEqual(1.0, summary["no_answer_retrieval_empty_rate"])
        self.assertEqual(200.0, summary["average_latency_ms"])
        self.assertEqual(300.0, summary["p95_latency_ms"])
        self.assertEqual(2, summary["by_complexity"]["agentic"]["case_count"])

    def test_validate_dataset_requires_existing_document(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            dataset = {
                "schema_version": "1.0",
                "dataset_id": "test",
                "documents": [{"doc_id": "missing", "path": "missing.md"}],
                "cases": [],
            }
            errors = baseline.validate_dataset(dataset, root)
        self.assertIn("document path does not exist: missing.md", errors)
        self.assertIn("at least 20 cases are required, found 0", errors)


if __name__ == "__main__":
    unittest.main()
