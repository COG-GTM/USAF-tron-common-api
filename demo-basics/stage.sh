#!/usr/bin/env bash
# Adds one layer of the "first principles" demo at a time. Run from the repository root.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
L=demo-basics/layers
P=demo-basics/plugin/sf-standards

case "${1:-status}" in
  standards)
    mkdir -p .devin/rules
    cp "$P/rules/software-factory-standards.md" .devin/rules/software-factory-standards.md
    echo "Added .devin/rules/software-factory-standards.md (always on). Start a new session to load it."
    ;;
  secret)
    cp "$L/fake.env" .env
    echo "Created a FAKE .env for the guardrails step."
    ;;
  guardrails)
    mkdir -p .devin
    cp "$L/config.json" .devin/config.json
    echo "Added .devin/config.json (allow / ask / deny). Start a new session to load it."
    ;;
  skill)
    mkdir -p .devin/skills/new-endpoint
    cp "$P/skills/new-endpoint/SKILL.md" .devin/skills/new-endpoint/SKILL.md
    echo "Added .devin/skills/new-endpoint/SKILL.md. Type /new-endpoint in a new session."
    ;;
  reviewer)
    mkdir -p .devin/agents
    cp "$P/agents/standards-reviewer.md" .devin/agents/standards-reviewer.md
    echo "Added .devin/agents/standards-reviewer.md (a read-only subagent). Start a new session to load it."
    ;;
  reset)
    rm -f .devin/rules/software-factory-standards.md .devin/config.json .env
    rm -rf .devin/skills/new-endpoint
    rm -f .devin/agents/standards-reviewer.md
    rmdir .devin/rules .devin/skills .devin/agents .devin 2>/dev/null || true
    git checkout -- src
    git clean -fq -- src
    echo "Back to the starting point. Untracked files left for you to review:"
    git status --short
    ;;
  status)
    for f in .devin/rules/software-factory-standards.md .env .devin/config.json .devin/skills/new-endpoint/SKILL.md .devin/agents/standards-reviewer.md; do
      if [ -e "$f" ]; then echo "  on   $f"; else echo "  off  $f"; fi
    done
    ;;
  *)
    echo "usage: demo-basics/stage.sh standards|secret|guardrails|skill|reviewer|reset|status" >&2
    exit 2
    ;;
esac
