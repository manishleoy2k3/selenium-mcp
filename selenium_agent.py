"""Local AI agent for Selenium MCP and Page Object Model test generation."""

import json
import os
import subprocess
import sys
from typing import Any

from openai import OpenAI

DEFAULT_URL = "https://opensource-demo.orangehrmlive.com/web/index.php/auth/login"
DEFAULT_JAR = "target/selenium-mcp-0.1.0-jar-with-dependencies.jar"

SYSTEM_PROMPT = f"""You are a senior QA automation engineer.

Use the Selenium MCP tools to inspect and automate the current web application. The
browser is already open at {DEFAULT_URL}. Never navigate away from the requested
application unless the user explicitly asks you to.

For every requested test:
- First reason about the user flow, then use MCP tools to inspect the live DOM.
- Prefer stable id, name, data-* or accessible selectors; do not guess selectors when
  the DOM can be inspected.
- Generate Java Selenium code using the Page Object Model (POM): page classes own
  locators and page actions, tests own scenarios and assertions, and configuration
  owns the base URL and browser settings.
- Use explicit waits, avoid Thread.sleep, keep credentials supplied by environment
  variables or placeholders, and never print secrets.
- Execute the flow through MCP tools before claiming it passes.
- Finish with the POM source files, executed steps, observed result, and any evidence
  such as a screenshot path. Clearly distinguish generated code from observed facts.

The initial automation URL is {DEFAULT_URL}.
"""


class SeleniumMcp:
    def __init__(self, jar_path: str) -> None:
        self.process = subprocess.Popen(
            ["java", "-jar", jar_path],
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=None,
            text=True,
            bufsize=1,
        )
        if self.process.stdout is None:
            raise RuntimeError("Could not open MCP server output")
        handshake = self.process.stdout.readline()
        if not handshake:
            raise RuntimeError("MCP server exited before sending its tool list")
        info = json.loads(handshake)
        self.tools = info.get("tools", [])

    def call(self, name: str, params: dict[str, Any]) -> list[dict[str, Any]]:
        if self.process.stdin is None or self.process.stdout is None:
            raise RuntimeError("MCP server streams are unavailable")
        request = {
            "type": "tool_call",
            "tool_call_id": f"agent-{name}",
            "name": name,
            "params": params,
        }
        self.process.stdin.write(json.dumps(request) + "\n")
        self.process.stdin.flush()
        response = self.process.stdout.readline()
        if not response:
            raise RuntimeError("MCP server closed its output")
        return json.loads(response).get("content", [])

    def close(self) -> None:
        try:
            self.call("close_session", {})
        except Exception:
            pass
        if self.process.poll() is None:
            self.process.terminate()


def tool_definitions(mcp: SeleniumMcp) -> list[dict[str, Any]]:
    return [
        {
            "type": "function",
            "function": {
                "name": tool["name"],
                "description": tool.get("description", "Selenium browser operation"),
                "parameters": tool.get("parameters", {"type": "object"}),
            },
        }
        for tool in mcp.tools
    ]


def bootstrap(mcp: SeleniumMcp) -> None:
    mcp.call("start_browser", {"browser": "chrome", "options": {"headless": False}})
    mcp.call("navigate", {"url": DEFAULT_URL})


def run_agent(mcp: SeleniumMcp, client: OpenAI, request: str) -> None:
    messages: list[dict[str, Any]] = [
        {"role": "system", "content": SYSTEM_PROMPT},
        {"role": "user", "content": request},
    ]
    tools = tool_definitions(mcp)
    while True:
        completion = client.chat.completions.create(
            model=os.environ["OPENAI_MODEL"],
            messages=messages,
            tools=tools,
            tool_choice="auto",
        )
        message = completion.choices[0].message
        assistant_message: dict[str, Any] = {"role": "assistant", "content": message.content or ""}
        if message.tool_calls:
            assistant_message["tool_calls"] = [
                {
                    "id": call.id,
                    "type": "function",
                    "function": {"name": call.function.name, "arguments": call.function.arguments},
                }
                for call in message.tool_calls
            ]
        messages.append(assistant_message)
        if not message.tool_calls:
            print(message.content or "")
            return
        for call in message.tool_calls:
            try:
                result = mcp.call(call.function.name, json.loads(call.function.arguments or "{}"))
            except Exception as exc:
                result = [{"type": "text", "text": f"Tool error: {exc}"}]
            messages.append(
                {
                    "role": "tool",
                    "tool_call_id": call.id,
                    "content": json.dumps(result),
                }
            )


def main() -> None:
    jar_path = os.environ.get("SELENIUM_MCP_JAR", DEFAULT_JAR)
    request = " ".join(sys.argv[1:]).strip() or (
        "Explore the OrangeHRM login page and create and execute a POM test that "
        "checks the login form is visible. Use a safe placeholder for credentials."
    )
    required = ["OPENAI_API_KEY", "OPENAI_BASE_URL", "OPENAI_MODEL"]
    missing = [name for name in required if not os.environ.get(name)]
    if missing:
        raise SystemExit("Missing environment variables: " + ", ".join(missing))
    if not os.path.exists(jar_path):
        raise SystemExit(f"MCP JAR not found: {jar_path}. Run: mvn clean package")

    mcp = SeleniumMcp(jar_path)
    try:
        bootstrap(mcp)
        run_agent(mcp, OpenAI(api_key=os.environ["OPENAI_API_KEY"], base_url=os.environ["OPENAI_BASE_URL"]), request)
    finally:
        mcp.close()


if __name__ == "__main__":
    main()
