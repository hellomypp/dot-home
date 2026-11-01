#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../apps/server"
if [[ -z "${JAVA_HOME:-}" ]] && [[ -x /usr/libexec/java_home ]]; then
  export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
fi
export GRADLE_USER_HOME="${DOT_HOME_GRADLE_CACHE:-$PWD/.gradle-local}"
if [[ "${1:-dev}" == dev ]]; then
  exec ./gradlew bootRun --args="--spring.profiles.active=dev --app.origin=${APP_ORIGIN:-http://127.0.0.1:5173}" --console=plain
fi
exec ./gradlew "$@" --console=plain
