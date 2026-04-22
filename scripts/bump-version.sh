#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -ne 2 ]; then
    echo "usage: $0 <versionName> <versionCode>" >&2
    echo "example: $0 0.4.3 43" >&2
    exit 1
fi

NEW_NAME="$1"
NEW_CODE="$2"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PROPS="$ROOT/version.properties"
PBXPROJ="$ROOT/iosApp/iosApp.xcodeproj/project.pbxproj"

cat > "$PROPS" <<EOF
versionName=$NEW_NAME
versionCode=$NEW_CODE
EOF

sed -i.bak -E "s/(MARKETING_VERSION = )[0-9]+(\.[0-9]+)+;/\1${NEW_NAME};/g" "$PBXPROJ"
sed -i.bak -E "s/(CURRENT_PROJECT_VERSION = )[0-9]+;/\1${NEW_CODE};/g" "$PBXPROJ"
rm -f "${PBXPROJ}.bak"

echo "bumped to versionName=$NEW_NAME versionCode=$NEW_CODE"
