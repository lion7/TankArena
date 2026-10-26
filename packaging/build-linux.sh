#!/bin/bash
# Builds the Linux packages in a container and puts them in build/dist/linux.
set -e
cd "$(dirname "$0")/.."

# The version from git (packaging/version.sh): the containers get no .git.
version="${VERSION:-$(packaging/version.sh)}"
docker buildx build --builder "${BUILDER:-default}" -f packaging/linux.Dockerfile --platform=linux/amd64 --build-arg "VERSION=$version" \
    -o type=local,dest=build/dist/linux .
ls -l build/dist/linux
