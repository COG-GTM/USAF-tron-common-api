#!/usr/bin/env python3
"""Estimate the always-on context a Devin session loads from this repository.

Tokens are estimated as characters / 4. The point is relative size, not an exact count.
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ALWAYS_ON = ["AGENTS.md", "AGENTS.local.md", "AGENT.md", "CLAUDE.md", ".windsurfrules", ".devin/global_rules.md"]
FRONTMATTER = re.compile(r"^---\n(.*?)\n---\n", re.S)


def tokens(text: str) -> int:
    return round(len(text) / 4)


def frontmatter(text: str) -> str:
    m = FRONTMATTER.match(text)
    return m.group(1) if m else ""


def trigger(text: str) -> str:
    m = re.search(r"^trigger:\s*(\S+)", frontmatter(text), re.M)
    return m.group(1) if m else "always_on"


def main() -> int:
    rows = []
    for name in ALWAYS_ON:
        p = ROOT / name
        if p.exists():
            t = p.read_text()
            rows.append(("always-on", name, len(t.splitlines()), tokens(t)))
    for d in (".devin/rules", ".windsurf/rules"):
        for p in sorted((ROOT / d).glob("*.md")):
            t = p.read_text()
            tier = "always-on" if trigger(t) == "always_on" else f"lazy ({trigger(t)})"
            rows.append((tier, str(p.relative_to(ROOT)), len(t.splitlines()), tokens(t)))
    for base in (".devin/skills", ".agents/skills", ".windsurf/skills"):
        for p in sorted((ROOT / base).glob("*/SKILL.md")):
            t = p.read_text()
            rows.append(("skill index", f"{p.relative_to(ROOT)} (name+description)", 0, tokens(frontmatter(t))))
            rows.append(("on demand", f"{p.relative_to(ROOT)} (body)", len(t.splitlines()), tokens(t)))
    width = max(len(r[1]) for r in rows)
    print(f"{'tier':<16} {'file':<{width}} {'lines':>6} {'~tokens':>8}")
    for tier, name, lines, tok in rows:
        print(f"{tier:<16} {name:<{width}} {lines:>6} {tok:>8}")
    every = sum(r[3] for r in rows if r[0] in ("always-on", "skill index"))
    demand = sum(r[3] for r in rows if r[0] == "on demand")
    print(f"\nloaded in every session: ~{every} tokens; available on demand: ~{demand} tokens")
    return 0


if __name__ == "__main__":
    sys.exit(main())
