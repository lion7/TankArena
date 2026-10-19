# Builds the Windows packages (zip and MSI) on a Windows builder; see packaging/build-windows.sh.
FROM eclipse-temurin:25-jdk-windowsservercore-ltsc2022@sha256:6cad92d6e08735fbe74659b8b923ce53606d2469383534a7381b8b21d04d666b AS base
SHELL ["cmd", "/S", "/C"]
RUN curl -fLO https://github.com/wixtoolset/wix3/releases/download/wix314rtm/wix314-binaries.zip
RUN mkdir C:\\wix && tar -C C:\\wix -xf wix314-binaries.zip && del wix314-binaries.zip
# The image's PATH is only C:\Windows\System32;C:\Windows: add PowerShell (the script and kotlin.bat
# need it) and WiX. The JDK is found through JAVA_HOME, which the image sets machine-wide.
ENV PATH="C:\\Windows\\System32;C:\\Windows;C:\\Windows\\System32\\WindowsPowerShell\\v1.0;C:\\wix"

FROM base AS build
WORKDIR C:/workspace
ARG VERSION=1.0.0
COPY . .
RUN powershell -NoProfile -ExecutionPolicy Bypass -File packaging\\package.ps1 msi
# One source for the package stage: copying several from this layer mounts it twice at once, which the
# Windows builder refuses ("The process cannot access the file because it is being used").
RUN mkdir build\\out && move build\\dist\\TankArena-*.msi build\\out && move build\\dist\\TankArena-*.zip build\\out

FROM mcr.microsoft.com/windows/nanoserver:ltsc2022 AS package
COPY --from=build C:/workspace/build/out/ C:/
