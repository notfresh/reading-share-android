#!/usr/bin/env python3
"""
tool_runner.py — 模块 3/5：工具执行器（ToolRunner）
=====================================================

Hermes 对应: tools/registry.py（@tool 装饰器 + schema 生成）
             + agent/tool_executor.py（工具调度与执行）
             + agent/tool_dispatch_helpers.py（分发辅助）

职责：工具注册 + 执行。包含：
  - @tool 装饰器：函数签名 → JSON Schema（传承自 MinimalAgentV1）
  - 工具查找（注册表）
  - 参数解析（JSON 反序列化）
  - 非法工具/非法参数的防御
  - 结果规范化（统一为字符串回填）

设计传承（自 V1 tools.py）：
  - @tool(description=...) 装饰器 + Google-style docstring Args 提取
  - 类型注解 → JSON Schema type（str→string, int→number, ...）
  - 无默认值参数 → required
  - 工具执行异常作为 Observation 回填给 LLM（不崩溃）—— Agent 从失败中学习的关键
"""

from __future__ import annotations

import inspect
import json
import re
from typing import Any, Callable, get_type_hints

# ── 工具注册中心（模块级，传承 V1 设计）────────────────────────────────────

_TOOL_REGISTRY: dict[str, dict] = {}
_TOOL_SCHEMAS: list[dict] = []


def _parse_docstring_params(docstring: str) -> dict[str, str]:
    """解析 Google-style docstring 的 Args 区域 → {参数名: 描述}。"""
    if not docstring:
        return {}
    params = {}
    in_args = False
    for line in docstring.split("\n"):
        stripped = line.strip()
        if stripped.startswith("Args:"):
            in_args = True
            continue
        if in_args:
            if stripped.startswith("Returns:") or stripped.startswith("Raises:"):
                break
            m = re.match(r"\s{8}(\w+):\s*(.*)", line)
            if m:
                params[m.group(1)] = m.group(2).strip()
            elif stripped == "":
                continue
            elif not line.startswith("    "):
                break
    return params


def _pytype_to_jsontype(py_type: type) -> str:
    mapping = {
        str: "string",
        int: "number",
        float: "number",
        bool: "boolean",
        list: "array",
        dict: "object",
    }
    return mapping.get(py_type, "string")


def tool(name: str = "", description: str = ""):
    """注册一个函数为 LLM 可调用的工具（传承自 MinimalAgentV1 tools.py）。

    类型注解 → JSON Schema type；参数名 → properties；
    无默认值的参数标记为 required；docstring Args: 区域自动提取参数描述。

    Usage:
        @tool(description="执行 shell 命令")
        def bash(command: str) -> str:
            \"\"\"Execute a command.

            Args:
                command: The command string to run
            \"\"\"
            ...

    文件位置对应 Hermes: tools/registry.py 的工具注册逻辑。
    """
    def decorator(func: Callable) -> Callable:
        tool_name = name or func.__name__
        tool_desc = description or func.__doc__ or ""

        sig = inspect.signature(func)
        hints = get_type_hints(func)
        param_descs = _parse_docstring_params(func.__doc__ or "")

        properties = {}
        required = []
        for pname, param in sig.parameters.items():
            if pname == "return":
                continue
            jtype = _pytype_to_jsontype(hints.get(pname, str))
            desc = param_descs.get(pname, f"Parameter {pname}")
            properties[pname] = {"type": jtype, "description": desc}
            if param.default is inspect.Parameter.empty:
                required.append(pname)

        schema = {
            "type": "function",
            "function": {
                "name": tool_name,
                "description": tool_desc,
                "parameters": {
                    "type": "object",
                    "properties": properties,
                    "required": required,
                },
            },
        }

        _TOOL_REGISTRY[tool_name] = {"fn": func, "schema": schema}
        _TOOL_SCHEMAS.append(schema)
        return func

    return decorator


def register_external(
    name: str,
    description: str,
    parameters: dict,
    fn: Callable,
) -> dict:
    """注册一个**外部提供 schema** 的工具。

    与 {@link tool} 装饰器的区别: schema 不从函数签名 / docstring 生成,
    而是调用方直接给。用于"schema 的单一来源在宿主侧(Java)"的场景 ——
    Android 集成里工具 schema 定义在 Java 的 AgentToolRegistry(跟 DAO 同源),
    Python 侧启动时拉取后用本函数注入,避免手抄两份 schema。

    Args:
        name: 工具名
        description: 工具描述(会进 LLM 的 tools 参数)
        parameters: JSON Schema 的 parameters 部分
            ({"type": "object", "properties": {...}, "required": [...]})
        fn: 实际执行体,签名与 parameters 一致

    Returns:
        注册进 _TOOL_SCHEMAS 的 schema dict
    """
    schema = {
        "type": "function",
        "function": {
            "name": name,
            "description": description,
            "parameters": parameters,
        },
    }
    _TOOL_REGISTRY[name] = {"fn": fn, "schema": schema}
    _TOOL_SCHEMAS.append(schema)
    return schema


# ── 工具执行器（V2 类化封装）───────────────────────────────────────────────


class ToolRunner:
    """工具执行器：从注册表执行 tool_call，返回 role=tool 结果消息。

    Hermes 对应: agent/tool_executor.py — 教学版串行执行 + 基础防御。
    注册表保持模块级（传承 V1），ToolRunner 提供面向循环的干净接口。
    """

    def __init__(self, verbose: bool = False):
        self.verbose = verbose
        # Plan Mode V2：守卫检查点（None = 不检查）。由 CLI 注入 plan_guard。
        self.guard = None
        # AgentIgnore：路径级权限校验（R/W/X，None = 不检查）。由 CLI 注入。
        self.agent_ignore = None

    # ── 供循环/提示词使用的只读接口 ────────────────────────────────────

    def schemas(self) -> list[dict]:
        """全部工具的 JSON Schema（发给 LLM 的 tools 参数）。"""
        return list(_TOOL_SCHEMAS)

    def names(self) -> list[str]:
        return sorted(_TOOL_REGISTRY.keys())

    def summary(self) -> str:
        """生成工具摘要文本（供 system prompt 注入）。

        Hermes 对应: prompt_builder 注入工具定义。
        传承 V1: get_tool_summary()
        """
        lines = []
        for schema in _TOOL_SCHEMAS:
            fn = schema["function"]
            params = fn["parameters"]
            args_list = ", ".join(
                f"{name}: {info['type']}"
                for name, info in params.get("properties", {}).items()
            )
            lines.append(f"  • {fn['name']}({args_list}) — {fn['description']}")
        return "\n".join(lines)

    # ── 执行 ────────────────────────────────────────────────────────────

    def execute(self, tool_call: dict) -> dict:
        """执行单个 tool_call，返回 role=tool 的结果消息。

        Args:
            tool_call: {"id", "type", "function": {"name", "arguments"}}

        Returns:
            {"role": "tool", "tool_call_id": ..., "content": ...}

        防御要点（传承 V1 + 增强）：
          - 工具不存在 → 错误回填（告诉 LLM 可用工具）
          - 参数 JSON 解析失败 → 错误回填
          - 参数不匹配（TypeError）→ 错误回填
          - 执行异常 → 错误回填（不抛出，错误也是 Observation）
        """
        fn_name = tool_call["function"]["name"]
        tool_call_id = tool_call.get("id", "")

        entry = _TOOL_REGISTRY.get(fn_name)
        if entry is None:
            content = f"错误：工具 '{fn_name}' 不存在。可用工具：{', '.join(self.names())}"
            if self.verbose:
                print(f"  ⚠️  {content}")
            return {"role": "tool", "tool_call_id": tool_call_id, "content": content}

        raw_args = tool_call["function"].get("arguments", "") or "{}"
        try:
            fn_args = json.loads(raw_args) if raw_args.strip() else {}
        except json.JSONDecodeError as e:
            content = f"错误：工具 '{fn_name}' 参数不是合法 JSON（{e}）。请检查参数格式。"
            if self.verbose:
                print(f"  ⚠️  {content}")
            return {"role": "tool", "tool_call_id": tool_call_id, "content": content}

        if self.verbose:
            print(f"     → {fn_name}({raw_args[:120]})")

        # Plan Mode V2：守卫检查（硬约束——违规调用被拒绝，工具不执行）
        # 对应 Kimi plan-mode-guard-deny：deny 发生在工具执行前，模型收到错误结果
        if self.guard is not None:
            reason = self.guard(fn_name, fn_args)
            if reason:
                content = f"错误：{reason}"
                if self.verbose:
                    print(f"     🚫 {content}")
                return {"role": "tool", "tool_call_id": tool_call_id, "content": content}

        # AgentIgnore：路径级权限校验（R 读 / W 写 / X 执行），deny 语义
        # 路径命中规则时，对应权限被排除；若工具需要的权限已被排除 → 拒绝（工具不执行），错误 Observation 回填给 LLM
        if self.agent_ignore is not None:
            reason = self.agent_ignore.check_tool(fn_name, fn_args)
            if reason:
                content = f"错误：{reason}"
                if self.verbose:
                    print(f"     🚫 {content}")
                return {"role": "tool", "tool_call_id": tool_call_id, "content": content}

        try:
            raw = entry["fn"](**fn_args)
            result = str(raw)
        except TypeError as e:
            result = json.dumps({"error": f"参数不匹配: {e}"}, ensure_ascii=False)
        except Exception as e:
            result = json.dumps({"error": f"{e}"}, ensure_ascii=False)

        return {"role": "tool", "tool_call_id": tool_call_id, "content": result}

    def execute_all(self, tool_calls: list[dict]) -> list[dict]:
        """串行执行一批 tool_calls（教学版串行，Hermes 可并发）。

        Hermes 对应: _execute_tool_calls()（串行）/
        _execute_tool_calls_concurrent()（并发）— 独立调用可并行。
        """
        return [self.execute(tc) for tc in tool_calls]
