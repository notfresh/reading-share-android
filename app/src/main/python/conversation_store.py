"""conversation_store.py — 会话历史(落盘 + 按名搜索 + 自动起名)
==============================================================

之前会话只存在内存(`agent_bridge._SESSION_MESSAGES`),进程死了就没了。
本模块把它落到 app 私有目录,并负责:

  1. 存/读:  `<files_dir>/agent/conversations/<id>.json`
  2. 列表:  按更新时间倒序,支持**按会话名**做简单 contains 匹配
  3. 自动起名: 会话超过 3 轮(即第 4 轮起)且还没起过名字时,
     用**前 3 轮**内容让 LLM 起一个 ≤12 字的标题 —— **只执行一次**,
     起过名字后 `title_generated=true`,此后永不改名

每条会话的 JSON:
```json
{
  "id": "20261030_054500_ab12",
  "title": "新对话",
  "title_generated": false,
  "created_at": 1793..., 
  "updated_at": 1793...,
  "messages": [ ... LLM 原生消息流 ... ]
}
```

**为什么存 LLM 消息流而不是"显示消息"**: 切换会话要能继续聊,上下文必须完整
(含 tool_calls / role=tool)。界面上的气泡是**从消息流推导**出来的
(`display_messages`),不单独存一份 —— 单一来源。
"""

from __future__ import annotations

import json
import os
import re
import shutil
import time
import uuid
from typing import Optional

DEFAULT_TITLE = "新对话"

# 保留最近多少段会话(超了删最老的)
MAX_CONVERSATIONS = 50

# "超过三轮" = 第 4 轮起才起名;取前 3 轮内容做总结
AUTOTITLE_TRIGGER_ROUNDS = 4
AUTOTITLE_SOURCE_ROUNDS = 3

# 标题最多几个字
TITLE_MAX_CHARS = 12

_TITLE_PROMPT = (
    "你负责给一段对话起标题。要求:只输出标题本身,不超过 12 个字,"
    "不要标点、不要引号、不要任何前缀或解释。"
)


# ── 路径 ──────────────────────────────────────────────────────────────

def root_dir(bridge) -> str:
    """会话目录 —— 由 Java 侧提供 files_dir(),这里只负责拼子目录。"""
    base = str(bridge.files_dir())
    path = os.path.join(base, "agent", "conversations")
    os.makedirs(path, exist_ok=True)
    return path


def new_id() -> str:
    return time.strftime("%Y%m%d_%H%M%S") + "_" + uuid.uuid4().hex[:4]


def _path(root: str, conversation_id: str) -> str:
    return os.path.join(root, conversation_id + ".json")


# ── 读写 ──────────────────────────────────────────────────────────────

def _read(root: str, conversation_id: str) -> Optional[dict]:
    try:
        with open(_path(root, conversation_id), encoding="utf-8") as f:
            doc = json.load(f)
        return doc if isinstance(doc, dict) else None
    except (OSError, json.JSONDecodeError):
        return None


def _write(root: str, doc: dict) -> None:
    """原子写:先写 .tmp 再 replace,避免写一半断掉留下坏文件。"""
    target = _path(root, doc["id"])
    tmp = target + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(doc, f, ensure_ascii=False)
    os.replace(tmp, target)


def _prune(root: str) -> None:
    """只保留最近 MAX_CONVERSATIONS 段。"""
    docs = []
    for name in os.listdir(root):
        if not name.endswith(".json"):
            continue
        doc = _read(root, name[:-5])
        if doc:
            docs.append((doc.get("updated_at", 0), name))
    docs.sort(reverse=True)
    for _, name in docs[MAX_CONVERSATIONS:]:
        try:
            os.remove(os.path.join(root, name))
        except OSError:
            pass


# ── 显示消息(从 LLM 消息流推导)────────────────────────────────────────

def display_messages(messages: list) -> list:
    """把 LLM 消息流压成界面气泡列表。

    - role=user 且有内容        → {"role": "user", "text": ...}
    - role=assistant 有内容     → {"role": "agent", "text": ...}
    - role=assistant 带 tool_calls → {"role": "tool", "text": "工具名 × N"}
    - role=tool / role=system   → 跳过(工具结果由 agent 自己总结)
    """
    out = []
    for msg in messages or []:
        role = msg.get("role")
        if role == "user":
            text = str(msg.get("content") or "").strip()
            if text:
                out.append({"role": "user", "text": text})
        elif role == "assistant":
            calls = msg.get("tool_calls") or []
            if calls:
                names = []
                for call in calls:
                    fn = call.get("function") or {}
                    name = fn.get("name") or ""
                    if name:
                        names.append(name)
                if names:
                    out.append({"role": "tool", "text": "、".join(names)})
            text = str(msg.get("content") or "").strip()
            if text:
                out.append({"role": "agent", "text": text})
    return out


# ── 会话操作 ──────────────────────────────────────────────────────────

def create(bridge) -> dict:
    """新建一段空会话(标题 = 默认名)。"""
    root = root_dir(bridge)
    now = time.time()
    doc = {
        "id": new_id(),
        "title": DEFAULT_TITLE,
        "title_generated": False,
        "created_at": now,
        "updated_at": now,
        "messages": [],
    }
    _write(root, doc)
    return doc


def save_turn(bridge, conversation_id: str, messages: list) -> dict:
    """一轮结束后落盘。会话若不存在则就地创建。"""
    root = root_dir(bridge)
    doc = _read(root, conversation_id) or {
        "id": conversation_id,
        "title": DEFAULT_TITLE,
        "title_generated": False,
        "created_at": time.time(),
        "updated_at": time.time(),
        "messages": [],
    }
    doc["messages"] = list(messages or [])
    doc["updated_at"] = time.time()
    doc.setdefault("title", DEFAULT_TITLE)
    doc.setdefault("title_generated", False)
    _write(root, doc)
    _prune(root)
    return doc


def list_documents(bridge, query: str = "") -> list:
    """会话列表(按更新时间倒序),query 按标题做 contains(忽略大小写)。"""
    root = root_dir(bridge)
    needle = (query or "").strip().lower()
    items = []
    for name in os.listdir(root):
        if not name.endswith(".json"):
            continue
        doc = _read(root, name[:-5])
        if not doc:
            continue
        title = str(doc.get("title") or DEFAULT_TITLE)
        rounds = count_rounds(doc.get("messages") or [])
        if rounds == 0:
            # 空会话不进列表(懒创建后可能留下 0 轮文件)
            continue
        if needle and needle not in title.lower():
            continue
        items.append({
            "id": doc.get("id", name[:-5]),
            "title": title,
            "updated_at": doc.get("updated_at", 0),
            "rounds": rounds,
        })
    items.sort(key=lambda it: it["updated_at"], reverse=True)
    return items


def load(bridge, conversation_id: str) -> Optional[dict]:
    return _read(root_dir(bridge), conversation_id)


def count_rounds(messages: list) -> int:
    """轮数 = 用户消息条数。"""
    return sum(1 for m in (messages or []) if m.get("role") == "user")


# ── 自动起名(只执行一次)──────────────────────────────────────────────

def _first_rounds_text(messages: list, rounds: int) -> str:
    chunks = []
    seen = 0
    for msg in messages or []:
        role = msg.get("role")
        if role == "user":
            seen += 1
            if seen > rounds:
                break
            chunks.append("用户: " + str(msg.get("content") or "")[:300])
        elif role == "assistant":
            text = str(msg.get("content") or "").strip()
            if text:
                chunks.append("助手: " + text[:300])
    return "\n".join(chunks)


def _clean_title(raw: str) -> str:
    title = re.sub(r"[\s\"'“”‘’《》【】\[\]()（）]+", "", str(raw or ""))
    title = title.strip("。.,,:;、-—")
    return title[:TITLE_MAX_CHARS]


def maybe_autotitle(bridge, llm, conversation_id: str) -> Optional[str]:
    """满足条件就给会话起名,返回新标题;不满足/失败返回 None。

    条件(**同时**满足才执行,且只执行一次):
      1. `title_generated` 还是 false(没起过名)
      2. 轮数 > 3(即第 4 轮及以后)
    起过的会话永远不会再被改名。
    """
    root = root_dir(bridge)
    doc = _read(root, conversation_id)
    if not doc or doc.get("title_generated"):
        return None
    messages = doc.get("messages") or []
    if count_rounds(messages) < AUTOTITLE_TRIGGER_ROUNDS:
        return None

    text = _first_rounds_text(messages, AUTOTITLE_SOURCE_ROUNDS)
    if not text.strip():
        return None

    try:
        resp = llm.complete([
            {"role": "system", "content": _TITLE_PROMPT},
            {"role": "user", "content": text},
        ])
        raw = resp.choices[0].message.content or ""
    except Exception:
        # 起名失败不影响对话 —— 保持默认名,下一轮还会再试
        return None

    title = _clean_title(raw)
    if not title:
        return None

    doc["title"] = title
    doc["title_generated"] = True
    _write(root, doc)
    return title
