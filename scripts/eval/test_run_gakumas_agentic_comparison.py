# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements. See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0.

"""Tests for the Gakumas Single Pass/Agentic comparison report."""

from __future__ import annotations

import unittest

from scripts.eval.run_gakumas_agentic_comparison import (
    compact,
    nearest_rank_p95,
    normalize_source,
    source_recall,
    summarize,
)


class GakumasAgenticComparisonTests(unittest.TestCase):
    def test_normalize_source_removes_upload_prefix_and_extension(self) -> None:
        self.assertEqual("contest-rules-v2", normalize_source("03-contest-rules-v2.md"))

    def test_source_recall_accepts_uploaded_document_name(self) -> None:
        recall = source_recall(
            ["contest-rules-v1.md", "contest-rules-v2.md"],
            ["01-contest-rules-v2.md", "02-contest-rules-v1.md"],
        )

        self.assertEqual(1.0, recall)

    def test_source_recall_is_not_applicable_without_expected_sources(self) -> None:
        self.assertIsNone(source_recall([], ["学园偶像大师.md"]))

    def test_nearest_rank_p95_uses_nearest_rank_definition(self) -> None:
        self.assertEqual(100, nearest_rank_p95([10, 20, 30, 40, 100]))

    def test_compact_rejects_citation_outside_retrieved_catalog(self) -> None:
        result = compact(
            {
                "retrievedDocIds": ["contest-rules-v2"],
                "retrievedChunkIds": ["chunk-1"],
                "citationChunkIds": ["chunk-2"],
                "latencyMs": 25,
            },
            ["contest-rules-v2.md"],
        )

        self.assertFalse(result["citationCatalogValid"])
        self.assertEqual(1.0, result["sourceRecall"])

    def test_summarize_keeps_acl_result_explicitly_unverified(self) -> None:
        result = summarize(
            [
                {
                    "expectedSources": ["contest-rules-v2.md"],
                    "single": {
                        "sourceRecall": 0.0,
                        "latencyMs": 20,
                    },
                    "active": {
                        "sourceRecall": 1.0,
                        "latencyMs": 40,
                        "agenticFallbackToSinglePass": False,
                        "wouldRouteAgentic": True,
                        "citationCatalogValid": True,
                    },
                }
            ]
        )

        self.assertEqual(0.0, result["singleAverageSourceRecall"])
        self.assertEqual(1.0, result["activeAverageSourceRecall"])
        self.assertEqual(20, result["averageAgenticExtraLatencyMs"])
        self.assertEqual("not_exercised", result["aclCoverage"])


if __name__ == "__main__":
    unittest.main()
