# Architecture Decision Records

Each ADR records one decision: its context, the decision and its consequences. A changed decision is
revised in place with a new date in its status line, or superseded by a newer ADR that says so.
Superseded ADRs are kept for the record. The order in which accepted-but-unbuilt decisions are
implemented is in the [roadmap](../roadmap.md).

| ADR | Decision | Status |
|---|---|---|
| [0001](0001-desktop-first-compose-kubriko.md) | Kotlin and Compose Multiplatform; desktop first, Android next | Accepted |
| [0002](0002-fixed-step-100hz.md) | 100 ticks per second, legacy step every 3rd tick | Accepted |
| [0003](0003-legacy-map-import.md) | Native format is the source of truth; `.MAP` import and export stay | Decided |
| [0004](0004-separate-editor-app.md) | Editor as a separate app | Superseded by 0021 |
| [0005](0005-network-ready-input-protocol-boundary.md) | Input frames and server frames as the only seam | Accepted |
| [0006](0006-module-collapse.md) | Six modules | Accepted, to be replaced by 0025 |
| [0007](0007-authoritative-headless-kubriko-server.md) | Authoritative server, render-only client | Accepted (its Kubriko part is history) |
| [0008](0008-kubriko-and-scene-json.md) | Kubriko scene JSON plus sidecar as map format | Superseded by 0019 and 0022 |
| [0009](0009-editor-wraps-kubriko-scene-editor.md) | Editor built on Kubriko's `SceneEditor` | Superseded by 0021 |
| [0010](0010-port-the-original-rules.md) | Port the original's rules from the C source (Classic) | Accepted |
| [0011](0011-structure-grid-and-pixel-masks.md) | Structures as a tile grid with pixel masks | Accepted |
| [0012](0012-port-the-computer-ai.md) | Port the original computer AI | Accepted |
| [0013](0013-original-presentation.md) | Reproduce the original's screen, fonts and palette effects (Classic) | Accepted |
| [0014](0014-simulation-thread-and-recordings.md) | Simulation thread, determinism, recorded playthroughs | Accepted |
| [0015](0015-ship-the-original-69-maps.md) | Ship the 69 maps of the original release | Accepted |
| [0016](0016-kotlin-toolchain-build.md) | Build with the Kotlin toolchain | Accepted |
| [0017](0017-desktop-packaging.md) | Self-contained desktop packages built in containers | Accepted |
| [0018](0018-audio-and-music.md) | Original samples through a software mixer; MIDI music | Accepted |
| [0019](0019-plain-kotlin-no-kubriko.md) | Plain Kotlin everywhere, no Kubriko | Done (2026-10-19) |
| [0020](0020-user-data-as-json.md) | Player data as JSON in the user's folders | Accepted |
| [0021](0021-editor-in-the-game.md) | Map editor inside the game | Accepted, not built |
| [0022](0022-native-map-format.md) | Native readable map format with `.MAP` converters | Accepted, not built |
| [0023](0023-classic-and-extended-rulesets.md) | Two rulesets: Classic and Extended | Accepted, not built |
| [0024](0024-networking.md) | Networked play: authoritative server with snapshots | Accepted, not built |
| [0025](0025-multiplatform-modules-and-android.md) | Multiplatform module layout and Android | Accepted, not built |
| [0026](0026-input-and-gamepads.md) | Input layer, remapping and gamepads | Accepted, not built |
| [0027](0027-four-players-two-teams.md) | Four players means two teams of two | Accepted, not built |
