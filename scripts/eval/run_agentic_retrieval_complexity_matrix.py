#!/usr/bin/env python3
"""Generate the AR3 rule-router offline confusion matrix."""

import argparse
import json
from pathlib import Path


def predict(case):
    score = 0
    if case["rewrittenParts"] >= 2:
        score += 2
    if case["resolvedParts"] >= 2:
        score += 2
    return "complex" if score >= 2 else "simple"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--cases", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    cases = json.loads(args.cases.read_text(encoding="utf-8"))
    counts = {"trueSimple": 0, "falseComplex": 0,
              "falseSimple": 0, "trueComplex": 0}
    rows = []
    for case in cases:
        predicted = predict(case)
        actual = case["label"]
        if actual == "simple" and predicted == "simple":
            counts["trueSimple"] += 1
        elif actual == "simple":
            counts["falseComplex"] += 1
        elif predicted == "simple":
            counts["falseSimple"] += 1
        else:
            counts["trueComplex"] += 1
        rows.append({"id": case["id"], "actual": actual, "predicted": predicted})
    simple_total = counts["trueSimple"] + counts["falseComplex"]
    complex_total = counts["trueComplex"] + counts["falseSimple"]
    result = {
        "schemaVersion": 1,
        "cases": len(cases),
        "matrix": counts,
        "simpleFalsePositiveRate": counts["falseComplex"] / simple_total,
        "complexFalseNegativeRate": counts["falseSimple"] / complex_total,
        "admission": {
            "simpleFalsePositiveRateBelow10Percent":
                counts["falseComplex"] / simple_total < 0.10
        },
        "results": rows,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        json.dumps(result, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8")


if __name__ == "__main__":
    main()
