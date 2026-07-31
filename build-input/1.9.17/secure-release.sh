#!/usr/bin/env bash
set -euo pipefail

VERSION_NAME="1.9.17"
VERSION_CODE="32"
SOURCE_ARCHIVE="versions/1.9.16/StreamDeck-IPTV-source-1.9.16.zip"
HEAD_BRANCH="${GITHUB_HEAD_REF:?GITHUB_HEAD_REF is required}"
PUBLIC_KEY_PATH="build-input/1.9.17/ephemeral-public.pem"
PAYLOAD_PATH="build-input/1.9.17/signing-payload.json"
AAD="b33r-iptv-1.9.17"

PRIVATE_KEY="$RUNNER_TEMP/b33r-1.9.17-private.pem"
PAYLOAD_FILE="$RUNNER_TEMP/b33r-1.9.17-payload.json"
SECRET_DIR="$RUNNER_TEMP/b33r-1.9.17-secrets"
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
git commit -m "Publish ephemeral 1.9.17 signing key"
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
python3 - "$SOURCE_ARCHIVE" "$SRC" <<'PY'
import pathlib, sys, zipfile
archive = pathlib.Path(sys.argv[1])
target = pathlib.Path(sys.argv[2])
with zipfile.ZipFile(archive) as zf:
    for info in zf.infolist():
        name = info.filename.replace("\\", "/").lstrip("/")
        if not name:
            continue
        destination = target / name
        if info.is_dir() or name.endswith("/"):
            destination.mkdir(parents=True, exist_ok=True)
        else:
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_bytes(zf.read(info))
PY

python3 - "$SRC" <<'PY'
from pathlib import Path
import sys

root = Path(sys.argv[1])
build = root / "app/build.gradle.kts"
text = build.read_text()
for old, new in {
    'versionCode = 31': 'versionCode = 32',
    'versionName = "1.9.16"': 'versionName = "1.9.17"',
}.items():
    if text.count(old) != 1:
        raise SystemExit(f"Expected exactly one occurrence of {old!r}")
    text = text.replace(old, new)
build.write_text(text)

helper = root / "app/src/main/java/com/streamdeck/iptv/ui/PlaybackLanguage.kt"
helper.write_text(r'''package com.streamdeck.iptv.ui

import android.content.Context
import androidx.core.os.ConfigurationCompat
import java.util.Locale

internal data class PlaybackLanguagePreference(
    val media3LanguageTags: List<String>,
    val vlcLanguageTokens: List<String>,
    val trackNameHints: List<String>,
)

internal fun playbackLanguagePreference(context: Context): PlaybackLanguagePreference {
    val configured = ConfigurationCompat.getLocales(context.resources.configuration)
    val locales = mutableListOf<Locale>()
    for (index in 0 until configured.size()) {
        configured[index]?.let { locales += it }
    }
    if (locales.isEmpty()) locales += Locale.getDefault()

    val media3Tags = linkedSetOf<String>()
    val vlcTokens = linkedSetOf<String>()
    val nameHints = linkedSetOf<String>()

    locales.forEach { locale ->
        locale.toLanguageTag()
            .takeIf { it.isNotBlank() && it != "und" }
            ?.let { tag ->
                media3Tags += tag
                vlcTokens += tag
            }
        locale.language
            .takeIf { it.isNotBlank() && it != "und" }
            ?.let { language ->
                media3Tags += language
                vlcTokens += language
                nameHints += language.lowercase(Locale.ROOT)
            }
        runCatching { locale.isO3Language }
            .getOrNull()
            ?.takeIf { it.isNotBlank() && it != "und" }
            ?.let { iso3 ->
                vlcTokens += iso3
                nameHints += iso3.lowercase(Locale.ROOT)
            }
        listOf(
            locale.getDisplayLanguage(Locale.ENGLISH),
            locale.getDisplayLanguage(locale),
        ).forEach { displayName ->
            displayName
                .trim()
                .takeIf { it.length >= 2 }
                ?.let { nameHints += it.lowercase(Locale.ROOT) }
        }
    }

    return PlaybackLanguagePreference(
        media3LanguageTags = media3Tags.toList(),
        vlcLanguageTokens = vlcTokens.toList(),
        trackNameHints = nameHints.toList(),
    )
}

internal fun preferredVlcTrackId(
    choices: List<Pair<Int, String>>,
    preference: PlaybackLanguagePreference,
): Int? {
    for (hint in preference.trackNameHints) {
        val expression = if (hint.length <= 3) {
            Regex("(^|[^a-z])${Regex.escape(hint)}([^a-z]|$)")
        } else {
            null
        }
        choices.firstOrNull { (_, name) ->
            val normalized = name.lowercase(Locale.ROOT)
            expression?.containsMatchIn(normalized) ?: normalized.contains(hint)
        }?.let { return it.first }
    }
    return null
}
''')

app_ui = root / "app/src/main/java/com/streamdeck/iptv/ui/AppUi.kt"
text = app_ui.read_text()
function_index = text.index("private fun Media3PlayerScreen(")
pre, post = text[:function_index], text[function_index:]
anchor = """    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val responsive = rememberResponsiveLayout()
"""
replacement = anchor + "    val languagePreference = remember(context) { playbackLanguagePreference(context) }\n"
if post.count(anchor) < 1:
    raise SystemExit("Media3 language preference anchor missing")
post = post.replace(anchor, replacement, 1)
text = pre + post
anchor = """        ExoPlayer.Builder(context, renderersFactory)
            .setLoadControl(loadControl)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(30_000)
            .build()
            .apply {
            if (contentKind != ContentKind.LIVE && fallbackResumePositionMs > 0L) {
"""
replacement = """        ExoPlayer.Builder(context, renderersFactory)
            .setLoadControl(loadControl)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(30_000)
            .build()
            .apply {
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setPreferredAudioLanguages(*languagePreference.media3LanguageTags.toTypedArray())
                .setPreferredTextLanguages(*languagePreference.media3LanguageTags.toTypedArray())
                .setSelectUndeterminedTextLanguage(false)
                .build()
            if (contentKind != ContentKind.LIVE && fallbackResumePositionMs > 0L) {
"""
if text.count(anchor) != 1:
    raise SystemExit("Media3 player construction anchor missing")
app_ui.write_text(text.replace(anchor, replacement))

vlc = root / "app/src/main/java/com/streamdeck/iptv/ui/VlcPlayer.kt"
text = vlc.read_text()
anchor = """    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val responsive = rememberResponsiveLayout()
"""
replacement = anchor + "    val languagePreference = remember(context) { playbackLanguagePreference(context) }\n"
if text.count(anchor) != 1:
    raise SystemExit("VLC language preference anchor missing")
text = text.replace(anchor, replacement)
anchor = """    var initialSeekApplied by remember(activeUrl, softwareDecode, selectedQuality, startPositionMs) {
        mutableStateOf(false)
    }
"""
replacement = anchor + """    var automaticAudioLanguageApplied by remember(activeUrl, softwareDecode, selectedQuality) {
        mutableStateOf(false)
    }
    var automaticSubtitleLanguageApplied by remember(activeUrl, softwareDecode, selectedQuality) {
        mutableStateOf(false)
    }
"""
if text.count(anchor) != 1:
    raise SystemExit("VLC automatic language state anchor missing")
text = text.replace(anchor, replacement)
anchor = """    val libVlc = remember {
        LibVLC(
"""
replacement = """    val libVlc = remember(languagePreference) {
        LibVLC(
"""
if text.count(anchor) != 1:
    raise SystemExit("VLC remember anchor missing")
text = text.replace(anchor, replacement)
anchor = """                "--http-reconnect",
                "--no-drop-late-frames",
"""
replacement = """                "--http-reconnect",
                "--audio-language=${languagePreference.vlcLanguageTokens.joinToString(",")}",
                "--sub-language=${languagePreference.vlcLanguageTokens.joinToString(",")}",
                "--no-drop-late-frames",
"""
if text.count(anchor) != 1:
    raise SystemExit("VLC global options anchor missing")
text = text.replace(anchor, replacement)
anchor = '''    fun refreshTrackChoices() {
        audioTracks = runCatching {
            mediaPlayer.audioTracks
                .orEmpty()
                .filter { it.id >= 0 }
                .map { VlcTrackChoice(it.id, it.name.ifBlank { "Audio ${it.id}" }) }
        }.getOrDefault(emptyList())
        subtitleTracks = listOf(VlcTrackChoice(-1, "Off")) + runCatching {
            mediaPlayer.spuTracks
                .orEmpty()
                .filter { it.id >= 0 }
                .map { VlcTrackChoice(it.id, it.name.ifBlank { "Subtitle ${it.id}" }) }
        }.getOrDefault(emptyList())
        selectedAudioTrack = runCatching { mediaPlayer.audioTrack }.getOrDefault(-1)
        selectedSubtitleTrack = runCatching { mediaPlayer.spuTrack }.getOrDefault(-1)
    }
'''
replacement = '''    fun refreshTrackChoices() {
        val refreshedAudioTracks = runCatching {
            mediaPlayer.audioTracks
                .orEmpty()
                .filter { it.id >= 0 }
                .map { VlcTrackChoice(it.id, it.name.ifBlank { "Audio ${it.id}" }) }
        }.getOrDefault(emptyList())
        val refreshedSubtitleTracks = runCatching {
            mediaPlayer.spuTracks
                .orEmpty()
                .filter { it.id >= 0 }
                .map { VlcTrackChoice(it.id, it.name.ifBlank { "Subtitle ${it.id}" }) }
        }.getOrDefault(emptyList())
        audioTracks = refreshedAudioTracks
        subtitleTracks = listOf(VlcTrackChoice(-1, "Off")) + refreshedSubtitleTracks
        selectedAudioTrack = runCatching { mediaPlayer.audioTrack }.getOrDefault(-1)
        selectedSubtitleTrack = runCatching { mediaPlayer.spuTrack }.getOrDefault(-1)

        if (!automaticAudioLanguageApplied) {
            preferredVlcTrackId(
                refreshedAudioTracks.map { it.id to it.name },
                languagePreference,
            )?.let { preferredId ->
                automaticAudioLanguageApplied = true
                if (mediaPlayer.setAudioTrack(preferredId)) {
                    selectedAudioTrack = preferredId
                } else {
                    automaticAudioLanguageApplied = false
                }
            }
        }
        if (!automaticSubtitleLanguageApplied) {
            preferredVlcTrackId(
                refreshedSubtitleTracks.map { it.id to it.name },
                languagePreference,
            )?.let { preferredId ->
                automaticSubtitleLanguageApplied = true
                if (mediaPlayer.setSpuTrack(preferredId)) {
                    selectedSubtitleTrack = preferredId
                } else {
                    automaticSubtitleLanguageApplied = false
                }
            }
        }
    }
'''
if text.count(anchor) != 1:
    raise SystemExit("VLC track refresh anchor missing")
text = text.replace(anchor, replacement)
anchor = '''            addOption(":http-reconnect")
            addOption(
'''
replacement = '''            addOption(":http-reconnect")
            addOption(":audio-language=${languagePreference.vlcLanguageTokens.joinToString(",")}")
            addOption(":sub-language=${languagePreference.vlcLanguageTokens.joinToString(",")}")
            addOption(
'''
if text.count(anchor) != 1:
    raise SystemExit("VLC media options anchor missing")
text = text.replace(anchor, replacement)
anchor = '''                        onSelect = { choice ->
                            if (mediaPlayer.setAudioTrack(choice.id)) {
                                selectedAudioTrack = choice.id
                            }
                        },
'''
replacement = '''                        onSelect = { choice ->
                            automaticAudioLanguageApplied = true
                            if (mediaPlayer.setAudioTrack(choice.id)) {
                                selectedAudioTrack = choice.id
                            }
                        },
'''
if text.count(anchor) != 1:
    raise SystemExit("VLC audio menu anchor missing")
text = text.replace(anchor, replacement)
anchor = '''                        onSelect = { choice ->
                            if (mediaPlayer.setSpuTrack(choice.id)) {
                                selectedSubtitleTrack = choice.id
                            }
                        },
'''
replacement = '''                        onSelect = { choice ->
                            automaticSubtitleLanguageApplied = true
                            if (mediaPlayer.setSpuTrack(choice.id)) {
                                selectedSubtitleTrack = choice.id
                            }
                        },
'''
if text.count(anchor) != 1:
    raise SystemExit("VLC subtitle menu anchor missing")
vlc.write_text(text.replace(anchor, replacement))
PY
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
"$BUILD_TOOLS/aapt" dump badging "$APK" | tee "$RUNNER_TEMP/b33r-1.9.17-badging.txt"
grep -q "package: name='com.streamdeck.iptv' versionCode='32' versionName='1.9.17'" "$RUNNER_TEMP/b33r-1.9.17-badging.txt"
NEW_CERT="$("$BUILD_TOOLS/apksigner" verify --print-certs "$APK" | awk -F': ' '/Signer #1 certificate SHA-256 digest/ {print $2; exit}')"
STABLE_CERT="$("$BUILD_TOOLS/apksigner" verify --print-certs versions/1.9.16/StreamDeck-IPTV-1.9.16.apk | awk -F': ' '/Signer #1 certificate SHA-256 digest/ {print $2; exit}')"
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
PUBLISH="$RUNNER_TEMP/b33r-1.9.17-publish"
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
    "Automatically prefers audio tracks matching the Fire TV, Android TV, or phone language.",
    "Automatically prefers matching subtitle tracks when the stream exposes language metadata or recognizable track names.",
    "Keeps manual audio and subtitle track selection available and preserves the selected manual override during playback.",
    "Retains http://pro.b33r.top, the compact responsive interface, top-only navigation, refreshed artwork, favorites, Continue Watching, and playback fallbacks.",
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

Stable Android release using `http://pro.b33r.top` and automatic device-language audio/subtitle preferences.

- Package: `com.streamdeck.iptv`
- Version name: `{version}`
- Version code: `{code}`
- Minimum Android: 5.0
- Server: `http://pro.b33r.top`
- APK SHA-256: `{sha}`
- Source SHA-256: `{source_sha}`
- Signing certificate SHA-256: `{cert}`
- APK size: {size} bytes

The signing certificate matches B33R IPTV 1.9.16, permitting normal Android in-place updates.
"""
(root / f"versions/{version}/README.md").write_text(readme)
PY

cd "$PUBLISH"
git config user.name "github-actions[bot]"
git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
git add "versions/$VERSION_NAME" versions/latest.json versions/history.json
git commit -m "Release B33R IPTV $VERSION_NAME"
git push origin HEAD:main
