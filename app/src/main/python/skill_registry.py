#!/usr/bin/env python3
"""
skill_registry.py — 技能注册表（技能框架挂载：新模块 1/2）
=============================================================

Superpowers 对应：skills/ 目录约定 —— 一个技能 = 一个目录 + 一个 SKILL.md。
Hermes 对应：tools/skills_tool.py + agent/prompt_builder.py 的 build_skills_system_prompt
             （渐进式披露：索引只给 name+description，全文按需加载）。
Kimi Code 对应：packages/agent-core/src/skill/ 的 SkillDefinition + registry。

职责：
  1. 扫描 skills/ 目录，解析每个 SKILL.md 的 frontmatter（name/description）
  2. 按需加载技能全文（load_skill 工具的背后实现）
  3. 生成技能索引文本（注入系统提示词，Hermes 式 <available_skills> 块）

扩展性落点：加一个新技能 = 在 skills/ 下放一个 SKILL.md 文件，零代码改动。
"""

from __future__ import annotations

import pathlib
import re
from typing import Dict, List, Optional

SKILL_FILE = "SKILL.md"
FM_START = "---"
# 技能总开关的约定名（向后兼容旧 API；新代码优先用 BOOTSTRAP_PREFIX）
BOOTSTRAP_SKILL = "using-superpowers"
# 新协议：以 using- 为前缀的 skill 都视为 bootstrap（commit 3/4 引入）
# ——允许 axgraph 的 using-axgraph、superpowers 的 using-superpowers 等
# 各自 plugin 拥有自己的 bootstrap，V2 在会话开始时全部注入。
BOOTSTRAP_PREFIX = "using-"


class SkillRegistry:
    """扫描并加载技能文件。

    Args:
        skills_dir: 单个 skills/ 根目录路径（向后兼容）
                    默认：与本文件同级的 skills/
        skills_dirs: 多个 skills/ 根目录路径列表（commit 3 新增）
                     允许多个 plugin 各自提供 skills/，被同一个
                     SkillRegistry 一起索引。冲突时按列表后者覆盖前者。

    例（commit 3 协议）：
        SkillRegistry(skills_dirs=[
            "~/.minimal-agent-v2/plugins/managed/superpowers/skills",
            "~/.minimal-agent-v2/plugins/managed/axgraph/skills",
        ])
    """

    def __init__(self, skills_dir: Optional[str] = None, skills_dirs: Optional[list[str]] = None):
        if skills_dirs is not None:
            self.skills_dirs: list[pathlib.Path] = [pathlib.Path(d) for d in skills_dirs]
        elif skills_dir is not None:
            self.skills_dirs = [pathlib.Path(skills_dir)]
        else:
            self.skills_dirs = [pathlib.Path(__file__).parent / "skills"]
        # 向后兼容：原 self.skills_dir 仍可用（取第一个 dir 或默认）
        self.skills_dir = self.skills_dirs[0]
        self._index: Dict[str, dict] = {}
        self.scan()

    # ── 目录扫描 ──────────────────────────────────────────────────────────

    def scan(self) -> None:
        """重新扫描所有 skills/ 目录，重建索引。

        约定：<skills_dir>/<name>/SKILL.md，frontmatter 里必须有 name 和 description。
        多 dir 时按列表顺序扫描，后者同名覆盖前者。
        """
        self._index = {}
        for skills_dir in self.skills_dirs:
            if not skills_dir.is_dir():
                continue
            for skill_dir in sorted(skills_dir.iterdir()):
                if not skill_dir.is_dir():
                    continue
                skill_file = skill_dir / SKILL_FILE
                if not skill_file.is_file():
                    continue
                meta = self._parse_frontmatter(skill_file.read_text(encoding="utf-8"))
                name = meta.get("name", skill_dir.name)
                self._index[name] = {
                    "path": str(skill_file),
                    "source_dir": str(skills_dir),
                "description": meta.get("description", ""),
            }

    # ── 查询接口 ──────────────────────────────────────────────────────────

    def list_skills(self) -> List[dict]:
        """返回 [{name, description}]。"""
        return [
            {"name": name, "description": meta["description"]}
            for name, meta in sorted(self._index.items())
        ]

    def has_skill(self, name: str) -> bool:
        return name in self._index

    def load(self, name: str) -> Optional[str]:
        """加载技能全文（含 frontmatter）。技能不存在返回 None。"""
        meta = self._index.get(name)
        if meta is None:
            return None
        return pathlib.Path(meta["path"]).read_text(encoding="utf-8")

    def has_bootstrap(self) -> bool:
        """向后兼容：任一 bootstrap 存在即 True。

        新代码优先用 bootstraps() 拿全列表（commit 3 起）。
        """
        return self.has_skill(BOOTSTRAP_SKILL) or bool(self.bootstraps())

    def bootstraps(self) -> list[dict]:
        """返回所有 bootstrap 技能的 [{name, path}]，按 name 字典序。

        协议约定（commit 3）：任何以 'using-' 为前缀的 skill 都视为
        bootstrap —— 允许 axgraph 的 using-axgraph、superpowers 的
        using-superpowers 各自 plugin 拥有自己的总开关。

        V2 在会话开始时遍历此列表，逐一灌进 system prompt。
        """
        return sorted(
            (
                {"name": name, "path": meta["path"]}
                for name, meta in self._index.items()
                if name.startswith(BOOTSTRAP_PREFIX)
            ),
            key=lambda x: x["name"],
        )

    def index_text(self) -> str:
        """生成技能索引文本（Hermes 式：只列 name + description）。

        对应 Hermes agent/prompt_builder.py:1490 build_skills_system_prompt
        的 <available_skills> 块——注入系统提示词，让模型知道"有什么技能、
        什么场景该加载哪个"。全文仍由 load_skill 按需加载（渐进式披露）。
        """
        skills = self.list_skills()
        if not skills:
            return ""
        lines = ["<available_skills>", "你有以下技能可用（遇到相关场景必须先用 load_skill 工具加载全文再行动）："]
        for s in skills:
            lines.append(f"- {s['name']}: {s['description']}")
        lines.append("</available_skills>")
        return "\n".join(lines)

    # ── frontmatter 解析（零依赖简化版）────────────────────────────────────

    @staticmethod
    def _parse_frontmatter(text: str) -> Dict[str, str]:
        """解析 YAML frontmatter：只取 name 和 description 两个字段。

        支持 description: | 块描述符（commit 3 补：plugins 普遍使用，
        例如 axgraph 的 using-axgraph SKILL.md）。V2 之前版本只能解
        单行 description，遇到 `|` 块描述符时会把 '|' 当成 description
        字符——这个 commit 修复。

        不引入 pyyaml 依赖（与 agent-harness 协议设计一致）。
        """
        result: Dict[str, str] = {}
        lines = text.splitlines()
        if not lines or lines[0].strip() != FM_START:
            return result

        in_block_desc = False
        block_indent = ""

        for line in lines[1:]:
            if line.strip() == FM_START:
                break

            if in_block_desc:
                # 块描述符的延续行：必须以空白开头；否则结束
                if line.startswith((" ", "\t")):
                    stripped = line.lstrip()
                    if stripped:
                        result["description"] = (
                            (result.get("description", "") + " " + stripped).strip()
                            if result.get("description")
                            else stripped
                        )
                    continue
                else:
                    in_block_desc = False
                    block_indent = ""

            m = re.match(r"^(\w+):\s*(.*)$", line)
            if m and m.group(1) in ("name", "description"):
                key = m.group(1)
                value = m.group(2).strip().strip('"').strip("'")
                if value in ("|", ">"):
                    # 块描述符——后续空白开头的行作为延续
                    in_block_desc = True
                    block_indent = ""
                    continue
                result[key] = value
        return result
