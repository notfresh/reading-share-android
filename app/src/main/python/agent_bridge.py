"""agent_bridge.py — Android(Chaquopy) 侧的 agent 内核入口
============================================================

这是 minimal-agent-v2 在 Android 上的**唯一入口**。桌面端入口是 cli.py,
两端共用同一套内核(conversation_loop / tool_runner / message_store /
llm_client / turn_context / loop_controller / skill_registry),
只有"怎么启动"和"工具从哪来"不同。

职责:
  1. 接收 Java 侧注入的能力宿主(AgentBridgeHost)
  2. 从宿主拉取工具 schema(单一来源在 Java —— 跟 DAO 同源)
  3. 把这些工具注册进 V2 的 tool_runner(用 register_external)
  4. 跑核心循环,返回结构化结果

与桌面端的差异(只有这几点):
  - 工具 schema 来自 Java,不是 Python 侧的 @tool 装饰器
  - 没有 CLI / REPL / session 文件持久化(会话由 Java 侧持有)
  - 写操作确认走 guard 机制(复用 V2 tool_runner 的 guard 检查点)

设计文档: minimal-agent-v2 docs/design-20261029-android-integration.md
"""

from __future__ import annotations

import json
from typing import Any, Optional

# ── Java 侧能力宿主(由 Java 启动时注入)────────────────────────────────
# 注入的是 Java 对象的代理,提供:
#   schemas_json()          -> str  工具 schema 列表(JSON 数组)
#   invoke(name, args_json) -> str  执行一个工具,返回 JSON 结果
#   confirm(name, args)     -> str  请求用户确认;返回 "" = 同意,非空 = 拒绝原因
#   requires_confirm(name)  -> bool 该工具是否需要确认(单一来源在 Java)
#   model_name() / base_url() / api_key() -> str  LLM 配置
_JAVA_BRIDGE: Any = None

# ── 会话状态(多轮上下文)───────────────────────────────────────────────────
# 照搬 V2 桌面端的接法(session_manager.py:308):
#     result = loop.run(user_message=..., initial_messages=session.messages)
#     session.messages = result.get("messages", [])
#
# 桌面端由 session_manager 持有 session 对象;Android 侧由本模块持有 ——
# **为什么放在 Python 侧而不是 Java 侧**:
#   1. 零编解码 —— 历史消息是 Python 原生的 list[dict],放 Java 要来回转 JSON
#   2. 进程级 —— Fragment 重建(转屏/导航返回)后会话还在,agent 不会"失忆"
#   3. Java 侧只管显示 —— 显示列表(用户文本/agent 文本)跟 LLM 消息流是两回事
#
# 存哪(内存 vs 文件)—— 第一版只做内存态,进程死了就没。
# 要落盘就是往 app 私有目录写个 JSON,后续再说(YAGNI)。
_SESSION_MESSAGES: Optional[list] = None


def reset_session() -> None:
    """清空会话(对应「新对话」)。"""
    global _SESSION_MESSAGES
    _SESSION_MESSAGES = None


def get_session_messages() -> list:
    """当前会话的完整消息流(调试/测试用)。"""
    return list(_SESSION_MESSAGES) if _SESSION_MESSAGES else []


def set_java_bridge(bridge: Any) -> None:
    """由 Java 侧在启动时调用,注入能力宿主。

    注意:这里存的是 Java 对象的引用。Java 侧刻意不让宿主持有任何
    Python 引用,避免形成跨语言引用环(Chaquopy 文档警告:这种环两个
    语言的 GC 都检测不到 → 内存泄漏)。
    """
    global _JAVA_BRIDGE
    _JAVA_BRIDGE = bridge


# ── 工具注册 ─────────────────────────────────────────────────────────────


def _make_tool_fn(name: str):
    """为一个工具生成执行体:统一转发到 Java 的 invoke。"""

    def _fn(**kwargs) -> str:
        if _JAVA_BRIDGE is None:
            return json.dumps({"ok": False, "error": "Java 桥接未初始化"}, ensure_ascii=False)
        args_json = json.dumps(kwargs, ensure_ascii=False)
        return str(_JAVA_BRIDGE.invoke(name, args_json))

    _fn.__name__ = name
    return _fn


def register_java_tools() -> list:
    """从 Java 拉 schema 并注册进 V2 的工具注册表。

    Returns:
        注册成功的工具名列表
    """
    if _JAVA_BRIDGE is None:
        return []

    from tool_runner import register_external

    raw = str(_JAVA_BRIDGE.schemas_json())
    try:
        schemas = json.loads(raw)
    except json.JSONDecodeError as e:
        raise RuntimeError(f"Java 侧工具 schema 不是合法 JSON: {e}") from e

    names = []
    for s in schemas:
        name = s.get("name")
        if not name:
            continue
        register_external(
            name=name,
            description=s.get("description", ""),
            parameters=s.get("parameters", {"type": "object", "properties": {}}),
            fn=_make_tool_fn(name),
        )
        names.append(name)
    return names


# ── 写操作确认(复用 V2 的 guard 检查点)─────────────────────────────────


def confirm_guard(name: str, args: dict) -> Optional[str]:
    """V2 的 guard 检查点:命中写工具 → 请 Java 弹确认框。

    语义与 V2 现有 guard 一致:
      返回 None → 放行
      返回字符串 → 拒绝,字符串回灌给 LLM 当作 Observation

    **写工具清单不在这里硬编码** —— 由 Java 侧的 requires_confirm 判定,
    单一来源在 AgentToolRegistry(跟 DAO 同源)。

    对应 V2 源码: tool_runner.py 的 `if self.guard is not None:` 检查点。
    """
    if _JAVA_BRIDGE is None:
        return "Java 桥接未初始化,无法请求确认"

    if not bool(_JAVA_BRIDGE.requires_confirm(name)):
        return None

    args_json = json.dumps(args, ensure_ascii=False)
    reason = str(_JAVA_BRIDGE.confirm(name, args_json) or "")
    if reason:
        # 用户拒绝 —— 把原因回灌给 LLM,让它换方案而不是卡死
        return f"用户拒绝了本次操作: {reason}"
    return None


# ── 对外入口 ─────────────────────────────────────────────────────────────


def run(
    user_text: str,
    system_prompt: Optional[str] = None,
    initial_messages: Optional[list] = None,
) -> str:
    """跑一个完整 agent 回合。**Java 侧的唯一调用点。**

    Args:
        user_text: 用户自然语言输入
        system_prompt: 覆盖默认系统提示词(可选)
        initial_messages: 历史消息(多轮上下文)。
            **照搬 V2 桌面端的接法**(session_manager.py:308):
                result = loop.run(user_message=..., initial_messages=session.messages)
                session.messages = result.get("messages", [])
            不传 = 每次都是全新会话(模型会"失忆")。

    Returns:
        JSON 字符串:
        {
          "final_response": str,        # LLM 的最终回答
          "messages": [...],            # 完整消息流 —— 宿主要存回它,才能多轮
          "api_calls": int,
          "tool_calls": int,
          "exit_reason": str,           # completed / max_turns_reached / error:*
          "error": str | null,
          "tools": [str]                # 本次注册的可用工具名
        }
    """
    from conversation_loop import ConversationLoop
    from llm_client import LLMClient
    from loop_controller import LoopController
    from tool_runner import ToolRunner

    if _JAVA_BRIDGE is None:
        return json.dumps(
            {
                "final_response": "",
                "messages": [],
                "api_calls": 0,
                "tool_calls": 0,
                "exit_reason": "error:no_bridge",
                "error": "Java 桥接未初始化",
                "tools": [],
            },
            ensure_ascii=False,
        )

    global _SESSION_MESSAGES

    tools = register_java_tools()

    runner = ToolRunner(verbose=False)
    # 写操作确认门 —— 复用 V2 的 guard 检查点,不新造机制
    runner.guard = confirm_guard

    llm = LLMClient(
        model=str(_JAVA_BRIDGE.model_name()),
        base_url=str(_JAVA_BRIDGE.base_url()),
        api_key=str(_JAVA_BRIDGE.api_key()),
        max_retries=2,
        timeout=90.0,
    )

    loop = ConversationLoop(
        llm=llm,
        tools=runner,
        controller=LoopController(max_turns=12),
        max_context_tokens=8000,
    )

    # 多轮上下文 —— 照搬 V2 桌面端接法(session_manager.py:308):
    #   传历史进去,再把返回的新消息流存回去。
    # initial_messages 显式传入时优先用它(测试/进阶调用);
    # 否则用模块级会话(Android 侧的默认路径)。
    history = initial_messages if initial_messages is not None else _SESSION_MESSAGES

    result = loop.run(
        user_text,
        system_prompt=system_prompt,
        initial_messages=history,
    )

    # 存回新消息流 —— 下一轮就有上下文了。
    # result["messages"] 已含完整的 assistant 回复,不需要额外 add_message
    # (与 V2 桌面端 session_manager.py:313 的处理一致)。
    _SESSION_MESSAGES = result.get("messages", [])

    result["tools"] = tools
    return json.dumps(result, ensure_ascii=False)
