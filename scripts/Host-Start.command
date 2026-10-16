#!/bin/bash
cd "$(dirname "$0")/.." || exit 1
python3 scripts/database.py host-start && python3 scripts/database.py init && python3 scripts/database.py web-host
status=$?
if [ "$status" -ne 0 ]; then
  echo "启动未完成，请查看上方提示。"
fi
read -r -p "Press Enter to close..."
exit "$status"
