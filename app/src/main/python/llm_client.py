#!/usr/bin/env python3
"""
llm_client.py — 模块 2/5：模型客户端（LLMClient）
====================================================

Hermes 对应: agent/chat_completion_helpers.py 的 _interruptible_api_call()
             + run_agent.py 的 client 管理 + agent/transports/*（provider 适配）

职责：循环的唯一"发请求"出口。调用方永远看不到重试/退避/错误分类——
      要么拿到一个干净响应，要么拿到分类好的错误。

教学要点：
  这是 5000 行里最大的块（~1500 行都是重试/凭证/错误处理）。
  拆出来的意义：骨架循环调 LLMClient.complete() 只有一行，
  重试逻辑全在这一个类里，可以单独测试（喂一个假 client 就能测）。
"""

from __future__ import annotations

import time
from typing import Any, Optional

import openai


class LLMError(Exception):
    """分类后的 LLM 错误。message 里带面向用户的提示。"""

    def __init__(self, category: str, message: str, retryable: bool = False):
        super().__init__(message)
        self.category = category      # timeout / rate_limit / auth / server / invalid / unknown
        self.retryable = retryable    # 是否值得重试


class LLMClient:
    """OpenAI 兼容 API 客户端，内置重试 + 退避 + 错误分类。

    Hermes 对应: 真实实现还有凭证轮换、provider fallback、流式、
    上下文压缩触发、预算扣减——教学版只做最核心的"重试 + 分类"。
    """

    def __init__(
        self,
        model: str = "deepseek-chat",
        base_url: str = "https://api.deepseek.com/v1",
        api_key: str = "",
        max_retries: int = 3,
        retry_backoff: float = 1.0,   # 退避基数（秒），指数增长
        timeout: float = 60.0,
        verbose: bool = False,
    ):
        self.model = model
        self.base_url = base_url
        self.api_key = api_key
        self.max_retries = max_retries
        self.retry_backoff = retry_backoff
        self.timeout = timeout
        self.verbose = verbose

        self._client = openai.OpenAI(
            base_url=base_url,
            api_key=api_key,
            timeout=timeout,
            max_retries=0,  # 关掉 SDK 自带重试，由我们自己控制
        )

        # 统计（教学用：看重试了几次）
        self.stats = {"calls": 0, "retries": 0, "last_latency_ms": 0}

    # ── 对外唯一接口 ────────────────────────────────────────────────────

    def complete(self, messages: list[dict], tools: Optional[list[dict]] = None) -> Any:
        """发一次非流式补全请求，带重试 + 错误分类。

        Returns:
            成功 → response 对象（.choices[0].message 可直接用）
            失败 → 抛 LLMError（已分类）

        Hermes 对应: _interruptible_api_call() 成功后返回 response，
        失败后由外层 retry 循环处理——教学版把 retry 收进了这个类。
        """
        attempt = 0
        while True:
            attempt += 1
            self.stats["calls"] += 1
            try:
                t0 = time.time()
                resp = self._client.chat.completions.create(
                    model=self.model,
                    messages=_strip_origin(messages),
                    tools=tools if tools else None,
                    temperature=0.7,
                )
                self.stats["last_latency_ms"] = int((time.time() - t0) * 1000)
                return resp
            except openai.RateLimitError as e:
                self._maybe_retry("rate_limit", e, attempt, wait_extra=2.0)
            except openai.APITimeoutError as e:
                self._maybe_retry("timeout", e, attempt)
            except openai.APIConnectionError as e:
                self._maybe_retry("connection", e, attempt)
            except openai.AuthenticationError as e:
                raise LLMError("auth", f"API key 无效或被拒：{e}", retryable=False)
            except openai.BadRequestError as e:
                raise LLMError("bad_request", f"请求参数错误（可能是上下文超长）：{e}", retryable=False)
            except openai.InternalServerError as e:
                self._maybe_retry("server", e, attempt)
            except Exception as e:
                # 未知错误：给一次重试机会，再不行就抛
                self._maybe_retry("unknown", e, attempt)

    # ── 内部：重试决策 ──────────────────────────────────────────────────

    def _maybe_retry(self, category: str, exc: Exception, attempt: int, wait_extra: float = 0.0) -> None:
        """决定是否重试。重试则等待退避后 return（继续 while）；否则抛 LLMError。

        注意：这个方法永远不"返回"——要么等待后回到循环，要么抛异常。
        """
        if attempt >= self.max_retries:
            raise LLMError(
                category,
                f"API 调用失败（{category}），已重试 {self.max_retries} 次：{exc}",
                retryable=False,
            )
        wait = self.retry_backoff * (2 ** (attempt - 1)) + wait_extra
        self.stats["retries"] += 1
        if self.verbose:
            print(f"  ⏳ {category}，{wait:.1f}s 后重试（{attempt}/{self.max_retries}）...")
        time.sleep(wait)
        # 不 return —— 继续 while 循环


# ── 辅助：normalize 助手消息 ────────────────────────────────────────────────

def normalize_assistant_message(msg: Any) -> dict:
    """把 ChatCompletionMessage 对象转成纯 dict（含 tool_calls 规范化）。

    Hermes 对应: transport.normalize_response() — provider 返回格式转统一格式。
    """
    entry: dict = {"role": "assistant", "content": msg.content}
    if msg.tool_calls:
        entry["tool_calls"] = [
            {
                "id": tc.id,
                "type": "function",
                "function": {
                    "name": tc.function.name,
                    "arguments": tc.function.arguments,
                },
            }
            for tc in msg.tool_calls
        ]
    return entry


def _strip_origin(messages: list[dict]) -> list[dict]:
    """发给 API 前去掉内部标记字段（origin），只留 OpenAI 认识的字段。

    技能框架挂载后，注入的 system 消息带 {"origin": {"kind": "injection", ...}}
    标记用于会话恢复检测——但 API 不认这个字段，发送前必须剥离
    （对应生产环境：序列化/传输层的字段白名单过滤）。
    """
    cleaned = []
    for m in messages:
        if "origin" in m:
            m = {k: v for k, v in m.items() if k != "origin"}
        cleaned.append(m)
    return cleaned
