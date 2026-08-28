"""Status reporting: structured, timestamped events for observability.

Emits events instead of free-form prints so a caller can stream progress to a
UI, log aggregator, or CI job summary.
"""

from __future__ import annotations

import json
import sys
import time
from dataclasses import asdict, dataclass, field
from typing import Any, Callable


@dataclass
class StatusEvent:
    type: str
    message: str
    detail: dict[str, Any] = field(default_factory=dict)
    timestamp: float = field(default_factory=time.time)


Sink = Callable[[StatusEvent], None]


def stdout_sink(event: StatusEvent) -> None:
    print(json.dumps(asdict(event)), file=sys.stderr, flush=True)


class StatusReporter:
    def __init__(self, sink: Sink = stdout_sink):
        self._sink = sink

    def task_started(self, goal: str) -> None:
        self._emit("task_started", f"Task started: {goal}")

    def tool_call(self, name: str, arguments: dict) -> None:
        self._emit("tool_call", f"Calling {name}", {"name": name, "arguments": arguments})

    def tool_result(self, name: str, result: str) -> None:
        self._emit("tool_result", f"{name} completed", {"name": name, "result": result[:500]})

    def approval_required(self, name: str, reason: str) -> None:
        self._emit("approval_required", f"Approval required for {name}: {reason}")

    def approval_denied(self, name: str, reason: str) -> None:
        self._emit("approval_denied", f"Approval denied for {name}: {reason}")

    def task_completed(self, summary: str) -> None:
        self._emit("task_completed", summary)

    def task_failed(self, error: str) -> None:
        self._emit("task_failed", error)

    def _emit(self, event_type: str, message: str, detail: dict[str, Any] | None = None) -> None:
        self._sink(StatusEvent(type=event_type, message=message, detail=detail or {}))
