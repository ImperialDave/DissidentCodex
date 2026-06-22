#!/usr/bin/env bash
# Builds codex-web and assembles codex-web-deploy/ for hosting upload.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="${ROOT}/codex-web"
OUT="${ROOT}/codex-web-deploy"

echo "==> Building Next.js production bundle..."
cd "${SRC}"
npm run build

echo "==> Assembling deploy package at ${OUT}..."
rm -rf "${OUT}"
mkdir -p "${OUT}"

# Copy standalone output (.next is a dotfolder — "*" would skip it)
cp -a "${SRC}/.next/standalone/." "${OUT}/"
cp -r "${SRC}/.next/static" "${OUT}/.next/static"
cp -r "${SRC}/public" "${OUT}/public"

cp "${ROOT}/scripts/deploy-package/DEPLOY.md" "${OUT}/DEPLOY.md"
cp "${ROOT}/scripts/deploy-package/.env.production.example" "${OUT}/.env.production.example"
cp "${ROOT}/scripts/deploy-package/package.json" "${OUT}/package.json"
cp "${ROOT}/scripts/deploy-package/Dockerfile" "${OUT}/Dockerfile"
cp "${ROOT}/scripts/deploy-package/.dockerignore" "${OUT}/.dockerignore"
cp "${ROOT}/scripts/deploy-package/firebase.json" "${OUT}/firebase.json"
cp "${ROOT}/scripts/deploy-package/.firebaserc" "${OUT}/.firebaserc"
cp "${ROOT}/scripts/deploy-package/start.sh" "${OUT}/start.sh"

chmod +x "${OUT}/start.sh"

echo "==> Done. Upload everything inside:"
echo "    ${OUT}"
echo "    See ${OUT}/DEPLOY.md for host-specific steps."