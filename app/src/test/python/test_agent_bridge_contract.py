"""agent_bridge 契约测试 —— 不需要 Android / 真机。

用 Fake Java 宿主替换真实的 AgentBridgeHost,验证 Python 侧的桥接逻辑:
  - schema 从 Java 拉取并注册进 V2 工具注册表
  - 工具执行转发到 Java 的 invoke
  - 写工具过 confirm_guard(拒绝时回灌字符串,LLM 能看见)
  - 拒绝时工具**不执行**(安全底线)

跑法:
    cd /root/projects/rs-android-agent/app/src/main/python
    python3 -m pytest ../test/python/test_agent_bridge_contract.py -v
"""
from __future__ import annotations

import json
import os
import sys

sys.path.insert(0, os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "main", "python"))

import agent_bridge
from tool_runner import ToolRunner, _TOOL_REGISTRY, _TOOL_SCHEMAS


class FakeJavaHost:
    """模拟 Java 侧 AgentBridgeHost 的行为。

    ``confirm_names`` 模拟 Java 的 requires_confirm 判定 —— Python 侧
    不该有自己的写工具清单,一切以 Java 为准。
    """

    def __init__(self, schemas, confirm_result="", confirm_names=None):
        self._schemas = schemas
        self._confirm_result = confirm_result
        self._confirm_names = confirm_names or {"add_tag_to_link", "set_link_remark"}
        self.invocations = []
        self.confirms = []

    def schemas_json(self):
        return json.dumps(self._schemas)

    def invoke(self, name, args_json):
        self.invocations.append((name, args_json))
        return json.dumps({"ok": True, "echo": args_json})

    def confirm(self, name, args_json):
        self.confirms.append((name, args_json))
        return self._confirm_result

    def requires_confirm(self, name):
        return name in self._confirm_names

    def model_name(self):
        return "deepseek-chat"

    def base_url(self):
        return "http://127.0.0.1:1/v1"

    def api_key(self):
        return "sk-fake"


SCHEMAS = [
    {
        "name": "search_links",
        "description": "搜索链接",
        "parameters": {
            "type": "object",
            "properties": {"query": {"type": "string"}},
            "required": ["query"],
        },
    },
    {
        "name": "add_tag_to_link",
        "description": "加标签",
        "parameters": {
            "type": "object",
            "properties": {
                "link_id": {"type": "number"},
                "tag": {"type": "string"},
            },
            "required": ["link_id", "tag"],
        },
    },
]


def setup_function():
    _TOOL_SCHEMAS.clear()
    _TOOL_REGISTRY.clear()


def teardown_function():
    _TOOL_SCHEMAS.clear()
    _TOOL_REGISTRY.clear()
    agent_bridge.set_java_bridge(None)


def test_register_java_tools_pulls_schema_from_java():
    agent_bridge.set_java_bridge(FakeJavaHost(SCHEMAS))
    names = agent_bridge.register_java_tools()

    assert names == ["search_links", "add_tag_to_link"]
    schema = next(s for s in _TOOL_SCHEMAS if s["function"]["name"] == "search_links")
    assert schema["function"]["description"] == "搜索链接"
    assert schema["function"]["parameters"]["required"] == ["query"]


def test_tool_execution_forwards_to_java_invoke():
    host = FakeJavaHost(SCHEMAS)
    agent_bridge.set_java_bridge(host)
    agent_bridge.register_java_tools()

    runner = ToolRunner()
    result = runner.execute({
        "id": "call_1",
        "type": "function",
        "function": {
            "name": "search_links",
            "arguments": json.dumps({"query": "小红书"}),
        },
    })

    # args_json 用 ensure_ascii=False —— 中文原样传给 Java,不做 \uXXXX 转义
    assert host.invocations == [
        ("search_links", json.dumps({"query": "小红书"}, ensure_ascii=False))
    ]
    assert '"echo"' in result["content"]


def test_read_tool_passes_without_confirmation():
    agent_bridge.set_java_bridge(FakeJavaHost(SCHEMAS))
    assert agent_bridge.confirm_guard("search_links", {"query": "x"}) is None


def test_write_tool_approved_passes():
    agent_bridge.set_java_bridge(FakeJavaHost(SCHEMAS, confirm_result=""))
    assert agent_bridge.confirm_guard("add_tag_to_link", {"link_id": 1}) is None


def test_write_tool_rejected_returns_reason_for_llm():
    agent_bridge.set_java_bridge(FakeJavaHost(SCHEMAS, confirm_result="用户点了拒绝"))
    reason = agent_bridge.confirm_guard("add_tag_to_link", {"link_id": 1, "tag": "x"})

    assert reason is not None
    # 拒绝原因必须能回灌给 LLM —— 模型要知道被拒了,能换方案
    assert "用户点了拒绝" in reason


def test_rejected_write_tool_is_not_executed():
    """拒绝时工具不能被执行 —— 安全底线。"""
    host = FakeJavaHost(SCHEMAS, confirm_result="用户点了拒绝")
    agent_bridge.set_java_bridge(host)
    agent_bridge.register_java_tools()

    runner = ToolRunner()
    runner.guard = agent_bridge.confirm_guard
    result = runner.execute({
        "id": "call_2",
        "type": "function",
        "function": {
            "name": "add_tag_to_link",
            "arguments": json.dumps({"link_id": 1, "tag": "x"}),
        },
    })

    assert "用户拒绝" in result["content"]
    assert host.invocations == []  # invoke 没被调到 —— 工具确实没执行


def test_write_tool_decision_comes_from_java_not_python():
    """Python 侧没有自己的写工具清单 —— 一律问 Java 的 requires_confirm。"""
    # Java 说 "delete_everything" 也要确认(Python 从没见过这个工具)
    host = FakeJavaHost(SCHEMAS, confirm_result="确认一下", confirm_names={"delete_everything"})
    agent_bridge.set_java_bridge(host)

    # Java 判定要确认 → Python 必须去问
    agent_bridge.confirm_guard("delete_everything", {"x": 1})
    assert host.confirms == [("delete_everything", json.dumps({"x": 1}, ensure_ascii=False))]

    # Java 判定不要确认 → 直接放行
    host2 = FakeJavaHost(SCHEMAS, confirm_names=set())
    agent_bridge.set_java_bridge(host2)
    assert agent_bridge.confirm_guard("delete_everything", {"x": 1}) is None
    assert host2.confirms == []
