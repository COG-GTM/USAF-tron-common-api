#!/usr/bin/env python3
"""PreToolUse guard. Reads the hook event on stdin; exit 2 blocks the tool call.

Permissions match tool arguments by pattern (Read(~/.ssh/**), Exec(curl)). This hook covers what
patterns cannot see: secrets referenced inside a shell command, network egress to hosts that are not
localhost, and MCP writes whose payload carries a credential. It is defence in depth, not the
primary control.
"""

import json
import re
import sys
from urllib.parse import urlparse

SECRET_PATHS = re.compile(r"(\.ssh/|\.aws/|\.env\b|\.pem\b|\.p12\b|\.jks\b|id_rsa|id_ed25519|credentials\.json)")
URL_RE = re.compile(r"https?://[^\s'\"]+")
EGRESS_TOOLS = re.compile(r"\b(curl|wget|nc|ncat|scp|rsync|ftp)\b")
ALLOWED_HOSTS = {"localhost", "127.0.0.1", "::1"}
SECRET_VALUES = re.compile(r"(-----BEGIN [A-Z ]*PRIVATE KEY-----|AKIA[0-9A-Z]{16}|ghp_[0-9A-Za-z]{36})")


def block(reason: str) -> None:
    print(json.dumps({"decision": "block", "reason": reason}))
    sys.exit(2)


def check_exec(cmd: str) -> None:
    if SECRET_PATHS.search(cmd):
        block(f"guard: command references a secret path ({SECRET_PATHS.search(cmd).group(0)})")
    if EGRESS_TOOLS.search(cmd):
        for url in URL_RE.findall(cmd):
            host = urlparse(url).hostname or ""
            if host not in ALLOWED_HOSTS:
                block(f"guard: network egress to {host} is not on the demo allowlist")
    if re.search(r"\bgit\s+push\b.*(--force|-f\b)", cmd):
        block("guard: force push is never allowed")


def main() -> None:
    try:
        event = json.load(sys.stdin)
    except json.JSONDecodeError:
        sys.exit(0)
    tool = event.get("tool_name", "")
    args = event.get("tool_input") or {}
    if tool == "exec":
        check_exec(str(args.get("command", "")))
    elif tool == "read":
        path = str(args.get("file_path") or args.get("path") or "")
        if SECRET_PATHS.search(path):
            block(f"guard: reading {path} is not allowed")
    elif tool.startswith("mcp__"):
        if SECRET_VALUES.search(json.dumps(args)):
            block("guard: MCP call payload contains a credential")
    sys.exit(0)


if __name__ == "__main__":
    main()
