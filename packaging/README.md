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
