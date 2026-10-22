#!/bin/bash
# Builds the relay's container image (relay.Dockerfile); PUSH=1 pushes it to GHCR (docker login ghcr.io first).
set -e
cd "$(dirname "$0")/.."

image="${IMAGE:-ghcr.io/lion7/tankarena/relay}"
version="${VERSION:-$(git rev-parse --short HEAD)}"
output=--load
[ "${PUSH:-0}" = 1 ] && output=--push
docker buildx build --builder "${BUILDER:-default}" -f packaging/relay.Dockerfile --platform=linux/amd64 \
    -t "$image:$version" -t "$image:latest" $output .
