#!/usr/bin/env bash
set -euo pipefail
export HOSTNAME="${HOSTNAME:-0.0.0.0}"
export PORT="${PORT:-3000}"
exec node server.js