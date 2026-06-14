"""用户手册自检:章节齐全、占位符存在"""
from __future__ import annotations
import sys
from pathlib import Path

from docx import Document

HERE = Path(__file__).parent
DOCX = HERE / "用户手册.docx"
MD = HERE / "用户手册.md"

EXPECTED_CHAPTERS = [
    "第 1 章", "第 2 章", "第 3 章", "第 4 章",
    "第 5 章", "第 6 章", "第 7 章", "第 8 章",
]
EXPECTED_PLACEHOLDERS = ["《XX 有限公司》", "《zhangsan@example.com》"]
EXPECTED_IMG_MARKERS = [
    "图 3-1", "图 4-1", "图 4-2", "图 5-1", "图 5-2",
    "图 6-1", "图 6-2", "图 6-3", "图 6-4",
]


def check_md() -> list[str]:
    errors: list[str] = []
    text = MD.read_text(encoding="utf-8")
    for ch in EXPECTED_CHAPTERS:
        if ch not in text:
            errors.append(f"缺少章节: {ch}")
    for ph in EXPECTED_PLACEHOLDERS:
        if ph not in text:
            errors.append(f"缺少占位符: {ph}")
    img_count = sum(text.count(m) for m in EXPECTED_IMG_MARKERS)
    if img_count < 8:
        errors.append(f"图占位符不足: 至少 8 个,当前 {img_count}")
    return errors


def check_docx() -> list[str]:
    errors: list[str] = []
    if not DOCX.exists():
        return [f"缺少文件: {DOCX}"]
    doc = Document(DOCX)
    text = "\n".join(p.text for p in doc.paragraphs)
    for ch in EXPECTED_CHAPTERS:
        if ch not in text:
            errors.append(f"docx 缺少章节: {ch}")
    return errors


def main() -> int:
    errors = check_md() + check_docx()
    if errors:
        for e in errors:
            print(f"[FAIL] {e}")
        return 1
    print("[OK] 用户手册自检通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())
