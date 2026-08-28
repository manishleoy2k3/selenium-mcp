"""Orchestrator: drives an LLM tool-calling loop against the Selenium MCP server,
enforcing approvals and emitting status events for every step.

This talks to the MCP server through the official `mcp` client SDK (stdio),
unlike the legacy `selenium_agent.py` at the repo root, which speaks a
custom line-delimited protocol that the real MCP servers (Java or this Python
one) do not implement. Prefer this orchestrator or update selenium_agent.py to
use the same `mcp` client before relying on it against either server.
"""

from __future__ import annotations

import json
import os
from contextlib import AsyncExitStack
from typing import Any

from mcp import ClientSession, StdioServerParameters
from mcp.client.stdio import stdio_client
from openai import AsyncOpenAI

from python.agent.approvals import ApprovalDenied, ApprovalGate
from python.agent.intake import IntakeError, Task, parse_request
from python.agent.status import StatusReporter

SYSTEM_PROMPT = """You are a senior QA automation engineer. Use the available
Selenium tools to inspect and automate the requested web application. Prefer
stable id/name/data-* or accessible selectors; inspect the live DOM instead of
guessing. Use explicit waits, never Thread.sleep-style polling loops, and never
print secrets. Report exactly what you observed versus what you assumed."""


class Orchestrator:
    def __init__(
        self,
        server_command: list[str],
        openai_client: AsyncOpenAI,
        model: str,
        approval_gate: ApprovalGate,
        reporter: StatusReporter,
    ) -> None:
        self._server_command = server_command
        self._openai = openai_client
        self._model = model
        self._approvals = approval_gate
        self._status = reporter
        self._session: ClientSession | None = None

    async def run(self, raw_request: str, default_url: str, allowed_domains: list[str] | None = None) -> str:
        try:
            task = parse_request(raw_request, default_url, allowed_domains)
        except IntakeError as exc:
            self._status.task_failed(str(exc))
            raise

        self._status.task_started(task.goal)

        async with AsyncExitStack() as stack:
            read, write = await stack.enter_async_context(
                stdio_client(StdioServerParameters(command=self._server_command[0], args=self._server_command[1:]))
            )
            session = await stack.enter_async_context(ClientSession(read, write))
            await session.initialize()
            self._session = session

            try:
                await self._bootstrap(task)
                summary = await self._run_agent_loop(task)
                self._status.task_completed(summary)
                return summary
            except Exception as exc:
                self._status.task_failed(str(exc))
                raise
            finally:
                await self._call_tool("close_browser", {})

    async def _bootstrap(self, task: Task) -> None:
        await self._call_tool("start_browser", {"browser": task.browser, "headless": task.headless})
        await self._call_tool("navigate", {"url": task.target_url})

    async def _run_agent_loop(self, task: Task) -> str:
        assert self._session is not None
        tools_response = await self._session.list_tools()
        tool_schemas = [_to_openai_tool_schema(tool) for tool in tools_response.tools]

        messages: list[dict[str, Any]] = [
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": task.goal},
        ]

        while True:
            completion = await self._openai.chat.completions.create(
                model=self._model,
                messages=messages,
                tools=tool_schemas,
                tool_choice="auto",
            )
            message = completion.choices[0].message
            messages.append(_assistant_message(message))

            if not message.tool_calls:
                return message.content or ""

            for call in message.tool_calls:
                arguments = json.loads(call.function.arguments or "{}")
                try:
                    result = await self._call_tool(call.function.name, arguments)
                except ApprovalDenied as exc:
                    result = f"Blocked: {exc}"
                except Exception as exc:  # tool execution failures go back to the model
                    result = f"Tool error: {exc}"

                messages.append({"role": "tool", "tool_call_id": call.id, "content": result})

    async def _call_tool(self, name: str, arguments: dict) -> str:
        assert self._session is not None
        self._status.tool_call(name, arguments)

        try:
            await self._approvals.check(name, arguments)
        except ApprovalDenied as exc:
            self._status.approval_denied(name, str(exc))
            raise

        result = await self._session.call_tool(name, arguments)
        text = "\n".join(part.text for part in result.content if hasattr(part, "text"))
        self._status.tool_result(name, text)
        return text


def _to_openai_tool_schema(tool) -> dict[str, Any]:
    return {
        "type": "function",
        "function": {
            "name": tool.name,
            "description": tool.description or "Selenium browser operation",
            "parameters": tool.inputSchema or {"type": "object"},
        },
    }


def _assistant_message(message) -> dict[str, Any]:
    result: dict[str, Any] = {"role": "assistant", "content": message.content or ""}
    if message.tool_calls:
        result["tool_calls"] = [
            {
                "id": call.id,
                "type": "function",
                "function": {"name": call.function.name, "arguments": call.function.arguments},
            }
            for call in message.tool_calls
        ]
    return result


async def build_default_orchestrator(reporter: StatusReporter | None = None) -> Orchestrator:
    client = AsyncOpenAI(api_key=os.environ["OPENAI_API_KEY"], base_url=os.environ.get("OPENAI_BASE_URL"))
    approval_gate = ApprovalGate(allowed_domains=[])
    return Orchestrator(
        server_command=["python", "-m", "python.selenium_mcp_server"],
        openai_client=client,
        model=os.environ["OPENAI_MODEL"],
        approval_gate=approval_gate,
        reporter=reporter or StatusReporter(),
    )
