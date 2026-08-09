# -*- coding: utf-8 -*-
import json, hashlib, re, datetime as dt
from pathlib import Path

"""Generate the P-card release-time quick-reference index for the Gakumas KB.

Reads the P-card corpus manifest (output/gamekee-gakumas-pcard-v1) plus the
H.I.F community corpus (output/gakumas-hif-community), sorts every card by
release date (newest first) and writes an import-ready community document
(output/gakumas-pcard-index) whose first chunk answers "最新的P卡是哪张"
style questions deterministically (vector-only retrieval cannot surface the
newest card for "latest" phrasing).

Usage: python scripts/demo/generate_pcard_time_index.py
"""

PCARD_MANIFEST = Path("output/gamekee-gakumas-pcard-v1/manifest.json")
HIF_MANIFEST = Path("output/gakumas-hif-community/manifest.json")
OUT = Path("output/gakumas-pcard-index")
MAX_CHARS = 1800


def load_manifest(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def parse_date(value) -> dt.date | None:
    try:
        return dt.date.fromisoformat(str(value)[:10])
    except Exception:
        return None


def split_sections(markdown: str) -> list[str]:
    parts = re.split(r"(?m)^(?=## )", markdown)
    return [p for p in parts if p.strip()]


def split_long_section(section: str) -> list[str]:
    """Split an oversized section on line boundaries, keeping lines intact."""
    chunks: list[str] = []
    current = ""
    for line in section.split("\n"):
        candidate = current + ("\n" if current else "") + line
        if len(candidate) > MAX_CHARS and current:
            chunks.append(current)
            current = line
        else:
            current = candidate
    if current.strip():
        chunks.append(current)
    return chunks


def main() -> None:
    items: list[tuple[dt.date, str]] = []
    for manifest_path in (PCARD_MANIFEST, HIF_MANIFEST):
        for doc in load_manifest(manifest_path).get("documents", []):
            release = parse_date(doc.get("releaseDate"))
            if release:
                items.append((release, doc["title"]))
    items.sort(key=lambda x: x[0], reverse=True)
    total = len(items)
    newest = items[0][0]

    lines = [
        "# P卡实装时间速查（最新优先）",
        "",
        "社区整理：本文档由本地语料 manifest 自动生成，按实装时间从新到旧排列，供「最新P卡 / 实装时间比较」类问题参考。",
        "",
        f"已收录 P 卡 {total} 张。",
        "",
        "## 最新P卡速答",
        "",
        f"问：最新的P卡是哪张？\n答：{items[0][1]}，实装时间 {newest.isoformat()}。",
    ]
    same_date = [title for (release, title) in items if release == newest]
    if len(same_date) > 1:
        lines.append(
            f"问：最新一批实装的P卡有哪些？\n答：{newest.isoformat()} 同期实装：{'；'.join(same_date)}。"
        )
    lines += ["", "## P卡实装时间一览（新→旧）", ""]
    for index, (release, title) in enumerate(items, 1):
        lines.append(f"{index}. {title}｜实装时间 {release.isoformat()}")
    lines += ["", f"共 {total} 张。完整卡面/数值以各卡专属页为准。"]
    markdown = "\n".join(lines)

    OUT.mkdir(parents=True, exist_ok=True)
    file_name = "01-900005-P卡实装时间速查-最新优先.md"

    prefix = "类型：reference\n标题：P卡实装时间速查（最新优先）\n来源：社区整理（自动生成）\n\n"
    chunks: list[str] = []
    for section in split_sections(markdown):
        if len(prefix) + len(section) <= MAX_CHARS:
            chunks.append(section)
        else:
            chunks.extend(split_long_section(section))
    chunk_contents = [prefix + c.strip() for c in chunks]

    json_file = file_name.rsplit(".", 1)[0] + ".semantic-chunks.json"
    (OUT / json_file).write_text(
        json.dumps(chunk_contents, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    (OUT / file_name).write_text(markdown, encoding="utf-8")

    manifest = {
        "schemaVersion": 2,
        "source": "community",
        "gameAlias": "gakumas",
        "note": "P卡实装时间速查索引：由本地语料 manifest 自动生成，最新优先。",
        "chunking": {"strategy": "identity prefix + heading sections", "maxChunkChars": MAX_CHARS, "overlapChars": 0},
        "documents": [
            {
                "contentId": 900005,
                "title": "P卡实装时间速查（最新优先）",
                "sourceUrl": "https://www.gamekee.com/gakumas/",
                "file": file_name,
                "semanticChunksFile": json_file,
                "semanticChunkCount": len(chunk_contents),
                "pageType": "reference",
                "images": [],
                "releaseDate": newest.isoformat(),
                "sourceTime": "2026-08-10T00:00:00+08:00",
                "sha256": hashlib.sha256(markdown.encode("utf-8")).hexdigest(),
            }
        ],
    }
    (OUT / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    print(f"wrote {file_name} chunks={len(chunk_contents)} cards={total} newest={newest}")


if __name__ == "__main__":
    main()
