#!/bin/bash
cd "$(dirname "$0")/.." || exit 1
python3 scripts/database.py tunnel
read -r -p "Press Enter to close..."
