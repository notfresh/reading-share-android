#!/usr/bin/env python3
"""
loop_controller.py — 模块 1/5：循环控制器（LoopController）
=============================================================

Hermes 对应: agent/iteration_budget.py + conversation_loop.py 的
            while 条件检查（max_iterations / 预算 / 中断 / grace call）

职责：回答循环里唯一的问题——"还继续吗？"。
  - 回合数上限（max_iterations）
  - 迭代预算（每轮扣一分的限额制）
  - 中断请求（用户中途打断）
  - grace call（预算耗尽后的最后一次机会）

教学要点：
  这是把 5000 行循环里的"退出条件"全部收敛到一个类。
  骨架循环不再自己写 if 判断，而是问 controller.should_continue()。
  Hermes 的 #77305 issue（API 失败也扣预算饿死 fallback 链）就是
  这类逻辑的边界案例——预算的"扣减时机"本身就是一门学问。
"""

from __future__ import annotations

import threading
from typing import Optional


class LoopController:
    """循环终止条件的唯一裁决者。"""

    def __init__(
        self,
        max_turns: int = 10,
        max_budget: Optional[int] = None,
        verbose: bool = False,
    ):
        """
        Args:
            max_turns: 最多几轮"思考→调工具"循环（Hermes: max_iterations）
            max_budget: 迭代预算总额。None = 与 max_turns 相同。
                        Hermes: iteration_budget — 可被工具调用额外消耗。
            verbose: 打印退出原因
        """
        self.max_turns = max_turns
        self.max_budget = max_budget if max_budget is not None else max_turns
        self.verbose = verbose

        self._turn_count = 0
        self._budget_used = 0
        self._grace_used = False
        self._interrupt_requested = False
        self._exit_reason: Optional[str] = None

        self._lock = threading.Lock()  # 中断可能来自其他线程（如 UI）

    # ── 查询：还继续吗？ ────────────────────────────────────────────────

    def should_continue(self) -> bool:
        """骨架循环每轮开头只问这一个问题。"""
        with self._lock:
            if self._interrupt_requested:
                self._exit_reason = "interrupted_by_user"
                return False
            if self._turn_count >= self.max_turns:
                self._exit_reason = "max_turns_reached"
                return False
            if self._budget_used >= self.max_budget and not self._grace_used:
                # 预算耗尽但还没用 grace → 给最后一次机会
                self._grace_used = True
                return True
            if self._budget_used >= self.max_budget:
                self._exit_reason = "budget_exhausted"
                return False
            return True

    def exit_reason(self) -> Optional[str]:
        return self._exit_reason

    # ── 每轮记账 ────────────────────────────────────────────────────────

    def record_turn(self) -> None:
        """每发起一次 LLM 调用记一轮。

        Hermes 对应: api_call_count += 1（conversation_loop.py:727）
        """
        with self._lock:
            self._turn_count += 1

    def consume_budget(self) -> bool:
        """扣减一分预算。返回 False 表示预算已耗尽。

        Hermes 对应: iteration_budget.consume()
        注意：Hermes 里某些工具调用失败也会扣预算（#77305 的争议点）。
        """
        with self._lock:
            if self._budget_used >= self.max_budget:
                return False
            self._budget_used += 1
            return True

    def budget_remaining(self) -> int:
        with self._lock:
            return max(0, self.max_budget - self._budget_used)

    # ── 中断 ────────────────────────────────────────────────────────────

    def request_interrupt(self, reason: str = "user_interrupt") -> None:
        """请求中断。骨架循环会在下一轮开头发现并退出。

        Hermes 对应: agent.interrupt() — 设置 per-thread interrupt flag，
        循环顶部检查（conversation_loop.py:720）。
        """
        with self._lock:
            self._interrupt_requested = True
            self._exit_reason = reason

    def reset(self) -> None:
        """回合结束后的重置（供复用同一实例跑多轮）。"""
        with self._lock:
            self._turn_count = 0
            self._budget_used = 0
            self._grace_used = False
            self._interrupt_requested = False
            self._exit_reason = None
