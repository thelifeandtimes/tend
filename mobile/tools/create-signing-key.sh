#!/usr/bin/env bash
# Generates a PRIVATE, persistent alpha signing identity. Never commit the output.
set -euo pipefail
umask 077
destination=${1:?Usage: create-signing-key.sh /absolute/private/new-directory}
[[ "$destination" == /* && ! -e "$destination" ]] || { echo 'Use a new absolute directory.' >&2; exit 1; }
mkdir -m 700 -p "$destination"
openssl rand -base64 36 > "$destination/password.txt"
"${JAVA_HOME:?Set JAVA_HOME to JDK 21}/bin/keytool" -genkeypair \
  -keystore "$destination/tend-alpha.p12" -storetype PKCS12 \
  -storepass:file "$destination/password.txt" -keypass:file "$destination/password.txt" \
  -alias tend-alpha -keyalg RSA -keysize 4096 -validity 10000 \
  -dname 'CN=Omabit Tend Android Alpha' >/dev/null
echo 'Alpha signing key generated. Back up this directory privately; updates require the same key.'
