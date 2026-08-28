"""Python MCP server exposing the same controlled Selenium tools as the Java
implementation (SeleniumMcpServer.java), built on the official `mcp` SDK.

Run with: python -m python.selenium_mcp_server
"""

from __future__ import annotations

from typing import Optional

from mcp.server.fastmcp import FastMCP

from python.browser_session import BrowserSession

mcp = FastMCP("selenium-mcp-python")

# One session per server process, mirroring the Java server's single static
# BROWSER instance. Swap for a session-keyed dict if you need multi-tenant use.
_BROWSER = BrowserSession()

_STRATEGY_ENUM = ["id", "name", "css", "xpath", "class", "tag", "linkText", "partialLinkText"]


@mcp.tool(description="Starts a Chrome or Firefox Selenium session.")
def start_browser(browser: str, headless: bool = False) -> str:
    return _BROWSER.start_browser(browser, headless)


@mcp.tool(description="Navigates the active browser to a URL.")
def navigate(url: str) -> str:
    return _BROWSER.navigate(url)


@mcp.tool(description="Returns the active page title and URL.")
def get_page_information() -> str:
    return _BROWSER.get_page_information()


@mcp.tool(description="Clicks a visible and clickable page element.")
def click_element(strategy: str, value: str, timeoutSeconds: int = 15) -> str:
    return _BROWSER.click(strategy, value, timeoutSeconds)


@mcp.tool(description="Types text into a visible form element.")
def type_text(
    strategy: str,
    value: str,
    text: str,
    clearFirst: bool = True,
    timeoutSeconds: int = 15,
) -> str:
    return _BROWSER.type_text(strategy, value, text, clearFirst, timeoutSeconds)


@mcp.tool(description="Returns visible text from a page element.")
def get_element_text(strategy: str, value: str, timeoutSeconds: int = 15) -> str:
    return _BROWSER.get_text(strategy, value, timeoutSeconds)


@mcp.tool(description="Returns an attribute from a page element.")
def get_element_attribute(strategy: str, value: str, attribute: str, timeoutSeconds: int = 15) -> str:
    return _BROWSER.get_attribute(strategy, value, attribute, timeoutSeconds)


@mcp.tool(description="Finds the first matching page element and returns a compact summary. Fails if no element matches.")
def find_element(strategy: str, value: str, timeoutSeconds: int = 15) -> str:
    return _BROWSER.find_element(strategy, value, timeoutSeconds)


@mcp.tool(description="Finds matching page elements and returns a compact summary.")
def find_elements(strategy: str, value: str, timeoutSeconds: int = 15) -> str:
    return _BROWSER.find_elements(strategy, value, timeoutSeconds)


@mcp.tool(description="Returns true when a visible matching element exists.")
def element_exists(strategy: str, value: str, timeoutSeconds: int = 5) -> str:
    return _BROWSER.element_exists(strategy, value, timeoutSeconds)


@mcp.tool(description="Waits for an element to become present, visible, or clickable.")
def wait_for_element(strategy: str, value: str, state: str = "visible", timeoutSeconds: int = 15) -> str:
    return _BROWSER.wait_for_element(strategy, value, state, timeoutSeconds)


@mcp.tool(description="Selects an option from a select element.")
def select_option(strategy: str, value: str, selectBy: str, option: str, timeoutSeconds: int = 15) -> str:
    return _BROWSER.select_option(strategy, value, selectBy, option, timeoutSeconds)


@mcp.tool(description="Switches into a frame by locator, name/id, or index.")
def switch_to_frame(
    strategy: Optional[str] = None,
    value: Optional[str] = None,
    nameOrId: Optional[str] = None,
    index: Optional[int] = None,
    timeoutSeconds: int = 15,
) -> str:
    return _BROWSER.switch_to_frame(strategy, value, nameOrId, index, timeoutSeconds)


@mcp.tool(description="Switches back to the top-level document.")
def switch_to_default_content() -> str:
    return _BROWSER.switch_to_default_content()


@mcp.tool(description="Returns open browser window handles.")
def get_window_handles() -> str:
    return _BROWSER.get_window_handles()


@mcp.tool(description="Switches to a browser window by handle.")
def switch_to_window(handle: str) -> str:
    return _BROWSER.switch_to_window(handle)


@mcp.tool(description="Executes browser JavaScript in the active page only. Does not execute Python or shell commands.")
def execute_script(
    script: str,
    strategy: Optional[str] = None,
    value: Optional[str] = None,
    timeoutSeconds: int = 15,
) -> str:
    return _BROWSER.execute_script(script, strategy, value, timeoutSeconds)


@mcp.tool(description="Scrolls a visible element into view.")
def scroll_to_element(strategy: str, value: str, timeoutSeconds: int = 15) -> str:
    return _BROWSER.scroll_to_element(strategy, value, timeoutSeconds)


@mcp.tool(description="Moves the pointer over a visible element.")
def hover_element(strategy: str, value: str, timeoutSeconds: int = 15) -> str:
    return _BROWSER.hover_element(strategy, value, timeoutSeconds)


@mcp.tool(description="Presses a supported keyboard key globally or on a target element.")
def press_key(
    key: str,
    strategy: Optional[str] = None,
    value: Optional[str] = None,
    timeoutSeconds: int = 15,
) -> str:
    return _BROWSER.press_key(key, strategy, value, timeoutSeconds)


@mcp.tool(description="Uploads a local file through an input[type=file] element.")
def upload_file(strategy: str, value: str, filePath: str, timeoutSeconds: int = 15) -> str:
    return _BROWSER.upload_file(strategy, value, filePath, timeoutSeconds)


@mcp.tool(description="Returns a base64-encoded PNG screenshot.")
def take_screenshot() -> str:
    return _BROWSER.take_screenshot_base64()


@mcp.tool(description="Returns a compact accessibility-oriented DOM snapshot.")
def get_accessibility_snapshot() -> str:
    return _BROWSER.get_accessibility_snapshot()


@mcp.tool(description="Closes the active Selenium browser session.")
def close_browser() -> str:
    return _BROWSER.close_browser()


def main() -> None:
    try:
        mcp.run(transport="stdio")
    finally:
        _BROWSER.close_browser()


if __name__ == "__main__":
    main()
