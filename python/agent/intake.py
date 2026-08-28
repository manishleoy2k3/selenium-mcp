"""Intake: turns a raw natural-language request into a structured, validated Task.

Keeping intake separate lets you reject/normalize requests (bad URLs, missing
credentials, disallowed domains) before any browser session or LLM call starts.
"""

from __future__ import annotations

import re
from dataclasses import dataclass, field
from urllib.parse import urlparse


class IntakeError(ValueError):
    """Raised when a request cannot be turned into a runnable task."""


@dataclass
class Task:
    goal: str
    target_url: str
    browser: str = "chrome"
    headless: bool = False
    allowed_domains: list[str] = field(default_factory=list)
    metadata: dict = field(default_factory=dict)


_URL_PATTERN = re.compile(r"https?://\S+")


def parse_request(raw_request: str, default_url: str, allowed_domains: list[str] | None = None) -> Task:
    """Validates and normalizes a raw user request into a Task.

    Raises IntakeError if the request is empty or references a URL outside the
    allowed domain list (when one is configured).
    """

    if not raw_request or not raw_request.strip():
        raise IntakeError("Request text is required")

    goal = raw_request.strip()
    match = _URL_PATTERN.search(goal)
    target_url = match.group(0) if match else default_url

    domains = allowed_domains or []
    if domains:
        host = urlparse(target_url).hostname or ""
        if not any(host == domain or host.endswith(f".{domain}") for domain in domains):
            raise IntakeError(f"Target host '{host}' is not in the allowed domain list")

    return Task(goal=goal, target_url=target_url, allowed_domains=domains)
