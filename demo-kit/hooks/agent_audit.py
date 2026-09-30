#!/usr/bin/env python3
"""Agent audit trail: one JSON line per session start and per tool call.

Writes .devin/audit/agent-audit.jsonl under the project (gitignored). Records who (OS user and
session id), what (tool and a truncated, secret-scrubbed summary of its input), when (UTC), and
whether it succeeded. Never records tool output.
"""

import getpass
import json
import os
import re
import sys
from datetime import datetime, timezone
from pathlib import Path

SCRUB = re.compile(r"(-----BEGIN [A-Z ]*PRIVATE KEY-----.*?-----END [A-Z ]*PRIVATE KEY-----|AKIA[0-9A-Z]{16}|ghp_[0-9A-Za-z]{36}|(?i:(password|secret|token)\s*[=:]\s*)\S+)", re.S)
MAX = 240


def summarize(tool_input) -> str:
    text = json.dumps(tool_input, sort_keys=True) if not isinstance(tool_input, str) else tool_input
    text = SCRUB.sub("[REDACTED]", text)
    return text if len(text) <= MAX else text[:MAX] + "..."


def main() -> None:
    kind = sys.argv[1] if len(sys.argv) > 1 else "tool"
    try:
        event = json.load(sys.stdin)
    except json.JSONDecodeError:
        event = {}
    root = Path(os.environ.get("DEVIN_PROJECT_DIR") or Path.cwd())
    out = root / ".devin" / "audit" / "agent-audit.jsonl"
    out.parent.mkdir(parents=True, exist_ok=True)
    record = {
        "at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "who": getpass.getuser(),
        "session": event.get("session_id"),
        "prompt": event.get("prompt_id"),
        "event": event.get("hook_event_name", kind),
    }
    if kind == "tool":
        response = event.get("tool_response") or {}
        record.update(
            tool=event.get("tool_name"),
            input=summarize(event.get("tool_input", {})),
            success=response.get("success") if isinstance(response, dict) else None,
        )
    with out.open("a") as f:
        f.write(json.dumps(record) + "\n")
    sys.exit(0)


if __name__ == "__main__":
    main()
