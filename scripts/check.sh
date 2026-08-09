#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

PYTHON="python3"
if [ -x backend/.venv/bin/python ]; then PYTHON="backend/.venv/bin/python"; fi

"$PYTHON" -m compileall -q backend
PYTHONPATH=backend "$PYTHON" -m unittest discover -s backend/tests -v

if command -v gradle >/dev/null 2>&1; then
  gradle --no-daemon lintDebug assembleDebug
else
  echo "Gradle is unavailable locally; Android build is validated by GitHub Actions."
fi
