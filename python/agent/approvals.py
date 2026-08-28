"""Approval gate: decides which tool calls require human sign-off before running.

Sensitive operations (running arbitrary JS, uploading local files, navigating to
a fresh domain, closing a session) go through an approval callback. In a CLI the
callback can prompt on stdin; in a service it can post to a queue/ticket system
and block on the response.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Awaitable, Callable
from urllib.parse import urlparse

ApprovalCallback = Callable[["ApprovalRequest"], Awaitable[bool]]

# Tools that always require approval regardless of arguments.
ALWAYS_APPROVE = {"execute_script", "upload_file", "close_browser"}


@dataclass
class ApprovalRequest:
    tool_name: str
    arguments: dict
    reason: str


class ApprovalDenied(RuntimeError):
    pass


class ApprovalGate:
    def __init__(self, allowed_domains: list[str] | None = None, on_request: ApprovalCallback | None = None):
        self.allowed_domains = allowed_domains or []
        self._on_request = on_request or self._auto_deny

    async def check(self, tool_name: str, arguments: dict) -> None:
        reason = self._reason_for_approval(tool_name, arguments)
        if reason is None:
            return

        request = ApprovalRequest(tool_name=tool_name, arguments=arguments, reason=reason)
        approved = await self._on_request(request)
        if not approved:
            raise ApprovalDenied(f"Approval denied for {tool_name}: {reason}")

    def _reason_for_approval(self, tool_name: str, arguments: dict) -> str | None:
        if tool_name in ALWAYS_APPROVE:
            return f"'{tool_name}' is a sensitive operation"

        if tool_name == "navigate" and self.allowed_domains:
            host = urlparse(arguments.get("url", "")).hostname or ""
            if not any(host == d or host.endswith(f".{d}") for d in self.allowed_domains):
                return f"navigation target '{host}' is outside the allowed domain list"

        return None

    @staticmethod
    async def _auto_deny(_: ApprovalRequest) -> bool:
        return False
