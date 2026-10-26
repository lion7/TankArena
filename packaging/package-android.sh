#!/usr/bin/env bash
# Builds the Android game into build/dist/android, named by version.
#
#   packaging/package-android.sh                 debug APK (signed with the Android debug key)
#   packaging/package-android.sh release         release APK, shrunk by R8, signed (see below)
#   packaging/package-android.sh bundle          release bundle (.aab) for Google Play
#   packaging/package-android.sh debug release   several at once
#   ... --install                                also installs the (last) APK with adb
#
# Signing a release: KEYSTORE=/path/to.jks KEY_ALIAS=... KEYSTORE_PASSWORD=... [KEY_PASSWORD=...].
# Without them the release APK is signed with the Android debug key (~/.android/debug.keystore) and
# named "-release-debugkey": installable for testing, not for a store. The bundle is left unsigned.
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
                # The key's own password, or the keystore's when it has none of its own.
                key_pass_var=KEYSTORE_PASSWORD
                [[ -n "${KEY_PASSWORD:-}" ]] && key_pass_var=KEY_PASSWORD
                "$apksigner" sign --ks "$KEYSTORE" --ks-key-alias "${KEY_ALIAS:?KEY_ALIAS is needed with KEYSTORE}" \
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
            out="$dist/TankArena-$version-release.aab"
            cp build/tasks/_app-android_bundleAndroid/gradle-project-release.aab "$out"
            ;;
    esac
    echo "Wrote $out"
done

if $install; then
    [[ -n "$last_apk" ]] || { echo "Nothing to install: build debug or release too" >&2; exit 2; }
    adb install -r "$last_apk"
fi
