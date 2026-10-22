# Architecture Decision Records

Each ADR records one decision as it stands in the code: its context, the decision and its
consequences. A changed decision is revised in place, with a new date in its status line, or replaced
by a newer ADR. How the decisions came about, step by step, is in the [history](../history.md); what is
still to be built is in the [roadmap](../roadmap.md).

ADR numbers are never reused, because code comments cite them. Withdrawn ADRs are deleted and listed
at the end.

## Foundations

| ADR | Decision |
|---|---|
| [0001](0001-kotlin-and-compose-multiplatform.md) | Kotlin and Compose Multiplatform |
| [0019](0019-plain-kotlin-no-game-engine.md) | Plain Kotlin and the game's own renderer, no game engine |
| [0025](0025-multiplatform-modules-and-android.md) | Multiplatform module layout and an Android app |
| [0016](0016-kotlin-toolchain-build.md) | Build with the Kotlin toolchain |

## Simulation

| ADR | Decision |
|---|---|
| [0002](0002-fixed-step-100hz.md) | 100 ticks per second, one game step every third tick |
| [0005](0005-network-ready-input-protocol-boundary.md) | Input frames and server frames as the only seam |
| [0007](0007-authoritative-server.md) | Authoritative simulation, render-only client |
| [0010](0010-port-the-original-rules.md) | Port the original's rules from the C source (Classic) |
| [0011](0011-structure-grid-and-pixel-masks.md) | Structures as a tile grid with pixel masks |
| [0012](0012-port-the-computer-ai.md) | Port the original computer AI |
| [0014](0014-simulation-thread-and-recordings.md) | Simulation thread, determinism, recorded playthroughs |

## Content and player data

| ADR | Decision |
|---|---|
| [0003](0003-legacy-map-import.md) | The native map format is the source of truth |
| [0015](0015-ship-the-original-69-maps.md) | Ship the 69 maps of the original release |
| [0022](0022-native-map-format.md) | A native, readable map format with `.MAP` converters |
| [0021](0021-editor-in-the-game.md) | Map editor inside the game |
| [0020](0020-user-data-as-json.md) | Player data as JSON in the player's folders |

## Presentation, sound and controls

| ADR | Decision |
|---|---|
| [0013](0013-original-presentation.md) | Reproduce the original's screen, fonts and palette effects (Classic) |
| [0018](0018-audio-and-music.md) | Original samples through a software mixer, MIDI music |
| [0026](0026-input-and-gamepads.md) | Input layer, remapping and gamepads |
| [0029](0029-extended-controls.md) | Extended controls: keyboard and mouse, twin-stick gamepads |

## Rulesets, players and networking

| ADR | Decision |
|---|---|
| [0023](0023-classic-and-extended-rulesets.md) | Two rulesets: Classic and Extended ("Next Kombat") |
| [0027](0027-four-players-two-teams.md) | Four players means two teams of two |
| [0024](0024-networking.md) | Networked play: an authoritative host sending frames |
| [0028](0028-internet-relay-and-lobby.md) | Internet play through a relay and lobby server |

## Delivery

| ADR | Decision |
|---|---|
| [0017](0017-desktop-packaging.md) | Self-contained desktop packages built in containers |

## Withdrawn

Replaced by later decisions and deleted on 2026-10-22; their story is in the
[history](../history.md#withdrawn-decisions).

| ADR | Was | Replaced by |
|---|---|---|
| 0004 | The editor as a separate application | [0021](0021-editor-in-the-game.md) |
| 0006 | Six JVM-only modules | [0025](0025-multiplatform-modules-and-android.md) |
| 0008 | A game engine's scene JSON plus a sidecar as the map format | [0019](0019-plain-kotlin-no-game-engine.md), [0022](0022-native-map-format.md) |
| 0009 | The editor built on a generic scene editor | [0021](0021-editor-in-the-game.md) |
