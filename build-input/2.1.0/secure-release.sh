#!/usr/bin/env bash
set -euo pipefail

REPO="uhuhuhuhuhuhuhuh/b33r"
VERSION="2.1.0"
VERSION_CODE="34"
EXPECTED_CERT="9948ad6eade4249478b09ab40b01073ae33182b1b1cd06cd576664a8f5b15839"
PATCH_SHA="a2048dca71090a8e18451d208be48d3ea010d49634632f18ae04401a8b916e7a"
ENCODED_SHA="c4c3889757e16ca03055a14d601df7504cc907d17740b138f683407cefebeb35"
CANDIDATE_BRANCH="${CANDIDATE_BRANCH:?candidate branch required}"
INPUT_DIR="build-input/${VERSION}"
PUBLIC_KEY_PATH="${INPUT_DIR}/ephemeral-public.pem"
PAYLOAD_PATH="${INPUT_DIR}/encrypted-signing-payload.json"
WORK_DIR="/tmp/b33r-${VERSION}"
MAIN_WORKTREE="/tmp/b33r-main-${VERSION}"
EXPORT_DIR="/tmp/b33r-${VERSION}-export"
PRIVATE_KEY="/tmp/b33r-${VERSION}-ephemeral-private.pem"
PAYLOAD_JSON="/tmp/b33r-${VERSION}-encrypted.json"
KEYSTORE="/tmp/b33r-${VERSION}-release.keystore"

cleanup() {
  rm -f "$PRIVATE_KEY" "$PAYLOAD_JSON" "$KEYSTORE" /tmp/b33r-signing.env
}
trap cleanup EXIT

rm -rf "$WORK_DIR" "$MAIN_WORKTREE" "$EXPORT_DIR"
mkdir -p "$WORK_DIR" "$EXPORT_DIR"

git config user.name "B33R Release Bot"
git config user.email "actions@users.noreply.github.com"

openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:4096 -out "$PRIVATE_KEY" >/dev/null 2>&1
openssl pkey -in "$PRIVATE_KEY" -pubout -out "$PUBLIC_KEY_PATH"
git add "$PUBLIC_KEY_PATH"
git commit -m "Publish ephemeral B33R ${VERSION} signing key"
git push origin "HEAD:${CANDIDATE_BRANCH}"

found_payload=false
for _ in $(seq 1 180); do
  git fetch -q origin "$CANDIDATE_BRANCH"
  if git show "origin/${CANDIDATE_BRANCH}:${PAYLOAD_PATH}" > "$PAYLOAD_JSON" 2>/dev/null; then
    found_payload=true
    break
  fi
  sleep 5
done
if [[ "$found_payload" != true ]]; then
  echo "Encrypted signing payload was not supplied before timeout." >&2
  exit 1
fi

python3 -m pip install --quiet --disable-pip-version-check cryptography
python3 - "$PRIVATE_KEY" "$PAYLOAD_JSON" "$KEYSTORE" <<'PY'
import base64
import json
import shlex
import sys
from pathlib import Path
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

private_path, payload_path, keystore_path = map(Path, sys.argv[1:])
payload = json.loads(payload_path.read_text())
private_key = serialization.load_pem_private_key(private_path.read_bytes(), password=None)
aes_key = private_key.decrypt(
    base64.b64decode(payload["wrappedKey"]),
    padding.OAEP(mgf=padding.MGF1(algorithm=hashes.SHA256()), algorithm=hashes.SHA256(), label=None),
)
plaintext = AESGCM(aes_key).decrypt(
    base64.b64decode(payload["nonce"]),
    base64.b64decode(payload["ciphertext"]),
    b"b33r-2.1.0-release",
)
signing = json.loads(plaintext)
keystore_path.write_bytes(base64.b64decode(signing["keystoreBase64"]))
env = {
    "B33R_RELEASE_STORE_FILE": str(keystore_path),
    "B33R_RELEASE_STORE_PASSWORD": signing["storePassword"],
    "B33R_RELEASE_KEY_ALIAS": signing["keyAlias"],
    "B33R_RELEASE_KEY_PASSWORD": signing["keyPassword"],
}
Path("/tmp/b33r-signing.env").write_text("\n".join(f"export {k}={shlex.quote(v)}" for k, v in env.items()) + "\n")
PY
source /tmp/b33r-signing.env

unzip -q versions/2.0.0/StreamDeck-IPTV-source-2.0.0.zip -d "$WORK_DIR"
cat "${INPUT_DIR}"/ui-review.patch.gz.b64.* > /tmp/b33r-${VERSION}.patch.gz.b64
printf '%s  %s\n' "$ENCODED_SHA" /tmp/b33r-${VERSION}.patch.gz.b64 | sha256sum -c -
base64 -d /tmp/b33r-${VERSION}.patch.gz.b64 | gzip -d > /tmp/b33r-${VERSION}.patch
printf '%s  %s\n' "$PATCH_SHA" /tmp/b33r-${VERSION}.patch | sha256sum -c -
(
  cd "$WORK_DIR"
  patch -p1 --forward --batch < /tmp/b33r-${VERSION}.patch
  chmod +x gradlew
  ./gradlew --no-daemon testDebugUnitTest assembleRelease
)

APK="$WORK_DIR/app/build/outputs/apk/release/app-release.apk"
test -s "$APK"
APKSIGNER="$ANDROID_HOME/build-tools/36.0.0/apksigner"
AAPT="$ANDROID_HOME/build-tools/36.0.0/aapt"
VERIFY_OUTPUT="$($APKSIGNER verify --verbose --print-certs "$APK")"
printf '%s\n' "$VERIFY_OUTPUT"
CERT="$(printf '%s\n' "$VERIFY_OUTPUT" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' | tr -d ':' | tr '[:upper:]' '[:lower:]' | head -1)"
[[ "$CERT" == "$EXPECTED_CERT" ]]
INFO="$($AAPT dump badging "$APK" | head -1)"
printf '%s\n' "$INFO"
printf '%s\n' "$INFO" | grep -q "package: name='com.streamdeck.iptv'"
printf '%s\n' "$INFO" | grep -q "versionCode='${VERSION_CODE}'"
printf '%s\n' "$INFO" | grep -q "versionName='${VERSION}'"

SOURCE="$EXPORT_DIR/StreamDeck-IPTV-source-${VERSION}.zip"
(
  cd "$WORK_DIR"
  zip -qr "$SOURCE" . -x 'app/build/*' '.gradle/*' '*.rej' '*.orig'
)
FINAL_APK="$EXPORT_DIR/StreamDeck-IPTV-${VERSION}.apk"
cp "$APK" "$FINAL_APK"
APK_SHA="$(sha256sum "$FINAL_APK" | awk '{print $1}')"
SOURCE_SHA="$(sha256sum "$SOURCE" | awk '{print $1}')"
APK_SIZE="$(stat -c '%s' "$FINAL_APK")"

git fetch -q origin main
git worktree add --detach "$MAIN_WORKTREE" origin/main
mkdir -p "$MAIN_WORKTREE/versions/${VERSION}"
cp "$FINAL_APK" "$MAIN_WORKTREE/versions/${VERSION}/StreamDeck-IPTV-${VERSION}.apk"
cp "$SOURCE" "$MAIN_WORKTREE/versions/${VERSION}/StreamDeck-IPTV-source-${VERSION}.zip"
export VERSION VERSION_CODE APK_SHA SOURCE_SHA APK_SIZE EXPECTED_CERT MAIN_WORKTREE
python3 <<'PY'
import json
import os
from pathlib import Path
version = os.environ["VERSION"]
version_code = int(os.environ["VERSION_CODE"])
apk_sha = os.environ["APK_SHA"]
source_sha = os.environ["SOURCE_SHA"]
apk_size = int(os.environ["APK_SIZE"])
cert = os.environ["EXPECTED_CERT"]
root = Path(os.environ["MAIN_WORKTREE"])
date = "2026-08-01"
notes = [
    "Rebuilds the television guide around synchronized channel/program rows and deterministic D-pad focus behavior.",
    "Introduces compact, medium, expanded, and television window policies while preserving the system font scale and accessible touch targets.",
    "Replaces split mobile navigation with a single More panel and replaces the guide action dialog with a context-preserving side panel.",
    "Debounces live previews and adds persistent per-channel player, buffering, decoder, subtitle, delay, source, and maximum-quality overrides.",
    "Refines device-language audio selection and forced-subtitle Auto behavior while retaining manual audio and subtitle controls.",
]
apk_url = f"https://github.com/uhuhuhuhuhuhuhuh/b33r/raw/main/versions/{version}/StreamDeck-IPTV-{version}.apk"
source_url = f"https://github.com/uhuhuhuhuhuhuhuh/b33r/raw/main/versions/{version}/StreamDeck-IPTV-source-{version}.zip"
release = {"schemaVersion": 1, "versionCode": version_code, "versionName": version, "releaseDate": date, "minimumAndroid": "5.0", "sizeBytes": apk_size, "sha256": apk_sha, "apkUrl": apk_url, "notes": notes, "sourceUrl": source_url, "sourceSha256": source_sha, "signingCertificateSha256": cert}
version_dir = root / "versions" / version
(version_dir / "release.json").write_text(json.dumps(release, indent=2) + "\n")
(version_dir / "README.md").write_text(f"# B33R IPTV {version}\n\n" + "\n".join(f"- {note}" for note in notes) + f"\n\nAPK SHA-256: `{apk_sha}`\n\nSource SHA-256: `{source_sha}`\n")
latest = dict(release)
latest.pop("sourceUrl", None)
latest.pop("sourceSha256", None)
latest.pop("signingCertificateSha256", None)
(root / "versions" / "latest.json").write_text(json.dumps(latest, indent=2) + "\n")
history_path = root / "versions" / "history.json"
history = json.loads(history_path.read_text())
history["latest"] = version
entry = {k: release[k] for k in ("versionCode", "versionName", "releaseDate", "sizeBytes", "apkUrl", "notes")}
history["releases"] = [entry] + [r for r in history.get("releases", []) if r.get("versionName") != version]
history_path.write_text(json.dumps(history, indent=2) + "\n")
PY

(
  cd "$MAIN_WORKTREE"
  git config user.name "B33R Release Bot"
  git config user.email "actions@users.noreply.github.com"
  git add "versions/${VERSION}" versions/latest.json versions/history.json
  git commit -m "Release B33R IPTV ${VERSION}"
  git push origin HEAD:main
)
PUBLISH_SHA="$(git -C "$MAIN_WORKTREE" rev-parse HEAD)"

cat > "$EXPORT_DIR/release-notes.md" <<'EOF_NOTES'
B33R IPTV 2.1.0 applies the complete first implementation pass from the 2.0.0 UI/UX review: remote-first synchronized EPG navigation, adaptive layouts, accessibility scaling, a context-preserving More/actions system, preview lifecycle improvements, and per-channel playback overrides. It uses original B33R code and assets.
EOF_NOTES

if gh release view "v${VERSION}" --repo "$REPO" >/dev/null 2>&1; then
  gh release delete "v${VERSION}" --repo "$REPO" --yes --cleanup-tag
fi
gh release create "v${VERSION}" "$FINAL_APK#B33R-IPTV.apk" "$SOURCE#B33R-IPTV-source.zip" --repo "$REPO" --target "$PUBLISH_SHA" --title "B33R IPTV ${VERSION}" --notes-file "$EXPORT_DIR/release-notes.md" --latest

LATEST_URL="https://github.com/${REPO}/releases/latest/download/B33R-IPTV.apk"
latest_ok=false
for _ in $(seq 1 18); do
  if curl -LfsS "$LATEST_URL" -o /tmp/B33R-IPTV-latest.apk; then
    LATEST_SHA="$(sha256sum /tmp/B33R-IPTV-latest.apk | awk '{print $1}')"
    if [[ "$LATEST_SHA" == "$APK_SHA" ]]; then
      latest_ok=true
      break
    fi
  fi
  sleep 5
done
[[ "$latest_ok" == true ]]
cp "$FINAL_APK" "$EXPORT_DIR/B33R-IPTV-${VERSION}.apk"
cp "$SOURCE" "$EXPORT_DIR/B33R-IPTV-source-${VERSION}.zip"
cat > "$EXPORT_DIR/checksums.txt" <<EOF_SUMS
APK_SHA256=${APK_SHA}
SOURCE_SHA256=${SOURCE_SHA}
SIGNING_CERT_SHA256=${EXPECTED_CERT}
VERSION_CODE=${VERSION_CODE}
PUBLISH_COMMIT=${PUBLISH_SHA}
EOF_SUMS
