#!/usr/bin/env sh
set -eu

GRADLE_VERSION=8.9
ROOT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
GRADLE_HOME="$ROOT_DIR/.gradle-local/gradle-$GRADLE_VERSION"
GRADLE_ZIP="$ROOT_DIR/.gradle-local/gradle-$GRADLE_VERSION-bin.zip"

if [ ! -x "$GRADLE_HOME/bin/gradle" ]; then
  mkdir -p "$ROOT_DIR/.gradle-local"
  if command -v curl >/dev/null 2>&1; then
    curl -L "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -o "$GRADLE_ZIP"
  else
    wget "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -O "$GRADLE_ZIP"
  fi
  unzip -q "$GRADLE_ZIP" -d "$ROOT_DIR/.gradle-local"
fi

exec "$GRADLE_HOME/bin/gradle" "$@"
