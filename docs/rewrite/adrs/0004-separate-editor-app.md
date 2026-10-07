# ADR 0004: Editor as a Separate App

- **Status:** Accepted (revised 2026-04 — superseded in part by [ADR 0009](0009-editor-wraps-kubriko-scene-editor.md)).
- **Decision:** the editor ships as a dedicated desktop app (`:game-editor`), distinct from the runtime client (`:game-client`).
- **Consequence:** editor scope is isolated from the shipping runtime; both apps share `:game-content`, `:game-protocol`, and the server's actor classes via `Editable<T>`.

The original 2026 wording said the editor would share "the same content and simulation modules". After the module collapse there is no `:game-sim`; the simulation lives in `:game-server`. The editor depends on `:game-server` only to reuse server actor classes and their `Editable<T>` state — it does not run any server managers. See [ADR 0009](0009-editor-wraps-kubriko-scene-editor.md) for the scene-editor embedding decision.
