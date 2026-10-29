#!/usr/bin/env python3
"""
turn_context.py — 模块 5/5：回合上下文准备（TurnContext）
============================================================

Hermes 对应: agent/turn_context.py 的 build_turn_context()
             + agent/prompt_builder.py（系统提示词构建）

职责：一次对话回合开始前的"一次性准备"——
  - 构建系统提示词（注入工具描述）
  - 组装初始 messages（system + user）
  - 清洗/规范化用户消息
  - 预检 API 凭证

教学要点：
  这个模块回答"Hermes 在 while 循环开始前做了什么"。
  走读卡 3 提到 run_conversation 的 prologue（conversation_loop.py:641 build_turn_context），
  就是这里的对应物——循环只负责"反复跑"，准备只做一次。
"""

from __future__ import annotations

from typing import Any


def build_system_prompt(tool_schemas: list[dict], personality: str = "", skills_index: str = "") -> str:
    """构建系统提示词，注入当前注册的工具描述 + 技能索引。

    Hermes 对应: agent/prompt_builder.py — 动态注入工具定义 + skills + 记忆。
    教学版原版只注入工具列表（skills_index 默认为空）；
    传入技能索引时，追加 <available_skills> 块——这就是"技能框架挂载点"：
    让模型知道有哪些技能、什么场景该加载哪个（渐进式披露，全文按需加载）。
    """
    if not tool_schemas:
        tool_summary = "（当前没有可用工具）"
    else:
        lines = []
        for t in tool_schemas:
            name = t.get("function", {}).get("name", "?")
            desc = t.get("function", {}).get("description", "")
            lines.append(f"- {name}: {desc}")
        tool_summary = "\n".join(lines)

    base = f"""你是 MinimalAgent Agent V2，一个可以调用工具帮助用户完成任务的 AI 助手。

你有以下工具可用：
{tool_summary}

使用规则：
1. 需要执行操作时，用 tool_calls 调用合适的工具
2. 工具返回结果后，根据结果继续推理或给出最终答案
3. 如果一次需要多个独立操作，可以同时调用多个工具
4. 用中文回复用户"""

    # Plan Mode V2：复杂任务规划规则（规划模式 = 状态机 + 守卫）
    base += """

复杂任务规划规则：
0. 进入规划模式前先自评需求清晰度——若模糊（目标/范围/验收标准不明确），
   主动用对话/AskUserQuestion 向用户澄清关键问题，再 enter_plan_mode
1. 遇到多步骤、多文件或需要设计决策的任务，先调用 enter_plan_mode 进入规划模式
2. 进入后：用只读工具调研 → 用 write 把计划写入计划文件（每个阶段含：明确目标 + 可执行的验收条件）
3. 规划模式下只能写入计划文件，写其他文件会被守卫拒绝（必须先 exit_plan_mode）
4. 计划写好后调用 exit_plan_mode 开始执行
5. 每完成一个阶段，对照该阶段的验收条件验证结果；不满足则修正后重新验证
6. 全部阶段完成后，做整体验证：逐条核对所有验收条件，全部满足才向用户报告完成"""

    if skills_index:
        base += f"\n\n{skills_index}"
    if personality:
        base += f"\n\n{personality}"
    return base


def build_initial_messages(
    user_message: str,
    tool_schemas: list[dict],
    system_prompt_override: str | None = None,
    personality: str = "",
    skill_bootstrap: str = "",  # 向后兼容：单字符串（superpowers 老协议）
    skill_bootstraps: list[str] | None = None,  # 新协议（commit 3）：多 bootstrap 列表
    skills_index: str = "",
) -> list[dict]:
    """组装回合初始 messages：system + user。

    Hermes 对应: build_turn_context() 返回的 ctx.messages。
    教学版原版固定两段（system + user）；挂载技能框架时：
      - skills_index:   技能索引（<available_skills> 块）拼进系统提示词
      - skill_bootstrap / skill_bootstraps:
                        "技能总开关"全文拼进系统提示词（Superpowers 式启动注入）
                        多 bootstrap（commit 3 协议：每个 plugin 自己的 using-*）
                        按传入顺序逐一拼接，新协议优先。
    """
    system_prompt = system_prompt_override or build_system_prompt(tool_schemas, personality, skills_index)

    # 新协议：多 bootstrap 优先；老协议（单字符串）向后兼容
    bootstraps: list[str] = []
    if skill_bootstraps:
        bootstraps = list(skill_bootstraps)
    elif skill_bootstrap:
        bootstraps = [skill_bootstrap]

    for bs in bootstraps:
        if bs:
            system_prompt = system_prompt + "\n\n" + bs

    return [
        {"role": "system", "content": system_prompt},
        {"role": "user", "content": user_message},
    ]


def sanitize_user_message(message: Any) -> str:
    """清洗用户消息：保证是纯文本字符串。

    Hermes 对应: sanitize_surrogates + 多模态内容折叠。
    教学版只处理最基本的 str/list 情况——真实 Hermes 还会处理
    图片、孤立代理字符（surrogate）、超长截断等。
    """
    if isinstance(message, str):
        return message
    if isinstance(message, list):
        # 多模态 content parts：取所有 text 部分拼接
        parts = []
        for p in message:
            if isinstance(p, dict) and p.get("type") == "text":
                parts.append(str(p.get("text", "")))
            elif isinstance(p, str):
                parts.append(p)
        return "\n".join(parts)
    return str(message)


def check_credentials(api_key: str, base_url: str) -> tuple[bool, str]:
    """预检 API 凭证。返回 (是否可用, 错误信息)。

    Hermes 对应: _ensure_runtime_credentials() — 检查 + 自动轮换 key。
    教学版只做存在性检查，不做轮换。
    """
    if not api_key:
        return False, "缺少 API key（设置 DEEPSEEK_API_KEY 或 OPENAI_API_KEY）"
    if not base_url:
        return False, "缺少 base_url"
    return True, ""


def plan_status_line() -> str:
    """生成当前规划模式状态注入文本（模型可见，V3 审批闭环）。

    规划模式激活时返回一段状态描述，供 REPL 拼在用户消息前；
    未激活返回空串。状态在框架内存（plan_mode.status），模型通过这段
    注入文本感知——状态不是靠模型"记住"的。

    对应 Hermes/Kimi：规划状态注入 agent 上下文（plan mode 提示词动态拼装）。
    """
    from plan_mode import plan_mode

    if not plan_mode.is_active or plan_mode.plan_path is None:
        return ""
    return (
        f"【框架状态】当前处于规划模式（{plan_mode.status_label}）。"
        f"计划文件：{plan_mode.plan_path}\n"
        "规则：只允许只读调研与写入计划文件（write_plan）；"
        "调用 exit_plan_mode 前必须先获得用户 /approve 批准，"
        "否则会被守卫拒绝。\n\n"
    )
