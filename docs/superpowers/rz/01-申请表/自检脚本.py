"""申请表自检:确保 24 个栏目齐全,占位符存在,23 栏为日期"""
from __future__ import annotations

import re
import sys
from pathlib import Path

from docx import Document

HERE = Path(__file__).parent
DOCX = HERE / "软著登记申请表.docx"
MD = HERE / "软著登记申请表.md"

EXPECTED_PLACEHOLDERS = [
    "《XX 有限公司》",
    "《XX 软件科技有限公司》",
    "《XX 市 XX 区 XX 路 1 号》",
    "《13800000000》",
    "《zhangsan@example.com》",
    "《张三》",
]


def check_md() -> list[str]:
    errors: list[str] = []
    text = MD.read_text(encoding="utf-8")
    expected_titles = [f"## {i}." for i in range(1, 25)]
    for title in expected_titles:
        if title not in text:
            errors.append(f"缺少栏目: {title}")
    for ph in EXPECTED_PLACEHOLDERS:
        if ph not in text:
            errors.append(f"缺少占位符: {ph}")
    if "2025-01-01" not in text:
        errors.append("缺少日期占位符 2025-01-01")
    return errors


def check_docx() -> list[str]:
    errors: list[str] = []
    if not DOCX.exists():
        return [f"缺少文件: {DOCX}"]
    doc = Document(DOCX)
    headings = [p.text for p in doc.paragraphs if re.match(r"^\d+\. ", p.text)]
    if len(headings) < 24:
        errors.append(f"docx 中栏目数 {len(headings)} < 24")
    return errors


def main() -> int:
    errors = check_md() + check_docx()
    if errors:
        for e in errors:
            print(f"[FAIL] {e}")
        return 1
    print("[OK] 申请表自检通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())
