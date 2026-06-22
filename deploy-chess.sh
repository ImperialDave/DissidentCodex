#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
if command -v node >/dev/null 2>&1; then
  :
elif [ -x /tmp/node-v20.18.0-linux-x64/bin/node ]; then
  export PATH="/tmp/node-v20.18.0-linux-x64/bin:${PATH:-}"
fi

cd "$ROOT"
if ! npx firebase projects:list --project dissidentcodex >/dev/null 2>&1; then
  echo "Run: npx firebase login"
  exit 1
fi

npx firebase deploy --only firestore:rules,functions:startChessGame --project dissidentcodex
"${ROOT}/scripts/build-apk.sh" debug
echo "Deployed. Install with:"
echo "adb install -r Codex-latest.apk"