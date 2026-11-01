#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

# Prefer the developer's Node/pnpm, with a fallback for a Codex-managed runtime.
dot_home_runtime="${CODEX_NODE_RUNTIME:-${HOME}/.cache/codex-runtimes/codex-primary-runtime/dependencies}"
if ! command -v node >/dev/null 2>&1 && [[ -x "$dot_home_runtime/node/bin/node" ]]; then
  export PATH="$dot_home_runtime/node/bin:$PATH"
fi
if command -v pnpm >/dev/null 2>&1; then
  exec pnpm "$@"
elif [[ -x "$dot_home_runtime/bin/fallback/pnpm" ]]; then
  exec "$dot_home_runtime/bin/fallback/pnpm" "$@"
else
  echo 'Node.js 24.15+ 및 pnpm 11.25.0을 설치한 뒤 다시 실행하세요.' >&2
  exit 1
fi
