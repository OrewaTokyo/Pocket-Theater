#!/usr/bin/env bash
# Usage: verify-apk.sh <apk> <version code> <version name>
# Fails if the APK is not the app we meant to build. Writes the signing fingerprint to $GITHUB_OUTPUT.
set -euo pipefail

APK="$1"; CODE="$2"; NAME="$3"
fail() { echo "::error::$1"; exit 1; }

[ -f "$APK" ] || fail "APK not found at $APK"
SIZE=$(stat -c %s "$APK")
[ "$SIZE" -gt 500000 ] || fail "APK is only $SIZE bytes, so something is missing from it."

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
BT=$(ls -d "$SDK"/build-tools/* 2>/dev/null | sort -V | tail -1)
[ -n "$BT" ] || fail "Android build-tools were not found on this runner."

BADGING=$("$BT/aapt2" dump badging "$APK")
echo "$BADGING" | grep -m1 "^package:"
echo "$BADGING" | grep -q "^package: name='com.pockettheater' versionCode='$CODE' versionName='$NAME'" \
  || fail "APK id or version is wrong. Expected com.pockettheater, code $CODE, name $NAME."
echo "$BADGING" | grep -q "^sdkVersion:'31'" || fail "APK minimum Android version is not 12 (API 31)."

for perm in INTERNET FOREGROUND_SERVICE FOREGROUND_SERVICE_SPECIAL_USE POST_NOTIFICATIONS; do
  echo "$BADGING" | grep -q "uses-permission: name='android.permission.$perm'" || fail "Permission $perm is missing."
done

LIST=$(unzip -Z1 "$APK")
echo "$LIST" | grep -q '^classes.dex$' || fail "APK has no app code (classes.dex)."
echo "$LIST" | grep -q '^assets/web/index.html$' || fail "The laptop site (assets/web/index.html) is not inside the APK."

"$BT/apksigner" verify --print-certs "$APK" | tee /tmp/certs.txt
FP=$(grep -m1 'SHA-256 digest' /tmp/certs.txt | awk '{print $NF}')
[ -n "$FP" ] || fail "APK is not signed."
echo "Signing key SHA-256: $FP"
[ -z "${GITHUB_OUTPUT:-}" ] || echo "key=$FP" >> "$GITHUB_OUTPUT"
echo "APK checks passed."
