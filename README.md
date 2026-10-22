# Tank Arena: Next Kombat

A rewrite in Kotlin of **Tank Arena**, the 1997 DOS game by 3.14 Software: tanks, helicopters and
planes on top-down maps, missions against the computer, and two players together or against each
other. The rewrite runs on Linux, Windows, macOS and Android, and plays in two ways:

- **Classic**: the original game, as it was. Same rules, same menus in the original's 320×200 look,
  same keys. Recorded games of the original's missions replay to the same end.
- **Next Kombat** (the initials spell TANK): the same game with what has been added since. New
  menus, keyboard and mouse controls, gamepads, up to four players, network play, replays and a map
  editor.

The choice is made at the first start and can be changed in the settings.

## What Next Kombat adds

- **Controls:** player one plays with the keyboard and the mouse. WASD drives the way you press, the
  turret turns to the pointer, and the rocket marker follows it. Players two to four use gamepads as
  twin sticks. A second player on the keyboard is a setting.
- **Up to four players** on one screen, as two teams, on the original maps.
- **Network games:** host or join on the local network, over the internet by address, or by a game
  code through a relay for hosts behind a router. A dedicated server hosts games without a screen.
- **Android:** touch controls, gamepads, and a mouse on a tablet.
- **Replays** of every game, a **map editor** inside the game (also exporting the original's `.MAP`
  files), and a wider view on wide screens.

## Playing

Packages are built with the scripts in [`packaging/`](packaging/README.md):

```bash
packaging/build-linux.sh                  # Linux: .tar.gz and AppImage in build/dist/linux
packaging/build-windows.sh                # Windows: .zip and .msi (on a Windows builder)
packaging/package-android.sh --install    # Android: APK in build/dist/android, installed with adb
```

Or straight from the source:

```bash
./kotlin run -m app-desktop               # the game
```

In a game: ESC leaves, P pauses, F1–F10 take over a friendly tank, F11 changes the gamma, F12 shows
or hides the radar. The full list is under Help in the game.

### Network games

Next Kombat's home screen has *Network game*: host a game (the lobby shows the address and, with
a relay set, a game code) or join one from the list of games on the network, by address or by
code. A dedicated server and an internet relay run without a screen:

```bash
./kotlin run -m app-server -- --map flagisle --players 2   # hosts games; --relay <address> publishes them
./kotlin run -m app-relay                                  # game codes and tunnels, port 47310
```

## Building

The build uses the Kotlin toolchain wrapper `./kotlin` (`kotlin.bat` on Windows), which fetches
everything it needs, a JDK included. Android needs the Android SDK in `~/Android/Sdk` (or
`ANDROID_HOME`).

```bash
./kotlin build                            # all modules
./kotlin test                             # all tests
./kotlin build -m app-android             # the Android debug APK
```

More in [docs/build.md](docs/build.md).

## The repository

| Folder | Holds |
|---|---|
| `game-protocol`, `game-content`, `game-sim` | The rules and the simulation, the maps and the game's data |
| `game-net` | Network play: messages, server and lobby, client, relay |
| `game-ui` | The game itself: screens, rendering, sound, input, editor (Compose Multiplatform) |
| `app-desktop`, `app-android` | The desktop and Android apps around it |
| `app-server`, `app-relay` | The dedicated server and the internet relay |
| `tools-mapconv` | Converters from the original's files |
| `packaging` | Package scripts for Linux, Windows and Android |
| `docs` | How the rewrite is built: architecture, decisions (ADRs), roadmap |
| `original` | The original game: C sources, data, maps, its readme and documentation |

The original release lives on unchanged in [`original/`](original/). Its C source in
[`original/src/`](original/src/) is the reference for every rule, and the rewrite's code points to
it file by file (`original/src/game/mainloop.c` and so on). The original game is described in
[`original/docs/`](original/docs/) and its own readme is
[`original/readme.txt`](original/readme.txt).

## Documentation

- [Architecture](docs/architecture.md) and the [decisions (ADRs)](docs/adrs/README.md)
- [Roadmap](docs/roadmap.md): what is built and what is next
- [Parity](docs/parity.md): how closely Classic follows the original
- [Testing](docs/testing-strategy.md) and [performance](docs/performance.md)
- [Map format](docs/content-format.md) and [assets](docs/assets.md)

## Credits

Tank Arena was made by 3.14 Software in 1997: programming by Jorrit Rouwe, graphics by Dennis
Medema, music by Janne Leinonen. The original thanks D.J. Delorie (DJGPP), Charles Sandmann
(CWSDPMI) and Shawn Hargreaves (Allegro).

Released under the GNU General Public License version 3; see [LICENSE.txt](LICENSE.txt).
