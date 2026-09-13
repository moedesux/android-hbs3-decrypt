#!/usr/bin/env sh

set -eu

GRADLE_VERSION=8.9
GRADLE_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}/wrapper/dists/gradle-${GRADLE_VERSION}-bin"
GRADLE_DIR="$GRADLE_HOME/gradle-${GRADLE_VERSION}"

if [ ! -x "$GRADLE_DIR/bin/gradle" ]; then
  mkdir -p "$GRADLE_HOME"
  archive="$GRADLE_HOME/gradle-${GRADLE_VERSION}-bin.zip"
  if [ ! -f "$archive" ]; then
    curl --fail --location --retry 3 --output "$archive" "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
  fi
  unzip -q -o "$archive" -d "$GRADLE_HOME"
fi

exec "$GRADLE_DIR/bin/gradle" "$@"
