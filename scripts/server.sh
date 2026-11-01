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
if [[ "${1:-}" == google || "${1:-}" == preview ]]; then
  dot_home_google_env="${DOT_HOME_GOOGLE_ENV_FILE:-$PWD/../../.tools/google.env}"
  if [[ -f "$dot_home_google_env" ]]; then
    set -a
    source "$dot_home_google_env"
    set +a
  fi
  dot_home_profiles=local
  if [[ "${1:-}" == google || -n "${GOOGLE_CLIENT_ID:-}" && -n "${GOOGLE_CLIENT_SECRET:-}" ]]; then
    dot_home_profiles=local,google
  fi
  export APP_ORIGIN="${APP_ORIGIN:-http://127.0.0.1:5173}"
  if [[ "$APP_ORIGIN" == http://127.0.0.1:* || "$APP_ORIGIN" == http://localhost:* ]]; then
    export SESSION_COOKIE_SECURE="${SESSION_COOKIE_SECURE:-false}"
  else
    export SESSION_COOKIE_SECURE=true
  fi
  if [[ "${1:-}" == preview ]]; then
    exec "${JAVA_HOME:+$JAVA_HOME/bin/}java" -jar build/libs/dot-home-server-0.1.0.jar --spring.profiles.active="$dot_home_profiles"
  fi
  exec ./gradlew bootRun --args="--spring.profiles.active=$dot_home_profiles" --console=plain
fi
exec ./gradlew "$@" --console=plain
