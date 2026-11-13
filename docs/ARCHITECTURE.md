# Architecture

```
PaiAC (JavaPlugin)
 ├─ platform/   PlatformInfo (Folia/Paper/Spigot + MC version), SchedulerAdapter (Bukkit | Folia), TpsMonitor
 ├─ config/     ConfigManager (versioned migration + backup), Settings, CheckSettings (validated)
 ├─ player/     PlayerState, PlayerStateManager, MovementTracker -> MoveData (shared environment + exemptions)
 ├─ checks/     Check (buffer/threshold/cooldown), CheckManager (dispatch, timing, error isolation)
 │    combat/ movement/ crystal/ anchor/ inventory/ xray/(diagnostics only)
 ├─ violation/  ViolationManager (VL + lag gating), EvidenceManager, AlertManager
 ├─ punishment/ PunishmentManager (thresholds, dry-run), BanManager (own ban list)
 ├─ storage/    StorageService (SQLite, single writer thread, bounded queue)
 ├─ packet/     PacketAdapter + NoopPacketAdapter (no packet library is used)
 ├─ listener/   CheckListener (events -> checks), ConnectionListener (join/quit/ban)
 └─ commands/   /paiac
```

Flow: Bukkit event → `CheckListener` → `CheckManager.dispatch` → `Check.onXxx` → `fail()` (buffer ≥ threshold, evidence ≥ min-evidence, cooldown) →
`ViolationManager.flag` (bypass, TPS/ping gate, VL) → `EvidenceManager` + `AlertManager` + `PunishmentManager.evaluate`.

Threading: checks run on the thread that fires the event (region thread on Folia) and only touch that player's `PlayerState`. Shared maps are concurrent. VL decay/autosave run async and touch only `PlayerState` maps. Kick/setback/ban kicks go through `SchedulerAdapter.runForPlayer`/`teleport`. Folia-only classes are never loaded on other platforms.

Decision: no PacketEvents/ProtocolLib. Everything is derived from Bukkit events; the consequences (tick-quantised timing, no raw rotation/packet order) are listed by `/paiac compat`.
