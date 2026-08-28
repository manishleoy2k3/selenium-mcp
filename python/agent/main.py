"""CLI entry point for the agent layer: intake -> orchestrator -> status output.

Usage:
    python -m python.agent.main "Log in and verify the dashboard header is visible"
"""

from __future__ import annotations

import asyncio
import sys

from python.agent.orchestrator import build_default_orchestrator
from python.agent.status import StatusReporter

DEFAULT_URL = "https://opensource-demo.orangehrmlive.com/web/index.php/auth/login"


async def _main() -> None:
    request = " ".join(sys.argv[1:]).strip() or (
        "Explore the OrangeHRM login page and verify the login form is visible."
    )

    reporter = StatusReporter()
    orchestrator = await build_default_orchestrator(reporter)
    result = await orchestrator.run(request, DEFAULT_URL)
    print(result)


if __name__ == "__main__":
    asyncio.run(_main())
