#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

command -v python3 >/dev/null 2>&1 || { echo "Python 3 is required." >&2; exit 1; }
command -v java >/dev/null 2>&1 || { echo "Java 17 is required." >&2; exit 1; }

python3 -m venv backend/.venv
backend/.venv/bin/python -m pip install --upgrade pip
backend/.venv/bin/pip install -r backend/requirements.txt

echo
echo "Backend dependencies installed."
if command -v gradle >/dev/null 2>&1; then
  echo "Gradle detected. Run ./scripts/check.sh to validate everything."
else
  echo "Install Gradle 8.7 or use GitHub Actions to build the Android APK."
fi
