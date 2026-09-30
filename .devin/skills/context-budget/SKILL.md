---
name: context-budget
description: Measure how much always-on context this repo loads into every session (rules, AGENTS files, skill descriptions) and recommend what to move into on-demand skills. Use when asked about context cost, context rot, or why the agent ignores a rule.
allowed-tools:
  - read
  - glob
  - exec
---

1. Run `python3 demo-kit/tools/context_budget.py` and show its table.
2. Explain the three tiers: always-on (loaded every session), discovered lazily (subdirectory rules, glob rules), and on demand (skill bodies, loaded only when invoked; only name and description are always present).
3. Point out duplicated guidance between always-on files and name the lines.
4. Recommend a split: what stays always-on (one screen: build, test, hard rules), what moves to glob rules, and what moves into skills. Do not edit files.
