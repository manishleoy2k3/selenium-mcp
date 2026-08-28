"""Reusable Selenium automation engine (Python port of BrowserSession.java).

This module owns all direct WebDriver interaction: session lifecycle, locators,
waits, element actions, and page inspection. It has no knowledge of MCP or of
any agent orchestration layer, so it can be reused by any tool/API surface.
"""

from __future__ import annotations

import base64
import json
import threading
from dataclasses import dataclass
from pathlib import Path
from typing import Optional

from selenium import webdriver
from selenium.common.exceptions import TimeoutException
from selenium.webdriver.common.action_chains import ActionChains
from selenium.webdriver.common.by import By
from selenium.webdriver.common.keys import Keys
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.support.select import Select
from selenium.webdriver.support.ui import WebDriverWait

DEFAULT_TIMEOUT_SECONDS = 15
MAX_SCRIPT_LENGTH = 4000

_LOCATOR_STRATEGIES = {
    "id": By.ID,
    "name": By.NAME,
    "css": By.CSS_SELECTOR,
    "cssselector": By.CSS_SELECTOR,
    "xpath": By.XPATH,
    "class": By.CLASS_NAME,
    "classname": By.CLASS_NAME,
    "tag": By.TAG_NAME,
    "tagname": By.TAG_NAME,
    "linktext": By.LINK_TEXT,
    "partiallinktext": By.PARTIAL_LINK_TEXT,
}

_KEYS = {
    "ENTER": Keys.ENTER,
    "TAB": Keys.TAB,
    "ESCAPE": Keys.ESCAPE,
    "SPACE": Keys.SPACE,
    "BACKSPACE": Keys.BACK_SPACE,
    "DELETE": Keys.DELETE,
    "ARROW_UP": Keys.ARROW_UP,
    "ARROW_DOWN": Keys.ARROW_DOWN,
    "ARROW_LEFT": Keys.ARROW_LEFT,
    "ARROW_RIGHT": Keys.ARROW_RIGHT,
    "HOME": Keys.HOME,
    "END": Keys.END,
    "PAGE_UP": Keys.PAGE_UP,
    "PAGE_DOWN": Keys.PAGE_DOWN,
}

_ACCESSIBILITY_SNAPSHOT_SCRIPT = """
return JSON.stringify((() => {
  const visible = element => {
    const style = window.getComputedStyle(element);
    const rect = element.getBoundingClientRect();
    return style.visibility !== 'hidden'
      && style.display !== 'none'
      && rect.width > 0
      && rect.height > 0;
  };
  const nameOf = element =>
    element.getAttribute('aria-label')
    || element.getAttribute('alt')
    || element.getAttribute('title')
    || element.innerText
    || element.value
    || '';
  const roleOf = element =>
    element.getAttribute('role')
    || ({
      A: 'link', BUTTON: 'button', INPUT: element.type || 'input',
      SELECT: 'select', TEXTAREA: 'textbox', IMG: 'image'
    })[element.tagName]
    || '';
  const selector =
    'a,button,input,select,textarea,img,[role],'
    + '[aria-label],[aria-labelledby],h1,h2,h3,h4,h5,h6';
  const nodes = Array.from(document.body.querySelectorAll(selector))
    .filter(visible)
    .slice(0, 200)
    .map((element, index) => ({
      index,
      tag: element.tagName.toLowerCase(),
      role: roleOf(element),
      name: nameOf(element).replace(/\\s+/g, ' ').trim().slice(0, 160),
      disabled: element.disabled === true,
      href: element.href || null
    }));
  return { title: document.title, url: location.href, count: nodes.length, nodes };
})(), null, 2);
"""


def _trim(text: Optional[str], limit: int) -> str:
    if not text:
        return ""
    return text if len(text) <= limit else text[:limit] + "..."


@dataclass
class BrowserSession:
    """Owns a single WebDriver instance. Not thread-safe across concurrent
    sessions by design -- one session per agent/user, guarded by a lock."""

    driver: Optional[webdriver.Remote] = None

    def __post_init__(self) -> None:
        self._lock = threading.RLock()

    # -- lifecycle ---------------------------------------------------------

    def start_browser(self, browser: str, headless: bool = False) -> str:
        with self._lock:
            self.close_browser()

            selected = (browser or "chrome").lower()

            if selected == "chrome":
                options = webdriver.ChromeOptions()
                if headless:
                    options.add_argument("--headless=new")
                options.add_argument("--disable-notifications")
                options.add_argument("--disable-popup-blocking")
                options.add_argument("--start-maximized")
                self.driver = webdriver.Chrome(options=options)
            elif selected == "firefox":
                options = webdriver.FirefoxOptions()
                if headless:
                    options.add_argument("-headless")
                self.driver = webdriver.Firefox(options=options)
            else:
                raise ValueError(f"Unsupported browser: {selected}")

            self.driver.set_page_load_timeout(60)
            return f"{selected} browser started successfully"

    def close_browser(self) -> str:
        with self._lock:
            if self.driver is not None:
                try:
                    self.driver.quit()
                finally:
                    self.driver = None
                return "Browser closed"
            return "No browser session was active"

    # -- navigation ----------------------------------------------------------

    def navigate(self, url: str) -> str:
        driver = self._require_driver()
        if not url:
            raise ValueError("url is required")
        driver.get(url)
        return f"Navigated to {url}. Title: {driver.title}. Current URL: {driver.current_url}"

    def get_page_information(self) -> str:
        driver = self._require_driver()
        return f"Title: {driver.title}\nURL: {driver.current_url}"

    # -- element actions -----------------------------------------------------

    def click(self, strategy: str, value: str, timeout_seconds: int = DEFAULT_TIMEOUT_SECONDS) -> str:
        element = self._wait_for_clickable(self._locator(strategy, value), timeout_seconds)
        element.click()
        return f"Clicked element using {strategy}={value}"

    def type_text(
        self,
        strategy: str,
        value: str,
        text: str,
        clear_first: bool = True,
        timeout_seconds: int = DEFAULT_TIMEOUT_SECONDS,
    ) -> str:
        element = self._wait_for_visible(self._locator(strategy, value), timeout_seconds)
        if clear_first:
            element.clear()
        element.send_keys(text)
        return f"Entered text into element using {strategy}={value}"

    def get_text(self, strategy: str, value: str, timeout_seconds: int = DEFAULT_TIMEOUT_SECONDS) -> str:
        element = self._wait_for_visible(self._locator(strategy, value), timeout_seconds)
        return element.text

    def get_attribute(
        self, strategy: str, value: str, attribute: str, timeout_seconds: int = DEFAULT_TIMEOUT_SECONDS
    ) -> str:
        element = self._wait_for_visible(self._locator(strategy, value), timeout_seconds)
        return element.get_attribute(attribute) or ""

    def find_element(self, strategy: str, value: str, timeout_seconds: int = DEFAULT_TIMEOUT_SECONDS) -> str:
        driver = self._require_driver()
        by = self._locator(strategy, value)
        element = WebDriverWait(driver, timeout_seconds or DEFAULT_TIMEOUT_SECONDS).until(
            EC.presence_of_element_located(by)
        )
        return self._summarize_element(element)

    def find_elements(self, strategy: str, value: str, timeout_seconds: int = DEFAULT_TIMEOUT_SECONDS) -> str:
        driver = self._require_driver()
        by = self._locator(strategy, value)
        try:
            WebDriverWait(driver, timeout_seconds or DEFAULT_TIMEOUT_SECONDS).until(
                lambda d: len(d.find_elements(*by)) > 0
            )
        except TimeoutException:
            return f"Found 0 elements using {strategy}={value}"

        elements = driver.find_elements(*by)
        lines = [f"Found {len(elements)} elements using {strategy}={value}"]
        for index, element in enumerate(elements):
            lines.append(
                f"{index}: tag={element.tag_name}, displayed={element.is_displayed()}, "
                f"enabled={element.is_enabled()}, text={_trim(element.text, 120)}"
            )
        return "\n".join(lines)

    def element_exists(self, strategy: str, value: str, timeout_seconds: int = 5) -> str:
        try:
            self._wait_for_visible(self._locator(strategy, value), timeout_seconds)
            return "true"
        except TimeoutException:
            return "false"

    def wait_for_element(
        self,
        strategy: str,
        value: str,
        state: str = "visible",
        timeout_seconds: int = DEFAULT_TIMEOUT_SECONDS,
    ) -> str:
        driver = self._require_driver()
        by = self._locator(strategy, value)
        expected_state = (state or "visible").lower()

        if expected_state == "present":
            WebDriverWait(driver, timeout_seconds or DEFAULT_TIMEOUT_SECONDS).until(
                EC.presence_of_element_located(by)
            )
        elif expected_state == "visible":
            self._wait_for_visible(by, timeout_seconds)
        elif expected_state == "clickable":
            self._wait_for_clickable(by, timeout_seconds)
        else:
            raise ValueError(f"Unsupported element state: {state}")

        return f"Element is {expected_state} using {strategy}={value}"

    def select_option(
        self,
        strategy: str,
        value: str,
        select_by: str,
        option: str,
        timeout_seconds: int = DEFAULT_TIMEOUT_SECONDS,
    ) -> str:
        element = self._wait_for_visible(self._locator(strategy, value), timeout_seconds)
        select = Select(element)
        method = select_by.lower()

        if method == "text":
            select.select_by_visible_text(option)
        elif method == "value":
            select.select_by_value(option)
        elif method == "index":
            select.select_by_index(int(option))
        else:
            raise ValueError(f"Unsupported selectBy value: {select_by}")

        return f"Selected option by {select_by} from element using {strategy}={value}"

    # -- frames / windows ------------------------------------------------------

    def switch_to_frame(
        self,
        strategy: Optional[str],
        value: Optional[str],
        name_or_id: Optional[str],
        index: Optional[int],
        timeout_seconds: int = DEFAULT_TIMEOUT_SECONDS,
    ) -> str:
        driver = self._require_driver()

        if index is not None:
            driver.switch_to.frame(index)
            return f"Switched to frame index {index}"

        if name_or_id:
            driver.switch_to.frame(name_or_id)
            return f"Switched to frame {name_or_id}"

        frame = self._wait_for_visible(self._locator(strategy, value), timeout_seconds)
        driver.switch_to.frame(frame)
        return f"Switched to frame using {strategy}={value}"

    def switch_to_default_content(self) -> str:
        driver = self._require_driver()
        driver.switch_to.default_content()
        return "Switched to default content"

    def get_window_handles(self) -> str:
        driver = self._require_driver()
        return f"Current window: {driver.current_window_handle}\nWindow handles: {driver.window_handles}"

    def switch_to_window(self, handle: str) -> str:
        driver = self._require_driver()
        if not handle:
            raise ValueError("handle is required")
        driver.switch_to.window(handle)
        return f"Switched to window {handle}. Title: {driver.title}. URL: {driver.current_url}"

    # -- javascript / misc ------------------------------------------------------

    def execute_script(
        self,
        script: str,
        strategy: Optional[str] = None,
        value: Optional[str] = None,
        timeout_seconds: int = DEFAULT_TIMEOUT_SECONDS,
    ) -> str:
        driver = self._require_driver()
        if not script:
            raise ValueError("script is required")
        if len(script) > MAX_SCRIPT_LENGTH:
            raise ValueError(f"script must be {MAX_SCRIPT_LENGTH} characters or fewer")

        if strategy:
            element = self._wait_for_visible(self._locator(strategy, value), timeout_seconds)
            result = driver.execute_script(script, element)
        else:
            result = driver.execute_script(script)

        return "null" if result is None else str(result)

    def scroll_to_element(self, strategy: str, value: str, timeout_seconds: int = DEFAULT_TIMEOUT_SECONDS) -> str:
        driver = self._require_driver()
        element = self._wait_for_visible(self._locator(strategy, value), timeout_seconds)
        driver.execute_script("arguments[0].scrollIntoView({block:'center',inline:'nearest'});", element)
        return f"Scrolled to element using {strategy}={value}"

    def hover_element(self, strategy: str, value: str, timeout_seconds: int = DEFAULT_TIMEOUT_SECONDS) -> str:
        driver = self._require_driver()
        element = self._wait_for_visible(self._locator(strategy, value), timeout_seconds)
        ActionChains(driver).move_to_element(element).perform()
        return f"Hovered over element using {strategy}={value}"

    def press_key(
        self,
        key: str,
        strategy: Optional[str] = None,
        value: Optional[str] = None,
        timeout_seconds: int = DEFAULT_TIMEOUT_SECONDS,
    ) -> str:
        driver = self._require_driver()
        selenium_key = self._key_value(key)

        if strategy:
            element = self._wait_for_visible(self._locator(strategy, value), timeout_seconds)
            element.send_keys(selenium_key)
        else:
            ActionChains(driver).send_keys(selenium_key).perform()

        return f"Pressed key {key}"

    def upload_file(
        self, strategy: str, value: str, file_path: str, timeout_seconds: int = DEFAULT_TIMEOUT_SECONDS
    ) -> str:
        if not file_path:
            raise ValueError("filePath is required")

        path = Path(file_path).resolve()
        if not path.is_file():
            raise ValueError(f"File does not exist: {path}")

        element = self._wait_for_visible(self._locator(strategy, value), timeout_seconds)
        element.send_keys(str(path))
        return f"Uploaded file {path}"

    def take_screenshot_base64(self) -> str:
        driver = self._require_driver()
        return base64.b64encode(driver.get_screenshot_as_png()).decode("ascii")

    def get_accessibility_snapshot(self) -> str:
        driver = self._require_driver()
        result = driver.execute_script(_ACCESSIBILITY_SNAPSHOT_SCRIPT)
        return "{}" if result is None else str(result)

    def get_page_source(self) -> str:
        return self._require_driver().page_source

    # -- internal helpers ------------------------------------------------------

    def _require_driver(self) -> webdriver.Remote:
        if self.driver is None:
            raise RuntimeError("No active browser session. Call start_browser first.")
        return self.driver

    def _summarize_element(self, element) -> str:
        return (
            f"Element: tag={element.tag_name}, displayed={element.is_displayed()}, "
            f"enabled={element.is_enabled()}, text={_trim(element.text, 120)}"
        )

    def _wait_for_visible(self, by, timeout_seconds: int):
        driver = self._require_driver()
        return WebDriverWait(driver, timeout_seconds or DEFAULT_TIMEOUT_SECONDS).until(
            EC.visibility_of_element_located(by)
        )

    def _wait_for_clickable(self, by, timeout_seconds: int):
        driver = self._require_driver()
        return WebDriverWait(driver, timeout_seconds or DEFAULT_TIMEOUT_SECONDS).until(
            EC.element_to_be_clickable(by)
        )

    def _locator(self, strategy: Optional[str], value: Optional[str]):
        if not strategy:
            raise ValueError("locator strategy is required")
        if not value:
            raise ValueError("locator value is required")

        by = _LOCATOR_STRATEGIES.get(strategy.lower())
        if by is None:
            raise ValueError(f"Unsupported locator strategy: {strategy}")
        return (by, value)

    def _key_value(self, key: Optional[str]) -> str:
        if not key:
            raise ValueError("key is required")
        selenium_key = _KEYS.get(key.upper())
        if selenium_key is None:
            raise ValueError(f"Unsupported key: {key}")
        return selenium_key
