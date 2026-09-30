#!/usr/bin/env bash
# Example of what IT would push through MDM (run as root) to give every developer on a Mac the org
# standards through Devin Local's system-level folders. Users can read these files but cannot change
# or delete them. Not part of the live demo. Set DEVIN_SYSTEM_DIR to try it somewhere harmless.
set -euo pipefail
SRC="$(cd "$(dirname "$0")/sf-standards" && pwd)"
DEST="${DEVIN_SYSTEM_DIR:-/Library/Application Support/Devin}"   # Linux: /etc/devin  Windows: C:\ProgramData\Devin

install -d -m 755 "$DEST/rules" "$DEST/skills/new-endpoint"
install -m 644 "$SRC/rules/software-factory-standards.md" "$DEST/rules/software-factory-standards.md"
install -m 644 "$SRC/skills/new-endpoint/SKILL.md" "$DEST/skills/new-endpoint/SKILL.md"
echo "Installed the software factory rule and the new-endpoint skill under $DEST"
