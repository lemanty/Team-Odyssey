#!/bin/bash
cd "$(dirname "$0")/.." || exit 1
python3 scripts/database.py host-start && python3 scripts/database.py init && python3 scripts/database.py web-host
read -r -p "Press Enter to close..."
