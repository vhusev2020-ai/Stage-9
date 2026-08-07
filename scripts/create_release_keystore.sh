#!/usr/bin/env bash
set -e
keytool -genkeypair   -keystore vebalist-release.jks   -alias vebalist   -keyalg RSA   -keysize 2048   -validity 10000
echo
echo "Then base64-encode vebalist-release.jks and save it in GitHub Actions secret VEBALIST_KEYSTORE_B64."
