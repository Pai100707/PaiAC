package com.paimc.anticheat.punishment;

import com.paimc.anticheat.PaiAC;
import com.paimc.anticheat.player.PlayerState;
import com.paimc.anticheat.storage.BanEntry;
import com.paimc.anticheat.storage.PunishmentRecord;
import com.paimc.anticheat.util.Colors;
import com.paimc.anticheat.util.Durations;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Threshold-driven punishments. DETECTION_ONLY (default) never touches a player: destructive actions are
 * recorded as dry-runs so staff can calibrate thresholds before switching to ENFORCE.
 */
public final class PunishmentManager {
    public record Threshold(double vl, PunishAction action, boolean enabled, double repeatEvery,
                            long durationMs, String reason) {}

    private final PaiAC plugin;
    private volatile boolean enforce;
    private volatile boolean autoBan;
    private volatile long cancelMs = 1500;
    private volatile long guardMs = 3000;
    private volatile List<Threshold> defaults = List.of();
    private volatile Map<String, List<Threshold>> perCheck = Map.of();

    public PunishmentManager(PaiAC plugin) {
        this.plugin = plugin;
    }

    public boolean enforcing() { return enforce; }

    public boolean autoBanEnabled() { return autoBan; }

    public void reload() {
        YamlConfiguration c = plugin.config().punishments();
        String mode = c.getString("enforcement-mode", "DETECTION_ONLY").toUpperCase(Locale.ROOT);
        if (!mode.equals("DETECTION_ONLY") && !mode.equals("ENFORCE")) {
            plugin.config().warn("punishments.yml: enforcement-mode '" + mode + "' invalid, using DETECTION_ONLY");
            mode = "DETECTION_ONLY";
        }
        enforce = mode.equals("ENFORCE");
        autoBan = c.getBoolean("auto-ban-enabled", false);
        cancelMs = Math.max(100, c.getLong("cancel-duration-ms", 1500));
        guardMs = Math.max(0, c.getLong("duplicate-guard-ms", 3000));
        defaults = parseList(c.getMapList("default-thresholds"), "default-thresholds");

        Map<String, List<Threshold>> map = new HashMap<>();
        ConfigurationSection cs = c.getConfigurationSection("checks");
        if (cs != null) {
            for (String id : cs.getKeys(false)) {
                map.put(id, parseList(cs.getMapList(id + ".thresholds"), "checks." + id));
            }
        }
        perCheck = map;
    }

    private List<Threshold> parseList(List<Map<?, ?>> raw, String where) {
        List<Threshold> out = new ArrayList<>();
        int i = 0;
        for (Map<?, ?> m : raw) {
            i++;
            Object vlO = m.get("vl");
            PunishAction a = PunishAction.parse(String.valueOf(m.get("action")));
            if (!(vlO instanceof Number) || a == null || ((Number) vlO).doubleValue() <= 0) {
                plugin.config().warn("punishments.yml " + where + " entry #" + i + " invalid (needs vl > 0 and a valid action), skipped.");
                continue;
            }
            boolean enabled = !(m.get("enabled") instanceof Boolean b) || b;
            double repeat = m.get("repeat-every") instanceof Number n ? Math.max(0, n.doubleValue()) : 0;
            long dur = -1;
            if (a == PunishAction.TEMPBAN) {
                try {
                    dur = Durations.parse(String.valueOf(m.get("duration")));
                } catch (IllegalArgumentException ex) {
                    plugin.config().warn("punishments.yml " + where + " entry #" + i + ": bad duration, entry skipped.");
                    continue;
                }
            }
            Object r = m.get("reason");
            out.add(new Threshold(((Number) vlO).doubleValue(), a, enabled, repeat, dur,
                    r == null ? "Suspicious behaviour detected ({check})" : String.valueOf(r)));
        }
        return out;
    }

    public void evaluate(PlayerState s, String checkId, double vlAfter) {
        List<Threshold> list = perCheck.getOrDefault(checkId, defaults);
        for (int i = 0; i < list.size(); i++) {
            Threshold t = list.get(i);
            if (!t.enabled() || vlAfter < t.vl()) continue;
            int bucket = t.repeatEvery() > 0 ? (int) ((vlAfter - t.vl()) / t.repeatEvery()) : 0;
            String key = checkId + "#" + i + "#" + bucket;
            if (!s.firedPunishments.add(key)) continue;
            execute(s, checkId, t, vlAfter);
        }
    }

    /** Re-arms thresholds once VL decayed below them. */
    public void onDecay(PlayerState s, String checkId, double newVl) {
        if (s.firedPunishments.isEmpty()) return;
        List<Threshold> list = perCheck.getOrDefault(checkId, defaults);
        for (int i = 0; i < list.size(); i++) {
            if (newVl < list.get(i).vl()) {
                String prefix = checkId + "#" + i + "#";
                s.firedPunishments.removeIf(k -> k.startsWith(prefix));
            }
        }
    }

    private void execute(PlayerState s, String checkId, Threshold t, double vl) {
        long now = System.currentTimeMillis();
        PunishAction a = t.action();
        String reason = t.reason().replace("{check}", checkId).replace("{vl}", String.format(Locale.ROOT, "%.1f", vl));
        boolean banAllowed = !a.isBan() || autoBan;
        boolean real = (!a.needsEnforcement() || enforce) && banAllowed;

        if (a.needsEnforcement() && real && (a == PunishAction.KICK || a.isBan())
                && now - s.lastPunishMs < guardMs) {
            return; // a removal just happened, avoid double punishment
        }

        long expires = a == PunishAction.TEMPBAN ? now + t.durationMs() : -1;
        if (!(real && a.isBan())) { // real bans are recorded by applyBan()
            plugin.storage().insertPunishment(new PunishmentRecord(now, s.uuid, s.name, checkId, a.name(), reason, vl, expires, !real, "PaiAC"));
        }

        if (!real) {
            String why = !enforce ? "DETECTION_ONLY" : "auto-ban-enabled is false";
            plugin.alerts().notice("&e[dry-run: " + why + "] &fwould " + a + " &f" + s.name + " &7(" + checkId + ", vl " + String.format(Locale.ROOT, "%.1f", vl) + ")");
            return;
        }

        Player p = s.player();
        switch (a) {
            case ALERT -> plugin.alerts().notice("&f" + s.name + " &7reached threshold &c" + checkId + " &7vl " + String.format(Locale.ROOT, "%.1f", vl));
            case LOG -> {
                s.verboseUntilMs = now + 600_000L;
                plugin.alerts().notice("&7Verbose evidence enabled for &f" + s.name + " &7(10 min)");
            }
            case CANCEL_ACTION -> {
                s.cancelActions(checkId, now + cancelMs);
                plugin.alerts().notice("&7Cancelling &f" + checkId + " &7actions of &f" + s.name + " &7for " + cancelMs + "ms");
            }
            case SETBACK -> {
                Location safe = s.safeLocation;
                if (p != null && safe != null && safe.getWorld() != null && safe.getWorld().equals(p.getWorld())) {
                    plugin.scheduler().teleport(p, safe.clone());
                    plugin.alerts().notice("&7Setback &f" + s.name + " &7(" + checkId + ")");
                }
            }
            case KICK -> {
                s.lastPunishMs = now;
                kick(p, plugin.config().msg("kick-screen", "&cRemoved by PaiAC.\n&7Reason: &f{reason}").replace("{reason}", reason));
                plugin.alerts().notice("&cKicked &f" + s.name + " &7(" + reason + ")");
            }
            case TEMPBAN, BAN -> {
                s.lastPunishMs = now;
                applyBan(s.uuid, s.name, reason, a == PunishAction.BAN ? -1 : t.durationMs(), "PaiAC", p);
            }
        }
    }

    /** Used by automatic punishments and by /paiac ban (explicit staff command, ignores enforcement mode). */
    public void applyBan(UUID id, String name, String reason, long durationMs, String by, Player online) {
        long now = System.currentTimeMillis();
        BanEntry b = new BanEntry(id, name, reason, now, durationMs < 0 ? -1 : now + durationMs, by);
        plugin.bans().ban(b);
        plugin.storage().insertPunishment(new PunishmentRecord(now, id, name, "manual", durationMs < 0 ? "BAN" : "TEMPBAN",
                reason, 0, b.expires(), false, by));
        plugin.alerts().notice("&cBanned &f" + name + " &7(" + (durationMs < 0 ? "permanent" : Durations.format(durationMs)) + ", " + reason + ")");
        if (online != null) kick(online, plugin.bans().screen(b));
    }

    public void kick(Player p, String message) {
        if (p == null) return;
        String colored = Colors.color(message);
        plugin.scheduler().runForPlayer(p, () -> p.kickPlayer(colored));
    }
}
