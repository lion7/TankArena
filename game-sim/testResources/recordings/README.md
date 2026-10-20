# Recorded playthroughs

Every `.json` file here is a mission played in the desktop client and replayed by
`RecordedPlaythroughTest`: the headless server must end the mission exactly as it ended when it
was played (won or lost, reason, kills, goal counters and the tick of the result).

The client saves a recording of every finished mission to `~/.local/share/tankarena/recordings`
(or `$XDG_DATA_HOME/tankarena/recordings`). Copy the ones worth keeping into this directory.

A recording only replays under the gameplay rules it was played with. When a change alters how
the same inputs play out, `GAME_RULES_VERSION` in `MatchRecording.kt` is bumped and older
recordings fail with a request to play the mission again.
