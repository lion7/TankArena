# Architecture Decision Records

Each ADR records one decision: its context, the decision and its consequences. A changed decision is
revised in place with a new date in its status line, or superseded by a new ADR that says so.

| ADR | Decision | Status |
|---|---|---|
| [0001](0001-desktop-first-compose-kubriko.md) | Desktop first; Compose for the shell, Kubriko for the playfield | Accepted |
| [0002](0002-fixed-step-100hz.md) | Fixed-step simulation at 100 ticks per second | Accepted |
| [0003](0003-legacy-map-import.md) | Legacy map import: `.MAP` or converted files as source of truth | **Open** |
| [0004](0004-separate-editor-app.md) | Editor as a separate app | Accepted |
| [0005](0005-network-ready-input-protocol-boundary.md) | Input frames and server frames as the only seam | Accepted |
| [0006](0006-module-collapse.md) | Six modules | Accepted |
| [0007](0007-authoritative-headless-kubriko-server.md) | Authoritative server, render-only client | Accepted (Kubriko part amended by 0019) |
| [0008](0008-kubriko-and-scene-json.md) | Published Kubriko; scene JSON plus sidecar as map format | Accepted (format to change with 0019) |
| [0009](0009-editor-wraps-kubriko-scene-editor.md) | Editor built on Kubriko's `SceneEditor`, extended to parity | Accepted |
| [0010](0010-port-the-original-rules.md) | Port the original's rules from the C source | Accepted |
| [0011](0011-structure-grid-and-pixel-masks.md) | Structures as a tile grid with pixel masks | Accepted |
| [0012](0012-port-the-computer-ai.md) | Port the original computer AI | Accepted |
| [0013](0013-original-presentation.md) | Reproduce the original's screen, fonts and palette effects | Accepted |
| [0014](0014-simulation-thread-and-recordings.md) | Simulation thread, determinism, recorded playthroughs | Accepted |
| [0015](0015-ship-the-original-69-maps.md) | Ship the 69 maps of the original release | Accepted |
| [0016](0016-kotlin-toolchain-build.md) | Build with the Kotlin toolchain | Accepted |
| [0017](0017-desktop-packaging.md) | Self-contained packages built in containers | Accepted |
| [0018](0018-audio-and-music.md) | Original samples through a software mixer; MIDI music | Accepted |
| [0019](0019-remove-kubriko-from-the-server.md) | Remove Kubriko from the server | **Proposed** |
| [0020](0020-user-data-as-json.md) | Player data as JSON in the user's folders | Accepted |
