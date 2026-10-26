#!/usr/bin/env bash
# Builds the Android game into build/dist/android, named by version.
#
#   packaging/package-android.sh                 debug APK (signed with the Android debug key)
#   packaging/package-android.sh release         release APK, shrunk by R8, signed (see below)
#   packaging/package-android.sh bundle          release bundle (.aab) for Google Play, signed (see below),
#                                                with the R8 mapping file beside it ("-mapping.txt")
#   packaging/package-android.sh debug release   several at once
#   ... --install                                also installs the (last) APK with adb
#
# Signing a release: KEYSTORE=/path/to.jks KEY_ALIAS=... KEYSTORE_PASSWORD=... [KEY_PASSWORD=...].
# The bundle is signed with that key (the Play upload key; Google re-signs with the app signing key).
# Without KEYSTORE the Play upload key is used when this machine has it:
# ~/Sync/Secrets/tank-arena-play-store.jks, alias "upload", password in the ".password" file beside it.
# Without any keystore the release APK is signed with the Android debug key (~/.android/debug.keystore) and
# named "-release-debugkey": installable for testing, not for a store. The bundle is then left
# unsigned and named "-release-unsigned".
#
# ANDROID_HOME defaults to ~/Android/Sdk. The version comes from git (packaging/version.sh); VERSION overrides
# it. It is stamped into app-android/module.yaml (versionName, versionCode) for the build and put back after.
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"

export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
version="${VERSION:-$(packaging/version.sh)}"
code="$(packaging/version.sh --code)"
dist="build/dist/android"
mkdir -p "$dist"

install=false
targets=()
for arg in "$@"; do
    case "$arg" in
        --install) install=true ;;
        debug | release | bundle) targets+=("$arg") ;;
        *) echo "Unknown argument: $arg (debug, release, bundle, --install)" >&2; exit 2 ;;
    esac
done
[[ ${#targets[@]} -eq 0 ]] && targets=(debug)

# The version goes into module.yaml for the build only; the checkout keeps its placeholder.
module="app-android/module.yaml"
cp "$module" "$module.orig"
trap 'mv "$root/$module.orig" "$root/$module"' EXIT
sed -i -E "s/^( *versionName:).*/\1 $version/; s/^( *versionCode:).*/\1 $code/" "$module"

build_tools="$(ls -d "$ANDROID_HOME"/build-tools/* 2>/dev/null | sort -V | tail -n 1)"
apksigner="$build_tools/apksigner"

upload_keystore="$HOME/Sync/Secrets/tank-arena-play-store.jks"
if [[ -z "${KEYSTORE:-}" && -f "$upload_keystore" ]]; then
    KEYSTORE="$upload_keystore"
    KEY_ALIAS="${KEY_ALIAS:-upload}"
    [[ -n "${KEYSTORE_PASSWORD:-}" ]] || KEYSTORE_PASSWORD="$(< "${upload_keystore%.jks}.password")"
    export KEYSTORE_PASSWORD
fi
# The key's own password, or the keystore's when it has none of its own.
key_pass_var=KEYSTORE_PASSWORD
[[ -n "${KEY_PASSWORD:-}" ]] && key_pass_var=KEY_PASSWORD
if [[ -n "${KEYSTORE:-}" ]]; then
    : "${KEY_ALIAS:?KEY_ALIAS is needed with KEYSTORE}" "${KEYSTORE_PASSWORD:?KEYSTORE_PASSWORD is needed with KEYSTORE}"
fi

last_apk=""
for target in "${targets[@]}"; do
    case "$target" in
        debug)
            # The Android build does not notice changed resources or a changed manifest: start over.
            rm -rf build/tasks/_app-android_buildAndroidDebug
            ./kotlin build -m app-android
            out="$dist/TankArena-$version-debug.apk"
            cp build/tasks/_app-android_buildAndroidDebug/gradle-project-debug.apk "$out"
            last_apk="$out"
            ;;
        release)
            rm -rf build/tasks/_app-android_buildAndroidRelease
            ./kotlin build -m app-android -v release
            unsigned="build/tasks/_app-android_buildAndroidRelease/gradle-project-release-unsigned.apk"
            if [[ -n "${KEYSTORE:-}" ]]; then
                out="$dist/TankArena-$version-release.apk"
                "$apksigner" sign --ks "$KEYSTORE" --ks-key-alias "$KEY_ALIAS" \
                    --ks-pass "env:KEYSTORE_PASSWORD" --key-pass "env:$key_pass_var" \
                    --v4-signing-enabled false --out "$out" "$unsigned"
            else
                out="$dist/TankArena-$version-release-debugkey.apk"
                "$apksigner" sign --ks "$HOME/.android/debug.keystore" --ks-key-alias androiddebugkey \
                    --ks-pass pass:android --key-pass pass:android --v4-signing-enabled false --out "$out" "$unsigned"
            fi
            "$apksigner" verify "$out"
            last_apk="$out"
            ;;
        bundle)
            ./kotlin package -m app-android
            task="build/tasks/_app-android_bundleAndroid"
            # Play Console takes the mapping file to turn R8's short names in crash reports back.
            mapping="$dist/TankArena-$version-mapping.txt"
            cp "$task/gradle-project/build/_app-android/outputs/mapping/release/mapping.txt" "$mapping"
            echo "Wrote $mapping"
            if [[ -n "${KEYSTORE:-}" ]]; then
                out="$dist/TankArena-$version-release.aab"
                # apksigner does not sign bundles; Play expects a JAR signature made by jarsigner.
                jarsigner -keystore "$KEYSTORE" -storepass:env KEYSTORE_PASSWORD -keypass:env "$key_pass_var" \
                    -sigalg SHA256withRSA -digestalg SHA-256 -signedjar "$out" \
                    "$task/gradle-project-release.aab" "$KEY_ALIAS"
                jarsigner -verify "$out" >/dev/null
            else
                out="$dist/TankArena-$version-release-unsigned.aab"
                cp "$task/gradle-project-release.aab" "$out"
            fi
            ;;
    esac
    echo "Wrote $out"
done

if $install; then
    [[ -n "$last_apk" ]] || { echo "Nothing to install: build debug or release too" >&2; exit 2; }
    adb install -r "$last_apk"
fi
