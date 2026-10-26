#!/bin/bash
# Builds the Windows packages on the Windows buildx builder and puts them in build/dist/windows.
set -e
cd "$(dirname "$0")/.."

# The version from git (packaging/version.sh): the containers get no .git.
version="${VERSION:-$(packaging/version.sh)}"
docker buildx build --builder "${BUILDER:-win2k22}" -f packaging/windows.Dockerfile --platform=windows/amd64 \
    --build-arg "VERSION=$version" -o type=tar,dest=build/windows.tar .
mkdir -p build/dist/windows
tar -C build/dist/windows -xf build/windows.tar --wildcards 'TankArena-*'
rm build/windows.tar
ls -l build/dist/windows
