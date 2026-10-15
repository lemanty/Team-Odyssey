#!/bin/bash
cd "$(dirname "$0")/.." || exit 1
python3 scripts/database.py web-member
read -r -p "Press Enter to close..."
