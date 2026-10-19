# ADR 0016: Build with the Kotlin Toolchain

- **Status:** Accepted (2026-10-07, commit `5f4f854`).
- **Supersedes:** Gradle (removed 2026-04) and then Amper 0.10.0.

## Context

The project moved from Gradle to Amper in 2026-04 for its smaller, declarative module files. Amper's
command-line tool was renamed the Kotlin toolchain (`kotlin-cli`) and continued from version 0.11;
`amper-cli` stops at 0.10.0.

## Decision

- Build with the Kotlin toolchain **0.13.0** through the `./kotlin` and `kotlin.bat` wrappers.
  `./kotlin update` moves the pin.
- Module files (`project.yaml`, `<module>/module.yaml`) do not pin Kotlin, Compose,
  kotlinx.serialization or the JDK; they follow the toolchain defaults (for 0.13.0: Kotlin 2.4.20,
  Compose 1.12.1, kotlinx.serialization 1.11.0, JDK 25). Library versions that the toolchain does not
  manage (Kubriko 0.7.3, material3 1.12.0-alpha03) are pinned explicitly.

Commands and layout: [build.md](../build.md).

## Consequences

- Updating the toolchain updates the language, Compose and the JDK at once; that is a deliberate
  upgrade step, followed by a full test run.
- The toolchain's `executable-jar` output cannot be shipped with Compose 1.12 (same-named jars from
  two groups collide); packaging works around it ([ADR 0017](0017-desktop-packaging.md)).
- No Gradle plugins are available (no Kover, no Compose `packageDistribution`); equivalents have to
  be scripted.
