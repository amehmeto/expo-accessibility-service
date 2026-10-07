#!/usr/bin/env bash
# Runs the detekt CLI on the Kotlin sources, with detekt.yml and the baseline.
#
# The CLI, not the Gradle plugin: this module builds as a subproject of the host
# app, so a plugin declared in android/build.gradle would land in every
# consumer's build. The jar is fetched once per machine and checked against the
# digest pinned below.
#
# Usage: scripts/detekt.sh [--create-baseline]
set -euo pipefail

DETEKT_VERSION="1.23.8"
DETEKT_SHA256="2ce2ff952e150baf28a29cda70a363b0340b3e81a55f43e51ec5edffc3d066c1"

repo_root=$(cd "$(dirname "$0")/.." && pwd)
cache_dir="${XDG_CACHE_HOME:-$HOME/.cache}/detekt"
jar="$cache_dir/detekt-cli-$DETEKT_VERSION-all.jar"

if ! command -v java >/dev/null 2>&1; then
  echo "detekt needs a JDK and no java is on PATH." >&2
  exit 1
fi

read_sha256() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | cut -d' ' -f1
  else
    shasum -a 256 "$1" | cut -d' ' -f1
  fi
}

if [ ! -f "$jar" ]; then
  mkdir -p "$cache_dir"
  download="$jar.download.$$"
  curl -fsSL --retry 3 -o "$download" \
    "https://github.com/detekt/detekt/releases/download/v$DETEKT_VERSION/detekt-cli-$DETEKT_VERSION-all.jar"
  actual=$(read_sha256 "$download")
  if [ "$actual" != "$DETEKT_SHA256" ]; then
    rm -f "$download"
    echo "detekt-cli $DETEKT_VERSION digest mismatch: got $actual, expected $DETEKT_SHA256." >&2
    exit 1
  fi
  mv "$download" "$jar"
fi

cd "$repo_root"
exec java -jar "$jar" \
  --input android/src \
  --config detekt.yml \
  --baseline android/detekt-baseline.xml \
  "$@"
