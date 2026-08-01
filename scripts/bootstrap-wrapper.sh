#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
TARGET="$ROOT/gradle/wrapper/gradle-wrapper.jar"
URL="https://raw.githubusercontent.com/gradle/gradle/v9.5.0/gradle/wrapper/gradle-wrapper.jar"
EXPECTED="497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7"

mkdir -p "$(dirname "$TARGET")"

if command -v curl >/dev/null 2>&1; then
    curl --fail --location --show-error "$URL" --output "$TARGET"
elif command -v wget >/dev/null 2>&1; then
    wget -O "$TARGET" "$URL"
else
    echo "curl or wget is required." >&2
    exit 1
fi

if command -v sha256sum >/dev/null 2>&1; then
    ACTUAL=$(sha256sum "$TARGET" | awk '{print $1}')
elif command -v shasum >/dev/null 2>&1; then
    ACTUAL=$(shasum -a 256 "$TARGET" | awk '{print $1}')
else
    echo "sha256sum or shasum is required to verify the wrapper." >&2
    rm -f "$TARGET"
    exit 1
fi

if [ "$ACTUAL" != "$EXPECTED" ]; then
    echo "Gradle wrapper checksum mismatch." >&2
    rm -f "$TARGET"
    exit 1
fi

echo "Gradle wrapper installed and verified: $TARGET"
