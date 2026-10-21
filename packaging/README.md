# Packaging TankArena

The game ships as self-contained packages: the game together with its own trimmed Java runtime, so players need
nothing installed. Both platforms are built in containers, like the Typewise admin app.

```bash
packaging/build-linux.sh        # build/dist/linux: .tar.gz and .AppImage
packaging/build-windows.sh      # build/dist/windows: .zip and .msi, built on the win2k22 builder
VERSION=1.2.0 packaging/build-linux.sh
```

- `build-linux.sh` runs `linux.Dockerfile` on the local `default` buildx builder (`BUILDER=...` picks another).
  The Kotlin toolchain's downloads stay in a BuildKit cache mount between builds.
- `build-windows.sh` runs `windows.Dockerfile` on the remote Windows builder `win2k22` (Windows Server 2022 with
  BuildKit on `tcp://<vm>:1234`, see the Typewise `build-and-push-admin.sh`). The image adds WiX 3.14 for the MSI.

Players unpack the `.tar.gz` or `.zip` anywhere and start `TankArena/bin/TankArena` or `TankArena\TankArena.exe`,
run the AppImage directly, or install the MSI (Start menu and desktop shortcuts; a newer MSI upgrades an older
one). Settings, passwords and recordings go to the player's own folders, never into the game folder.

## Inside the containers

`package.sh` (Linux) and `package.ps1` (Windows) do the same steps and can also run outside a container:

1. `kotlin package -m game-client -f executable-jar` builds the game and writes the module jars.
2. The runtime class path comes from `kotlin show dependencies --scope runtime`. Each library jar is copied from
   the toolchain's Maven cache under a name that includes its group.
3. `jpackage` from a JDK 25 bundles the jars with a runtime made of the five Java modules the game uses
   (`java.base`, `java.desktop`, `java.instrument`, `java.management`, `jdk.unsupported`, found with
   `jdeps --print-module-deps`), then packs the result (`appimagetool` for the AppImage, WiX for the MSI).

The executable jar itself is not shipped. Compose 1.12 publishes artifacts with the same file name in two
groups: `androidx.compose.runtime:runtime-desktop` holds the code, and `org.jetbrains.compose.runtime:runtime-desktop`
is an empty redirect to it. The executable jar stores both as `runtime-desktop-1.12.1.jar`, and the empty one
wins, so the game cannot start (`NoClassDefFoundError: androidx/compose/runtime/Composer`). The `lifecycle` and
`runtime-saveable` libraries collide the same way.

`jpackage` only builds for the system it runs on, and the game bundles that system's Skia library, which is why
Windows needs a Windows builder.

## Android

```bash
packaging/package-android.sh                   # build/dist/android/TankArena-<version>-debug.apk
packaging/package-android.sh release           # ...-release-debugkey.apk, shrunk by R8
packaging/package-android.sh bundle            # ...-release.aab for Google Play (unsigned)
packaging/package-android.sh debug --install   # and install it on the connected phone or emulator
```

The version comes from `versionName` in `app-android/module.yaml`. It runs on this machine, no container: it
needs the Android SDK in `~/Android/Sdk` (or `ANDROID_HOME`). The debug APK and the release APK can be installed
directly (`adb install -r`, or copied to a phone that allows apps from unknown sources); the release one is about
10 MB instead of 25.

Release signing is not set up yet. Without a keystore the release APK is signed with the Android debug key and
named `-release-debugkey`: fine for testing, not for a store. With one, it is signed properly:

```bash
KEYSTORE=~/keys/tankarena.jks KEY_ALIAS=tankarena KEYSTORE_PASSWORD=... packaging/package-android.sh release
```

(`KEY_PASSWORD` too if the key has its own password.) The bundle stays unsigned.

The toolchain's own outputs, which the script copies, are `build/tasks/_app-android_buildAndroidDebug/gradle-project-debug.apk`,
`build/tasks/_app-android_buildAndroidRelease/gradle-project-release-unsigned.apk` and
`build/tasks/_app-android_bundleAndroid/gradle-project-release.aab`. Because the Android build does not notice
changed resources or a changed manifest, the script removes the APK's task folder first.

R8 shrinks the release build with the rules in `app-android/proguard-rules.pro`; Ktor needs one of them.

