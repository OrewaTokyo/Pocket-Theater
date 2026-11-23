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
grep -m1 "^package:" <<<"$BADGING"
grep -q "^package: name='com.pockettheater' versionCode='$CODE' versionName='$NAME'" <<<"$BADGING" \
  || fail "APK id or version is wrong. Expected com.pockettheater, code $CODE, name $NAME."
# Different build-tools versions print the minimum Android version as sdkVersion or as minSdkVersion, so accept both.
if ! grep -Eq "^(min)?[sS]dkVersion:'31'" <<<"$BADGING"; then
  echo "Lines about the Android version in the APK:"; grep -i "sdkversion" <<<"$BADGING" || echo "(none)"
  fail "APK minimum Android version is not 12 (API 31)."
fi

# A debuggable app lets anyone with a USB cable read its private data, so a published build must not be one.
if grep -q "^application-debuggable" <<<"$BADGING"; then fail "The APK is a debug build (debuggable). Publish the release build."; fi

for perm in INTERNET FOREGROUND_SERVICE FOREGROUND_SERVICE_SPECIAL_USE POST_NOTIFICATIONS; do
  grep -q "uses-permission: name='android.permission.$perm'" <<<"$BADGING" || fail "Permission $perm is missing."
done

LIST=$(unzip -Z1 "$APK")
grep -q '^classes.dex$' <<<"$LIST" || fail "APK has no app code (classes.dex)."
grep -q '^assets/web/index.html$' <<<"$LIST" || fail "The laptop site (assets/web/index.html) is not inside the APK."

"$BT/apksigner" verify --print-certs "$APK" | tee /tmp/certs.txt
FP=$(grep -m1 'SHA-256 digest' /tmp/certs.txt | awk '{print $NF}')
[ -n "$FP" ] || fail "APK is not signed."
echo "Signing key SHA-256: $FP"
# When the workflow knows the permanent key's fingerprint, the APK must be signed by exactly that key.
if [ -n "${EXPECTED_KEY_SHA256:-}" ]; then
  [ "$FP" = "$(echo "$EXPECTED_KEY_SHA256" | tr -d ':' | tr 'A-F' 'a-f')" ] \
    || fail "APK is not signed with the permanent key (got $FP). New builds would not install over the old one."
  echo "Signed with the permanent key."
fi
[ -z "${GITHUB_OUTPUT:-}" ] || echo "key=$FP" >> "$GITHUB_OUTPUT"
echo "APK checks passed."
