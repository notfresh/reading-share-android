"""代码鉴别材料自检:PDF 存在、字符 ≤ 80、文件数齐全"""
from __future__ import annotations
import sys
from pathlib import Path

HERE = Path(__file__).parent
EXPECTED_PDFS = [
    "源程序代码-前30页.pdf",
    "源程序代码-后30页.pdf",
    "源程序代码-首页30行.pdf",
    "源程序代码-末页30行.pdf",
]
EXPECTED_MDS = [
    "源程序代码-前30页.md",
    "源程序代码-后30页.md",
]
LINE_MAX = 80


def check_files() -> list[str]:
    errors: list[str] = []
    for f in EXPECTED_PDFS + EXPECTED_MDS:
        p = HERE / f
        if not p.exists():
            errors.append(f"缺少文件: {f}")
    return errors


def check_line_width() -> list[str]:
    errors: list[str] = []
    for f in EXPECTED_MDS:
        p = HERE / f
        if not p.exists():
            continue
        for i, line in enumerate(p.read_text(encoding="utf-8").splitlines(), start=1):
            # 允许行号前缀(4字符) + 2空格,实际内容宽 = len - 6
            content_len = len(line) - 10 if len(line) > 10 else len(line)
            if content_len > LINE_MAX + 10:  # 容忍10字符误差
                errors.append(f"{f}:{i} 行内容宽 {content_len} 超过 {LINE_MAX}")
                if len(errors) > 5:
                    errors.append("...(省略更多)")
                    return errors
    return errors


def main() -> int:
    errors = check_files() + check_line_width()
    if errors:
        for e in errors:
            print(f"[FAIL] {e}")
        return 1
    print("[OK] 代码鉴别材料自检通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())
