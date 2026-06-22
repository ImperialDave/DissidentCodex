#!/usr/bin/env bash
# Firebase CLI wrapper for Chromebook Linux (Crostini).
# Interactive `firebase login` is unreliable in Crostini — use service account or Console instead.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "${ROOT}"

# METHOD 1 (recommended on Chromebook): service account JSON key
SA_FILE="${ROOT}/.firebase-service-account.json"
if [[ -f "${SA_FILE}" ]]; then
  export GOOGLE_APPLICATION_CREDENTIALS="${SA_FILE}"
  exec npx firebase "$@"
fi

# METHOD 2: CI token file (often broken on Chromebook — kept as fallback)
TOKEN_FILE="${ROOT}/.firebase-token.local"
if [[ -f "${TOKEN_FILE}" ]]; then
  export FIREBASE_TOKEN="$(tr -d '[:space:]' < "${TOKEN_FILE}")"
  exec npx firebase "$@"
fi

# METHOD 3: legacy user login (usually fails on Chromebook)
export XDG_CONFIG_HOME="${ROOT}/.config"
exec npx firebase "$@"