# Builds the Linux packages (tar.gz and AppImage); see packaging/build-linux.sh.
FROM eclipse-temurin:25-jdk-noble@sha256:ba0f0e1a33864105e1d454e88105c617ab40f71062db1f306bf58be32a3f8e5e AS base
RUN apt-get update && apt-get install -y --no-install-recommends curl file binutils && rm -rf /var/lib/apt/lists/*
RUN curl -fL -o /usr/local/bin/appimagetool \
        https://github.com/AppImage/appimagetool/releases/download/1.9.1/appimagetool-x86_64.AppImage \
    && chmod +x /usr/local/bin/appimagetool

FROM base AS build
WORKDIR /workspace
ARG VERSION=0.0.0
COPY . .
RUN --mount=type=cache,target=/root/.cache/JetBrains/Kotlin \
    VERSION=${VERSION} packaging/package.sh appimage

FROM scratch AS package
COPY --from=build /workspace/build/dist/*.tar.gz /workspace/build/dist/*.AppImage /
