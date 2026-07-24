#!/usr/bin/env python3
"""Build a reproducible Gakuen Idolmaster demo corpus from MediaWiki."""

import argparse
import datetime as dt
import json
import urllib.parse
import urllib.request
from pathlib import Path

API = "https://zh.wikipedia.org/w/api.php"
PAGES = [
    "学园偶像大师",
    "偶像大师系列",
    "長月葵",
    "小鹿奈緒",
    "薄井友里",
    "川村玲奈",
]
USER_AGENT = "ragent-agentic-retrieval-demo/1.0 (personal interview project)"


def fetch(title):
    query = urllib.parse.urlencode({
        "action": "query",
        "format": "json",
        "formatversion": "2",
        "prop": "extracts|info|revisions",
        "explaintext": "1",
        "inprop": "url",
        "rvprop": "ids|timestamp",
        "titles": title,
    })
    request = urllib.request.Request(
        f"{API}?{query}", headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=30) as response:
        page = json.load(response)["query"]["pages"][0]
    if page.get("missing"):
        return None
    revision = (page.get("revisions") or [{}])[0]
    return {
        "title": page["title"],
        "sourceUrl": page["fullurl"],
        "revisionId": revision.get("revid"),
        "revisionTimestamp": revision.get("timestamp"),
        "content": (page.get("extract") or "").strip(),
    }


def safe_name(index, title):
    return f"{index:02d}-" + "".join(
        character if character.isalnum() else "-"
        for character in title).strip("-") + ".md"


def render(article, fetched_at):
    attribution = (
        f"来源：{article['sourceUrl']}\n"
        f"页面：{article['title']}\n"
        f"修订版本：{article['revisionId']}\n"
        f"抓取时间：{fetched_at}\n"
        "许可：CC BY-SA 4.0（请同时遵守页面中可能存在的附加说明）"
    )
    return f"# {article['title']}\n\n{attribution}\n\n{article['content']}\n"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    fetched_at = dt.datetime.now(dt.timezone.utc).isoformat()
    manifest = []
    for index, title in enumerate(PAGES, 1):
        article = fetch(title)
        if not article or not article["content"]:
            continue
        file_name = safe_name(index, article["title"])
        (args.output / file_name).write_text(
            render(article, fetched_at), encoding="utf-8")
        manifest.append({
            "file": file_name,
            "kind": "wikipedia",
            **{key: value for key, value in article.items() if key != "content"},
        })
    (args.output / "manifest.json").write_text(
        json.dumps({
            "schemaVersion": 1,
            "fetchedAt": fetched_at,
            "license": "CC BY-SA 4.0",
            "api": API,
            "documents": manifest,
        }, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8")
    print(f"prepared {len(manifest)} Wikipedia documents in {args.output}")


if __name__ == "__main__":
    main()
