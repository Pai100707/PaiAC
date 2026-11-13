# Compatibility

Compiled against Paper API 1.21.1 (oldest target). Version-fragile lookups (attributes, enchantments, entity types) are resolved by namespaced key / name so renames between 1.21.1 and 1.21.11 do not cause `NoSuchFieldError`.

| Platform | Status |
|---|---|
| Paper 1.21.1 – 1.21.11 | targeted, **untested** |
| Folia 1.21.x | scheduler path implemented, **untested**; `folia-supported: true` is declared |
| Spigot / Bukkit | main-thread scheduler path; Paper-only API is not called on this path, **untested** |
| Outside 1.21.1–1.21.11 | loads with a warning, behaviour unverified |

Known API assumptions to verify on first build/run: `Registry.ATTRIBUTE` key names (`generic.movement_speed` / `movement_speed`, `player.entity_interaction_range` / `entity_interaction_range`), `Player#getPing`, `Block#isPassable`, `Player#getScheduler` (Folia only).

Without a packet library: no sub-tick click timing, no packet ordering, no client on-ground flag, no raw rotation packets.
