#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
exec bash scripts/pnpm.sh --filter @dot-home/web dev "$@"
