#!/usr/bin/env bash
# Chromebook Linux (Crostini): how to deploy WITHOUT firebase login loops.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

cat <<EOF
╔══════════════════════════════════════════════════════════════╗
║  CHROMEBOOK TRUTH: firebase login / login:ci often FAILS    ║
║  in Linux (Crostini). Google OAuth + localhost is broken.   ║
║  Stop retrying the same login — use one of these instead:   ║
╚══════════════════════════════════════════════════════════════╝

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
OPTION A — Deploy RULES in browser (easiest, 2 minutes)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
No terminal login. No codes. No tokens.

1. Open: https://console.firebase.google.com/project/dissidentcodex/firestore/rules
2. Select ALL text in the editor → delete
3. On Chromebook, open: ${ROOT}/firestore.rules
   Copy entire file → paste into console → Publish

4. Open: https://console.firebase.google.com/project/dissidentcodex/storage/rules
5. Same with: ${ROOT}/storage.rules → Publish

Done. Rules are live.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
OPTION B — Service account (for terminal deploy: rules, hosting, functions)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
This is what Firebase recommends instead of login:ci.

IN BROWSER (once):
1. https://console.firebase.google.com/project/dissidentcodex/settings/serviceaccounts/adminsdk
2. Click "Generate new private key" → download JSON
3. Move file to Chromebook Linux, e.g.:
     ${ROOT}/.firebase-service-account.json

IN TERMINAL:
  cd ${ROOT}
  npm run deploy:rules

The deploy script auto-uses that JSON file. No firebase login.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
DO NOT USE (broken on your Chromebook)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  firebase login
  firebase login --no-localhost
  firebase login:ci
  npm run firebase:login:ci

These cause: "credentials no longer valid" / "Unable to authenticate
using the provided code" — that is a Chromebook+Crostini issue, not
something you are doing wrong.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Current status in ${ROOT}:
EOF

if [[ -f "${ROOT}/.firebase-service-account.json" ]]; then
  echo "  ✓ Service account found — run: npm run deploy:rules"
elif [[ -f "${ROOT}/.firebase-token.local" ]]; then
  echo "  ~ Token file exists (may be expired) — prefer Option A or B"
else
  echo "  ✗ No service account — use Option A (browser) or Option B (JSON key)"
fi