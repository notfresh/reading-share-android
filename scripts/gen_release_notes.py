#!/usr/bin/env python3
"""从 dev-docs/release.md 抽取最近 N 个版本,生成 app assets 里的纯文本更新日志。

单一来源仍是 release.md —— 本脚本只在构建时把前 N 节压成移动端可读的纯文本
(去掉 markdown 强调符/内联代码反引号),由 app/build.gradle 的 preBuild 钩子调用。
产物 app/src/main/assets/release_notes.txt 不进 git(每次构建重新生成)。
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "dev-docs" / "release.md"
DST = ROOT / "app" / "src" / "main" / "assets" / "release_notes.txt"
MAX_VERSIONS = 10


def strip_markdown(line: str) -> str:
    """压掉移动端 TextView 显示不好的 markdown 记号,保留纯文字。"""
    line = re.sub(r"\*\*(.+?)\*\*", r"\1", line)      # **bold** → bold
    line = re.sub(r"`([^`]+)`", r"\1", line)          # `code` → code
    line = re.sub(r"^\s*[-*]\s+", "· ", line)          # 列表符 → ·
    return line.rstrip()


def main() -> None:
    lines = SRC.read_text(encoding="utf-8").splitlines()

    # 按 `# 版本号` 一级标题分节(文件顶部就是第一个 #,顺序即新→旧)
    sections: list[list[str]] = []
    for line in lines:
        if re.match(r"^# \d", line):
            sections.append([line])
        elif sections:
            sections[-1].append(line)

    picked = sections[:MAX_VERSIONS]
    if not picked:
        sys.exit(f"no version sections found in {SRC}")

    out: list[str] = []
    for i, sec in enumerate(picked):
        if i > 0:
            out.append("")
            out.append("────────────────────")
            out.append("")
        for line in sec:
            if not line.strip() and (not out or not out[-1].strip()):
                continue  # 压连续空行
            out.append(strip_markdown(line))

    DST.parent.mkdir(parents=True, exist_ok=True)
    DST.write_text("\n".join(out).strip() + "\n", encoding="utf-8")
    print(f"gen_release_notes: {len(picked)} versions -> {DST}")


if __name__ == "__main__":
    main()
