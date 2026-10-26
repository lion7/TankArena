#!/usr/bin/env bash
# Prints TankArena's version, derived from git as the blocks and altus derive theirs:
#
#   packaging/version.sh          0.7.5: the last <major>.<minor> tag, then the commits since it;
#                                 0.7.5-dirty with uncommitted changes
#   packaging/version.sh --code   213: the number of commits, Android's versionCode (only grows on main)
#
# A new minor starts with a tag: git tag -a 0.8 -m "TankArena 0.8" && git push origin 0.8. Without a reachable
# tag (a shallow clone) the version is 0.0.<commits>, so it is still a number.
set -euo pipefail

cd "$(dirname "$0")/.."

count="$(git rev-list --count HEAD)"
if [[ "${1:-}" == "--code" ]]; then
    echo "$count"
    exit 0
fi

if described="$(git describe --long --dirty --tags --match '[0-9]*.[0-9]*' 2>/dev/null)"; then
    # "0.7-5-gb920d22-dirty" -> "0.7.5-dirty"
    dirty=""
    if [[ "$described" == *-dirty ]]; then
        dirty="-dirty"
        described="${described%-dirty}"
    fi
    echo "$(echo "${described%-g*}" | sed 's|-|.|g')$dirty"
else
    dirty=""
    [[ -n "$(git status --porcelain)" ]] && dirty="-dirty"
    echo "0.0.$count$dirty"
fi
