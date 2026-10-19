#!/usr/bin/env bash
# Builds a self-contained TankArena for Linux: the game plus its own Java runtime, so players need
# nothing installed. Normally run inside packaging/linux.Dockerfile (see packaging/build-linux.sh).
#
#   packaging/package.sh            app image + .tar.gz in build/dist
#   packaging/package.sh appimage   also an AppImage (needs appimagetool)
#   packaging/package.sh rpm        also an .rpm (needs rpmbuild); likewise deb (needs dpkg-deb)
#
# VERSION=1.2.0 sets the version. KOTLIN_SHARED_CACHE moves the Kotlin toolchain's download cache.
# JPACKAGE=/path/to/jpackage picks the JDK tool; by default it comes from JAVA_HOME, then the JDK
# the Kotlin toolchain provisioned, then PATH.
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"

name="TankArena"
version="${VERSION:-1.0.0}"
dist="build/dist"
cache="${KOTLIN_SHARED_CACHE:-$HOME/.cache/JetBrains/Kotlin}"
kotlin=(./kotlin "--shared-cache-dir=$cache")

find_jpackage() {
    if [[ -n "${JPACKAGE:-}" ]]; then echo "$JPACKAGE"; return; fi
    if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/jpackage" ]]; then echo "$JAVA_HOME/bin/jpackage"; return; fi
    # The game targets Java 25, so take the toolchain's own JDK 25 when there is one.
    local cached
    cached="$(ls -d "$cache"/extract.cache/*jdk*25*/*/bin/jpackage 2>/dev/null | sort | tail -n 1 || true)"
    if [[ -n "$cached" ]]; then echo "$cached"; return; fi
    command -v jpackage || { echo "jpackage not found: install a JDK 25 or set JPACKAGE" >&2; exit 1; }
}

jpackage="$(find_jpackage)"
echo "Using $jpackage ($("$jpackage" --version))"

echo "Building the game..."
# Packaging the executable jar is what writes the module jars (build/tasks/_<module>_jarJvm); the
# executable jar itself is not used, see below.
"${kotlin[@]}" package -m game-client -f executable-jar

# Every runtime library jar, named by group as well: Compose 1.12 has artifacts with the same file
# name in two groups (androidx.compose.runtime:runtime-desktop and its empty JetBrains redirect),
# which is why the toolchain's executable jar loses the real ones.
input="build/package-input"
rm -rf "$input" "$dist/$name"
mkdir -p "$input" "$dist"
"${kotlin[@]}" show dependencies -m game-client --scope runtime -p jvm \
    | grep -oE '[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+:[0-9][A-Za-z0-9_.+-]*( -> [0-9][A-Za-z0-9_.+-]*)?( \(\*\))?$' \
    | sed -E 's/ \(\*\)$//; s/^([^:]+):([^:]+):[^ ]+ -> (.*)$/\1:\2:\3/' \
    | sort -u \
    | while IFS=: read -r group artifact version; do
        jar="$cache/.m2.cache/${group//.//}/$artifact/$version/$artifact-$version.jar"
        # Umbrella artifacts have no jar of their own, only metadata.
        if [[ -f "$jar" ]]; then cp "$jar" "$input/$group.$artifact-$version.jar"; fi
    done
for module in game-client game-content game-protocol game-server; do
    cp "build/tasks/_${module}_jarJvm/$module-jvm.jar" "$input/"
done
echo "$(ls "$input" | wc -l) jars on the class path"

common=(
    --name "$name"
    --app-version "$version"
    --vendor "TankArena"
    --description "Tank Arena, rewritten in Kotlin"
    --input "$input"
    --main-jar game-client-jvm.jar
    --main-class com.tankarena.app.desktop.MainKt
    --icon packaging/tankarena.png
    # Only the Java modules the game uses (jdeps --print-module-deps over the jars).
    --add-modules java.base,java.desktop,java.instrument,java.management,jdk.unsupported
    # Skia and the audio backend load native code.
    --java-options "--enable-native-access=ALL-UNNAMED"
    --dest "$dist"
)

echo "Building the app image..."
"$jpackage" --type app-image "${common[@]}"

arch="$(uname -m)"
archive="$dist/$name-$version-linux-$arch.tar.gz"
tar -C "$dist" -czf "$archive" "$name"
echo "Wrote $archive (run $name/bin/$name)"

for type in "$@"; do
    case "$type" in
        appimage)
            echo "Building the AppImage..."
            appdir="build/AppDir"
            rm -rf "$appdir"
            mkdir -p "$appdir"
            cp -r "$dist/$name" "$appdir/usr"
            cp packaging/linux/AppRun packaging/linux/tankarena.desktop "$appdir/"
            cp packaging/tankarena.png "$appdir/tankarena.png"
            chmod +x "$appdir/AppRun"
            # No FUSE in a container: appimagetool unpacks itself instead of mounting.
            ARCH="$arch" APPIMAGE_EXTRACT_AND_RUN=1 appimagetool --no-appstream "$appdir" "$dist/$name-$version-$arch.AppImage"
            ;;
        *)
            echo "Building the $type installer..."
            "$jpackage" --type "$type" "${common[@]}" --linux-shortcut --linux-menu-group Game
            ;;
    esac
done
