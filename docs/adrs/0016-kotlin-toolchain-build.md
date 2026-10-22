# ADR 0016: Build with the Kotlin Toolchain

- **Status:** Accepted (2026-10-07). Replaces Gradle (until 2026-04) and Amper 0.10.0.

## Context

The project moved from Gradle to Amper in 2026-04 for its small, declarative module files. Amper's
command-line tool was renamed the Kotlin toolchain (`kotlin-cli`) and continued from version 0.11;
`amper-cli` stops at 0.10.0.

## Decision

- Build with the Kotlin toolchain **0.13.0** through the `./kotlin` and `kotlin.bat` wrappers.
  `./kotlin update` moves the pin.
- Module files (`project.yaml`, `<module>/module.yaml`) do not pin Kotlin, Compose,
  kotlinx.serialization or the JDK; they follow the toolchain's defaults (for 0.13.0: Kotlin 2.4.20,
  Compose 1.12.1, kotlinx.serialization 1.11.0, JDK 25). Libraries the toolchain does not manage are
  pinned in the module files (material3, Ktor, Jamepad, activity-compose).

Commands and layout: [build.md](../build.md).

## Consequences

- Updating the toolchain updates the language, Compose and the JDK at once; that is a deliberate
  upgrade, followed by a full test run.
- The toolchain's `executable-jar` cannot be shipped for the game: Compose 1.12 publishes same-named
  jars in two groups, and the jar keeps only one of them. Packaging works around it
  ([ADR 0017](0017-desktop-packaging.md)). The relay has no Compose, so its executable jar is fine.
- No Gradle plugins are available (no Kover, no Compose `packageDistribution`); equivalents are
  scripted.
