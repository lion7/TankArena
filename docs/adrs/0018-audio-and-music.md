# ADR 0018: Original Samples through a Software Mixer, MIDI Music

- **Status:** Accepted (sound 2026-09-25; mixer and music 2026-10-18).

## Context

The original plays 29 samples from `original/DATA/SOUND.DAT` with distance volume, stereo pan and a
random pitch, at most 32 at a time (`original/src/hardware/sound.c`), and MIDI music: `TUNE.MID`, or
`BELVED.MID` on maps of background 3, at 30 % volume during play.

The first desktop backend opened a `javax.sound` `Clip` per sound; opening a clip blocked for up to
hundreds of milliseconds and stalled the game.

## Decision

- **Samples:** the 29 original sounds are bundled as WAVs in `game-content/resources/sounds/` and
  loaded from the class path.
- **One output, mixed in software:** `MixerAudioBackend` (`:game-ui`, shared by every platform) mixes
  up to 32 voices with volume, pan and pitch into one 16-bit stereo stream, written to a `PcmOutput`:
  a `javax.sound` `SourceDataLine` on the desktop, an `AudioTrack` on Android.
- **Rules from the original:** `AudioManager` turns `GameEvent`s into sounds as
  `create_sound_absolute()` does: the distance to the player's vehicle (the nearer of two players',
  612 without one) sets the volume (full nearer than 100 px, silent from 612 px, nothing at 32 or
  below), two players' distances set the pan, and the pitch varies 900..1099.
- **Music:** `TUNE.MID` and `BELVED.MID` (`game-content/resources/music/`) play through Java's software
  MIDI synthesizer on the desktop (`MusicPlayer`) and through `MediaPlayer` on Android, at 30 % during
  play, with the music and sound volumes from the game options.

## Consequences

- No stalls from audio; sound timing follows the simulation's events.
- The music depends on the platform's MIDI synthesizer, so it does not sound like the original's sound
  card. That is accepted ([parity.md](../parity.md#intentional-deviations)).
- The WAVs were extracted from `SOUND.DAT` by hand; no `tools-mapconv` command regenerates them yet
  ([assets.md](../assets.md#open-asset-work)).
