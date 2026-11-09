#!/usr/bin/env sh
set -eu
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROPERTIES="$APP_HOME/gradle/wrapper/gradle-wrapper.properties"
URL=$(sed -n 's/^distributionUrl=//p' "$PROPERTIES" | sed 's/\\:/:/g' | head -n 1)
ARCHIVE=$(basename "$URL")
VERSION=$(printf "%s" "$ARCHIVE" | sed 's/^gradle-//; s/-bin.zip$//')
GRADLE_USER_HOME=${GRADLE_USER_HOME:-"$HOME/.gradle"}
INSTALL_DIR="$GRADLE_USER_HOME/wrapper/dists/gradle-$VERSION/albarq-bootstrap"
GRADLE_BIN="$INSTALL_DIR/gradle-$VERSION/bin/gradle"
if [ ! -x "$GRADLE_BIN" ]; then
  mkdir -p "$INSTALL_DIR"
  TMP_ZIP="$INSTALL_DIR/$ARCHIVE.$$"
  if command -v curl >/dev/null 2>&1; then curl --fail --location --retry 3 "$URL" --output "$TMP_ZIP"
  elif command -v wget >/dev/null 2>&1; then wget -O "$TMP_ZIP" "$URL"
  else echo "curl or wget is required to download Gradle." >&2; exit 1; fi
  unzip -q "$TMP_ZIP" -d "$INSTALL_DIR"
  rm -f "$TMP_ZIP"
fi
exec "$GRADLE_BIN" "$@"
