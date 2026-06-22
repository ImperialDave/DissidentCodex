#!/usr/bin/env bash
# Wipes broken Firebase CLI credentials so login can start fresh.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

echo "Cleaning Firebase auth for: ${ROOT}"

rm -f "${ROOT}/.config/configstore/firebase-tools.json"
rm -f "${ROOT}/.config/configstore/update-notifier-firebase-tools.json"
rm -f "${ROOT}/.firebase-token.local"
rm -f "${ROOT}/firebase-debug.log"

# Also clear home-folder creds that sometimes conflict on Chromebook.
rm -f "${HOME}/.config/configstore/firebase-tools.json" 2>/dev/null || true

echo "Done. Credentials cleared."
echo "Next: npm run firebase:login:ci"