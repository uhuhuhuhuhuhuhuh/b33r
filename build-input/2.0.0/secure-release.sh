#!/usr/bin/env bash
set -euo pipefail

VERSION_NAME="2.0.0"
VERSION_CODE="33"
BASE_SOURCE="versions/1.9.17/StreamDeck-IPTV-source-1.9.17.zip"
HEAD_BRANCH="${GITHUB_HEAD_REF:?GITHUB_HEAD_REF is required}"
CANDIDATE_DIR="build-input/2.0.0"
PUBLIC_KEY_PATH="$CANDIDATE_DIR/ephemeral-public.pem"
PAYLOAD_PATH="$CANDIDATE_DIR/signing-payload.json"
AAD="b33r-iptv-2.0.0"

PRIVATE_KEY="$RUNNER_TEMP/b33r-2.0.0-private.pem"
PAYLOAD_FILE="$RUNNER_TEMP/b33r-2.0.0-payload.json"
SECRET_DIR="$RUNNER_TEMP/b33r-2.0.0-secrets"
SRC="$RUNNER_TEMP/b33r-iptv-$VERSION_NAME"
OUT="$RUNNER_TEMP/b33r-$VERSION_NAME-release"

cleanup() {
  rm -rf "$PRIVATE_KEY" "$PAYLOAD_FILE" "$SECRET_DIR"
}
trap cleanup EXIT

openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:4096 -out "$PRIVATE_KEY" 2>/dev/null
openssl pkey -in "$PRIVATE_KEY" -pubout -out "$PUBLIC_KEY_PATH" 2>/dev/null
chmod 600 "$PRIVATE_KEY"
git config user.name "github-actions[bot]"
git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
git add "$PUBLIC_KEY_PATH"
git commit -m "Publish ephemeral 2.0.0 signing key"
git push origin "HEAD:$HEAD_BRANCH"

found=false
for _ in $(seq 1 180); do
  git fetch --quiet origin "$HEAD_BRANCH"
  if git show "origin/$HEAD_BRANCH:$PAYLOAD_PATH" > "$PAYLOAD_FILE" 2>/dev/null; then
    found=true
    break
  fi
  sleep 5
done
if [[ "$found" != "true" ]]; then
  echo "Encrypted signing payload was not supplied." >&2
  exit 1
fi
chmod 600 "$PAYLOAD_FILE"

python3 -m pip install --quiet cryptography
mkdir -p "$SECRET_DIR"
chmod 700 "$SECRET_DIR"
python3 - "$PRIVATE_KEY" "$PAYLOAD_FILE" "$SECRET_DIR" "$AAD" <<'PY'
import base64
import json
import pathlib
import sys
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

private_path = pathlib.Path(sys.argv[1])
payload_path = pathlib.Path(sys.argv[2])
output = pathlib.Path(sys.argv[3])
aad = sys.argv[4].encode()
payload = json.loads(payload_path.read_text())
private_key = serialization.load_pem_private_key(private_path.read_bytes(), password=None)
aes_key = private_key.decrypt(
    base64.b64decode(payload["wrappedKey"]),
    padding.OAEP(
        mgf=padding.MGF1(algorithm=hashes.SHA256()),
        algorithm=hashes.SHA256(),
        label=None,
    ),
)
plaintext = AESGCM(aes_key).decrypt(
    base64.b64decode(payload["nonce"]),
    base64.b64decode(payload["ciphertext"]),
    aad,
)
secret = json.loads(plaintext)
(output / "release.keystore").write_bytes(base64.b64decode(secret["keystoreBase64"]))
(output / "credentials.json").write_text(json.dumps({
    "storePassword": secret["storePassword"],
    "keyAlias": secret["keyAlias"],
    "keyPassword": secret["keyPassword"],
}))
(output / "release.keystore").chmod(0o600)
(output / "credentials.json").chmod(0o600)
PY
rm -f "$PRIVATE_KEY" "$PAYLOAD_FILE"

SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
SDKMANAGER="$(find "$SDK_ROOT/cmdline-tools" -type f -name sdkmanager | sort | tail -n 1)"
test -n "$SDKMANAGER"
yes | "$SDKMANAGER" --licenses >/dev/null 2>&1 || true
"$SDKMANAGER" "platform-tools" "platforms;android-36" "build-tools;36.0.0"
BUILD_TOOLS="$SDK_ROOT/build-tools/36.0.0"

rm -rf "$SRC" "$OUT"
mkdir -p "$SRC" "$OUT"
unzip -q "$BASE_SOURCE" -d "$SRC"
cat "$CANDIDATE_DIR"/patch.gz.b64.part* | base64 -d | gzip -d > "$RUNNER_TEMP/b33r-2.0.0.patch"
set +e
(cd "$SRC" && patch --batch --forward -p1 < "$RUNNER_TEMP/b33r-2.0.0.patch")
PATCH_STATUS=$?
set -e
base64 -d "$CANDIDATE_DIR/conflict-overrides.tar.gz.b64" | tar -xz -C "$SRC"
find "$SRC" -type f \( -name '*.rej' -o -name '*.orig' \) -delete
echo "Patch exit status before exact overrides: $PATCH_STATUS"
chmod +x "$SRC/gradlew"

read_secret() {
  python3 - "$SECRET_DIR/credentials.json" "$1" <<'PY'
import json, pathlib, sys
print(json.loads(pathlib.Path(sys.argv[1]).read_text())[sys.argv[2]])
PY
}
STORE_PASSWORD="$(read_secret storePassword)"
KEY_ALIAS="$(read_secret keyAlias)"
KEY_PASSWORD="$(read_secret keyPassword)"
echo "::add-mask::$STORE_PASSWORD"
echo "::add-mask::$KEY_ALIAS"
echo "::add-mask::$KEY_PASSWORD"
export B33R_RELEASE_STORE_FILE="$SECRET_DIR/release.keystore"
export B33R_RELEASE_STORE_PASSWORD="$STORE_PASSWORD"
export B33R_RELEASE_KEY_ALIAS="$KEY_ALIAS"
export B33R_RELEASE_KEY_PASSWORD="$KEY_PASSWORD"

(
  cd "$SRC"
  ./gradlew --no-daemon --stacktrace assembleRelease
)
APK="$SRC/app/build/outputs/apk/release/app-release.apk"
test -s "$APK"
"$BUILD_TOOLS/apksigner" verify --verbose --print-certs "$APK"
"$BUILD_TOOLS/aapt" dump badging "$APK" | tee "$RUNNER_TEMP/b33r-2.0.0-badging.txt"
grep -q "package: name='com.streamdeck.iptv' versionCode='$VERSION_CODE' versionName='$VERSION_NAME'" "$RUNNER_TEMP/b33r-2.0.0-badging.txt"
NEW_CERT="$("$BUILD_TOOLS/apksigner" verify --print-certs "$APK" | awk -F': ' '/Signer #1 certificate SHA-256 digest/ {print $2; exit}')"
STABLE_CERT="$("$BUILD_TOOLS/apksigner" verify --print-certs versions/1.9.17/StreamDeck-IPTV-1.9.17.apk | awk -F': ' '/Signer #1 certificate SHA-256 digest/ {print $2; exit}')"
test -n "$NEW_CERT"
test "$NEW_CERT" = "$STABLE_CERT"

cp "$APK" "$OUT/StreamDeck-IPTV-$VERSION_NAME.apk"
python3 - "$SRC" "$OUT/StreamDeck-IPTV-source-$VERSION_NAME.zip" <<'PY'
import pathlib, sys, zipfile
source = pathlib.Path(sys.argv[1])
output = pathlib.Path(sys.argv[2])
with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as zf:
    for path in sorted(source.rglob("*")):
        relative = path.relative_to(source)
        if any(part in {".gradle", "build"} for part in relative.parts):
            continue
        if path.name in {"local.properties", "release.keystore", "credentials.json"} or not path.is_file():
            continue
        zf.write(path, relative.as_posix())
PY
SIZE="$(stat -c '%s' "$OUT/StreamDeck-IPTV-$VERSION_NAME.apk")"
SHA="$(sha256sum "$OUT/StreamDeck-IPTV-$VERSION_NAME.apk" | awk '{print $1}')"
SOURCE_SHA="$(sha256sum "$OUT/StreamDeck-IPTV-source-$VERSION_NAME.zip" | awk '{print $1}')"

git fetch origin main
PUBLISH="$RUNNER_TEMP/b33r-2.0.0-publish"
rm -rf "$PUBLISH"
git worktree add --detach "$PUBLISH" origin/main
DEST="$PUBLISH/versions/$VERSION_NAME"
mkdir -p "$DEST"
cp "$OUT/StreamDeck-IPTV-$VERSION_NAME.apk" "$DEST/"
cp "$OUT/StreamDeck-IPTV-source-$VERSION_NAME.zip" "$DEST/"

VERSION_NAME="$VERSION_NAME" VERSION_CODE="$VERSION_CODE" SIZE="$SIZE" SHA="$SHA" SOURCE_SHA="$SOURCE_SHA" CERT="$NEW_CERT" python3 - "$PUBLISH" <<'PY'
from datetime import date
from pathlib import Path
import json, os, sys
root = Path(sys.argv[1])
version = os.environ["VERSION_NAME"]
code = int(os.environ["VERSION_CODE"])
size = int(os.environ["SIZE"])
sha = os.environ["SHA"]
source_sha = os.environ["SOURCE_SHA"]
cert = os.environ["CERT"]
release_date = date.today().isoformat()
base = f"https://github.com/uhuhuhuhuhuhuhuh/b33r/raw/main/versions/{version}"
notes = [
    "Introduces an original TV-first EPG grid and touch-first phone/tablet navigation shell.",
    "Adds multiple encrypted playlists, provider-specific credentials, profiles, parental controls, channel organization, global search, favorites, and viewing history.",
    "Adds scheduled recordings, reminders, rolling live-TV timeshift, multiview foundations, picture-in-picture, backup/restore, and expanded playback and appearance preferences.",
    "Keeps automatic device-language audio/subtitle selection, manual track overrides, Media3 and VLC playback fallbacks, and the http://pro.b33r.top default server.",
    "Uses original B33R code, branding, assets, and interaction design; no third-party paid feature or licensing bypass is included.",
]
latest = {
    "schemaVersion": 1,
    "versionCode": code,
    "versionName": version,
    "releaseDate": release_date,
    "minimumAndroid": "5.0",
    "sizeBytes": size,
    "sha256": sha,
    "apkUrl": f"{base}/StreamDeck-IPTV-{version}.apk",
    "notes": notes,
}
(root / "versions/latest.json").write_text(json.dumps(latest, indent=2) + "\n")
history_path = root / "versions/history.json"
history = json.loads(history_path.read_text())
history["latest"] = version
history["releases"] = [r for r in history.get("releases", []) if r.get("versionName") != version]
history["releases"].insert(0, {k: latest[k] for k in ("versionCode", "versionName", "releaseDate", "sizeBytes", "apkUrl", "notes")})
history_path.write_text(json.dumps(history, indent=2) + "\n")
release = dict(latest)
release["sourceUrl"] = f"{base}/StreamDeck-IPTV-source-{version}.zip"
release["sourceSha256"] = source_sha
release["signingCertificateSha256"] = cert
(root / f"versions/{version}/release.json").write_text(json.dumps(release, indent=2) + "\n")
readme = f"""# B33R IPTV {version}

Major original responsive IPTV release for phones, tablets, Android TV, and Fire TV.

- Package: `com.streamdeck.iptv`
- Version name: `{version}`
- Version code: `{code}`
- Minimum Android: 5.0
- Default server: `http://pro.b33r.top`
- APK SHA-256: `{sha}`
- Source SHA-256: `{source_sha}`
- Signing certificate SHA-256: `{cert}`
- APK size: {size} bytes

The signing certificate matches B33R IPTV 1.9.17, permitting normal Android in-place updates.

This release implements original B33R functionality and does not contain TiviMate code, branding, assets, or licensing bypasses.
"""
(root / f"versions/{version}/README.md").write_text(readme)
PY

cd "$PUBLISH"
git config user.name "github-actions[bot]"
git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
git add "versions/$VERSION_NAME" versions/latest.json versions/history.json
git commit -m "Release B33R IPTV $VERSION_NAME"
git push origin HEAD:main

RELEASE_NOTES="$RUNNER_TEMP/b33r-2.0.0-release-notes.md"
cat > "$RELEASE_NOTES" <<NOTES
B33R IPTV $VERSION_NAME is the original responsive IPTV overhaul for phones, tablets, Android TV, and Fire TV.

It includes the new EPG, multiple encrypted playlists, profiles and parental controls, recordings and reminders, rolling timeshift, multiview foundations, global search, backup/restore, picture-in-picture, and expanded playback/customization controls.

APK SHA-256: $SHA
Signing certificate SHA-256: $NEW_CERT
NOTES

gh release view "v$VERSION_NAME" >/dev/null 2>&1 && gh release delete "v$VERSION_NAME" --yes --cleanup-tag || true
gh release create "v$VERSION_NAME" \
  "$OUT/StreamDeck-IPTV-$VERSION_NAME.apk#B33R-IPTV.apk" \
  "$OUT/StreamDeck-IPTV-source-$VERSION_NAME.zip#B33R-IPTV-source.zip" \
  --title "B33R IPTV $VERSION_NAME" \
  --notes-file "$RELEASE_NOTES" \
  --latest

curl -fsSL --retry 5 --retry-delay 3 \
  "https://github.com/uhuhuhuhuhuhuhuh/b33r/releases/latest/download/B33R-IPTV.apk" \
  -o "$RUNNER_TEMP/B33R-IPTV-latest.apk"
echo "$SHA  $RUNNER_TEMP/B33R-IPTV-latest.apk" | sha256sum -c -
