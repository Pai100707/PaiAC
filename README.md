# PaiAC 1.0.0

Modular anti-cheat plugin for Survival / Economy / Crystal & Anchor PvP servers.
Package `com.paimc.anticheat` · Java 21 · Gradle (Kotlin DSL) · Paper-first, Folia + Spigot/Bukkit paths included · MC 1.21.1 – 1.21.11 (targeted).

## STATUS – read this first

| Item | State |
|---|---|
| Source complete for Stage 1 (core) + checks listed below | yes |
| Compiled against the real Paper API | **NO – never built** (the authoring sandbox had no network / Gradle / Paper API). Only a javac syntax pass and the pure-Java utility tests were run. Expect a few compile errors on first build; send them back and they get fixed. |
| Unit tests | `DoubleRingTest`, `DurationsTest` (pure Java; equivalent assertions were executed, JUnit itself was not) |
| Tested on a real server (Paper / Folia / Spigot) | **NO** |
| Gradle wrapper | **not included** – generate it yourself (below) |
| `folia-supported: true` | declared; Folia path (region schedulers) is implemented but untested |

## Build (compile-only, no extra plugins needed)

```bash
# on a machine with Gradle 8.x and JDK 21 (Termux: pkg install openjdk-21 gradle)
gradle wrapper --gradle-version 8.10.2     # creates gradlew + gradle/wrapper/*
./gradlew build                            # -> build/libs/PaiAC-0.1.0-SNAPSHOT.jar
./gradlew test
```

Dependencies: only `paper-api:1.21.1-R0.1-SNAPSHOT` (`compileOnly`). No PacketEvents/ProtocolLib; SQLite JDBC is the driver already bundled in Paper/Spigot (loaded reflectively; without it PaiAC runs in memory-only mode).

## What is implemented

| Check id | Idea |
|---|---|
| `fly` | gravity prediction `(vy-0.08)*0.98`, one-sided, server-side ground detection |
| `speed` | cap from the player's movement-speed attribute (sprint/potions included) with air/ice/soul-speed multipliers |
| `boat-fly` | boats hovering/rising without support, surface-based speed caps |
| `reach` | eye→hitbox distance with ping + target-speed compensation |
| `kill-aura` | needs ≥2 signals on one hit: ray miss, target switch, aim snap, invalid state, no line of sight |
| `auto-clicker` | CPS cap (strong) + cadence regularity (weak), mining swings skipped |
| `crystal-macro` | place→hit cycle mean / regularity / instant-burst / rate, selectable **profiles**, **baseline-collection** calibration mode |
| `anchor-charge` `anchor-sequence` `anchor-switch` | place→glowstone→switch→explode timing |
| `auto-totem` | pop→off-hand refill time and consistency |
| `fast-xp` | experience bottles thrown faster than the vanilla rate; excess throws are cancelled |
| anti-xray | **not a Java check**: native Paper/Spigot anti-xray is only *read and reported*; example config in `xray/paper-world-defaults.example.yml` |

Infrastructure: per-check buffer/threshold/cooldown/min-evidence, VL with decay, evidence in SQLite (bounded async queue), alerts, threshold punishments (**DETECTION_ONLY by default**, dry-run logging, `auto-ban-enabled` second switch, duplicate-punishment guard), own ban list, TPS/ping lag gating, per-check exception isolation + auto-disable, config validation + versioned migration with backup, `/paiac` command.

## Safety defaults
* `enforcement-mode: DETECTION_ONLY` – nothing is ever done to players until you switch to `ENFORCE`.
* BAN/TEMPBAN additionally need `auto-ban-enabled: true`.
* Crystal/anchor/totem thresholds are **uncalibrated starting points**. Use `crystal.macro.baseline-collection: true` first, see `docs/CALIBRATION.md`.

## Commands & permissions
`/paiac help|status|checks|compat|profile|alerts|vl|reset|exempt|unexempt|ban|unban|bans|debug|reload`
Permissions: `paiac.command`, `paiac.admin`, `paiac.view`, `paiac.alerts`, `paiac.bypass`, `paiac.bypass.<check-id>`.

More: `docs/ARCHITECTURE.md`, `docs/COMPATIBILITY.md`, `docs/CALIBRATION.md`.
