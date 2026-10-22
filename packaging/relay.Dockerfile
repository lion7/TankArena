# The relay and lobby for internet play (ADR 0028) as a container image; see packaging/build-relay.sh.
FROM eclipse-temurin:25-jdk-noble@sha256:ba0f0e1a33864105e1d454e88105c617ab40f71062db1f306bf58be32a3f8e5e AS build
# The kotlin script downloads the toolchain with curl.
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
WORKDIR /workspace
COPY . .
# The relay has no Compose, so the toolchain's executable jar works here (unlike for the desktop app).
RUN --mount=type=cache,target=/root/.cache/JetBrains/Kotlin \
    ./kotlin package -m app-relay -f executable-jar \
    && cp build/tasks/_app-relay_executableJarJvm/app-relay-jvm-executable.jar /relay.jar

FROM eclipse-temurin:25-jre-noble@sha256:3f2223fe04b750aa5a8179e29b5c9a1ddf7665fc9bb08bfe11ad01d143bc13a5
LABEL org.opencontainers.image.source="https://github.com/lion7/TankArena" \
      org.opencontainers.image.title="Tank Arena relay" \
      org.opencontainers.image.description="The relay and lobby for Tank Arena's internet play" \
      org.opencontainers.image.licenses="GPL-3.0-only"
COPY --from=build /relay.jar /app/relay.jar
# The image's own unprivileged user (ubuntu), by number so Kubernetes can check runAsNonRoot.
USER 1000
EXPOSE 47310
ENTRYPOINT ["java", "-jar", "/app/relay.jar"]
# Meant to run behind a TLS proxy (wss://); without one, run it with --port 47310 instead.
CMD ["--behind-proxy"]
