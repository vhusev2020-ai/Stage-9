#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
VENV_DIR="$SCRIPT_DIR/.venv"
PYTHON_BIN=""

for candidate in \
  /usr/local/bin/python3.12 \
  /opt/homebrew/bin/python3.12 \
  /usr/local/bin/python3 \
  /opt/homebrew/bin/python3 \
  /usr/bin/python3
do
  if [ -x "$candidate" ]; then
    if "$candidate" -c 'import sys; raise SystemExit(0 if (3, 10) <= sys.version_info[:2] <= (3, 13) else 1)' 2>/dev/null; then
      PYTHON_BIN="$candidate"
      break
    fi
  fi
done

if [ -z "$PYTHON_BIN" ]; then
  osascript -e 'display alert "Python 3.10–3.13 is required" message "Install Python 3.12 with Homebrew, then open VEbalist Photo Prep again."'
  open "https://www.python.org/downloads/macos/"
  exit 1
fi

if [ ! -x "$VENV_DIR/bin/python" ]; then
  "$PYTHON_BIN" -m venv "$VENV_DIR"
fi

if ! "$VENV_DIR/bin/python" -c 'import PIL, numpy, onnxruntime' 2>/dev/null; then
  osascript -e 'display notification "Installing the free local photo components. This happens once." with title "VEbalist Photo Prep"'
  "$VENV_DIR/bin/python" -m pip install --upgrade pip
  "$VENV_DIR/bin/python" -m pip install -r "$SCRIPT_DIR/requirements.txt"
fi

exec "$VENV_DIR/bin/python" "$SCRIPT_DIR/photo_prep.py"
