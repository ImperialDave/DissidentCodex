#!/usr/bin/env bash
# Build Codex APK and save to the canonical project root.
# CANONICAL OUTPUT: ~/AndroidStudioProjects/Codex/Codex-latest.apk
set -euo pipefail

CANONICAL="${CODEX_PROJECT:-$HOME/AndroidStudioProjects/Codex}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

sync_to_canonical() {
  local src="$1"
  echo "Syncing ${src} → ${CANONICAL}..."
  cp "${src}/app/build.gradle.kts" "${CANONICAL}/app/build.gradle.kts"
  cp -r "${src}/app/src" "${CANONICAL}/app/"
  cp "${src}/firestore.rules" "${src}/storage.rules" "${CANONICAL}/"
}

if [[ "$ROOT" == *".grok/worktrees"* ]]; then
  echo "Worktree detected — syncing sources to canonical project first..."
  sync_to_canonical "$ROOT"
  ROOT="$CANONICAL"
fi

cd "$ROOT"
export JAVA_HOME="${JAVA_HOME:-/opt/android-studio/jbr}"

# Default to debug: signed with the debug keystore so it upgrades prior sideloaded builds.
# Release builds are unsigned and will NOT install over existing debug-signed installs.
BUILD_TYPE="${1:-debug}"
if [[ "$BUILD_TYPE" == "release" ]]; then
  ./gradlew assembleRelease
  APK_SRC="app/build/outputs/apk/release/app-release-unsigned.apk"
  echo "WARNING: release APK is unsigned — use only for fresh installs, not upgrades."
else
  ./gradlew assembleDebug
  APK_SRC="app/build/outputs/apk/debug/app-debug.apk"
fi

APK_DST="${ROOT}/Codex-latest.apk"
cp "$APK_SRC" "$APK_DST"
echo ""
echo "APK saved to canonical location:"
echo "  ${APK_DST}"
ls -lh "$APK_DST"
echo ""
echo "Install: adb install -r ${APK_DST}"