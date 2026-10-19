# ADR 0018: Audio — Original Samples through a Software Mixer, MIDI Music

- **Status:** Accepted (2026-09-25 sound, commit `76c20a6`; 2026-10-18 mixer and music, commits
  `e969636` and `ee705c3`).

## Context

The original plays 29 samples from `DATA/SOUND.DAT` with distance volume, stereo pan and a random
pitch (900–1100), at most 32 at a time (`src/hardware/sound.c`), and MIDI music: `TUNE.MID`, or
`BELVED.MID` on maps of background 3, at 30 % volume during play.

The first desktop backend opened a `javax.sound` `Clip` per sound; opening a clip blocked for up to
hundreds of milliseconds and stalled the game.

## Decision

- **Samples:** the 29 original sounds are bundled as WAVs under `game-content/resources/sounds/`
  (copied from `unpacked/sound/`, which holds `SOUND.DAT`'s samples) and loaded from the class path.
- **One output line, mixed in software:** `DesktopAudioBackend` opens a single 16-bit stereo
  `SourceDataLine` and mixes up to 32 voices on an `audio-mixer` thread, with volume, pan and pitch.
- **Rules from the original:** `AudioManager` maps `GameEvent`s to sounds with the original's distance
  curve (full volume within 100 px, silent at 612 px), pan and pitch. Single-player sound is centred
  like the original; in two-player modes each player's sounds pan toward their window.
- **Music:** `MusicPlayer` plays `TUNE.MID` / `BELVED.MID` (bundled under
  `game-content/resources/music/`) through Java's software MIDI synthesizer, at 30 % during play, with
  the music and sound volumes from the game options.

## Consequences

- No stalls from audio; sound timing follows the simulation's events.
- The MIDI sound depends on the JDK's built-in synthesizer, so it does not sound like the original's
  sound card (an OPL/General MIDI difference that is accepted).
- `SOUND.DAT` is not extracted by a tool; if the WAVs are ever regenerated, an `extract-sounds`
  command should be added to `tools-mapconv` (see [assets.md](../assets.md)).
- Distance is measured from the window's camera, not from the player's tank as in the original — a
  small parity difference listed in [parity.md](../parity.md).
