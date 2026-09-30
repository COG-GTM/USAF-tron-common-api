#!/usr/bin/env bash
# Adds one layer of the "first principles" demo at a time. Run from the repository root.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
L=demo-basics/layers

case "${1:-status}" in
  standards)
    mkdir -p .devin/rules
    cp "$L/software-factory-standards.md" .devin/rules/software-factory-standards.md
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
    cp "$L/skills/new-endpoint/SKILL.md" .devin/skills/new-endpoint/SKILL.md
    echo "Added .devin/skills/new-endpoint/SKILL.md. Type /new-endpoint in a new session."
    ;;
  reset)
    rm -f .devin/rules/software-factory-standards.md .devin/config.json .env
    rm -rf .devin/skills/new-endpoint
    rmdir .devin/rules .devin/skills .devin 2>/dev/null || true
    rm -f src/main/java/mil/tron/commonapi/controller/ranks/BranchController.java \
          src/test/java/mil/tron/commonapi/controller/ranks/BranchControllerTests.java
    git checkout -- src
    echo "Back to the starting point. Untracked files left for you to review:"
    git status --short
    ;;
  status)
    for f in .devin/rules/software-factory-standards.md .env .devin/config.json .devin/skills/new-endpoint/SKILL.md; do
      if [ -e "$f" ]; then echo "  on   $f"; else echo "  off  $f"; fi
    done
    ;;
  *)
    echo "usage: demo-basics/stage.sh standards|secret|guardrails|skill|reset|status" >&2
    exit 2
    ;;
esac
