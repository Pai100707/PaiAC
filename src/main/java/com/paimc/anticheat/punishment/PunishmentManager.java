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

    /** Pseudo check id of the combined VL (sum of every check). */
    public static final String TOTAL_ID = "total";

    private final PaiAC plugin;
    private volatile boolean enforce;
    private volatile boolean autoBan;
    private volatile long cancelMs = 1500;
    private volatile long guardMs = 3000;
    private volatile List<Threshold> defaults = List.of();
    private volatile Map<String, List<Threshold>> perCheck = Map.of();
    private volatile List<Threshold> totalThresholds = List.of();

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
        totalThresholds = parseList(c.getMapList("total-thresholds"), "total-thresholds");

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

    /**
     * Evaluates the thresholds of the check that just flagged AND the combined (total) thresholds.
     * Non-removal actions run immediately. Among all removal actions (KICK / TEMPBAN / BAN) crossed
     * together only the most severe one runs, so a KICK can never swallow a TEMPBAN or BAN.
     */
    public void evaluate(PlayerState s, String flaggedCheck, double checkVl) {
        Pending best = scan(s, flaggedCheck, checkVl, flaggedCheck, null);
        best = scan(s, TOTAL_ID, s.totalVl(), flaggedCheck, best);
        if (best == null) return;
        if (System.currentTimeMillis() - s.lastPunishMs < guardMs) {
            s.firedPunishments.remove(best.key()); // too soon after another removal: retry on the next flag
            return;
        }
        execute(s, best.owner(), best.t(), best.vl(), flaggedCheck);
    }

    private record Pending(Threshold t, String key, String owner, double vl) {}

    private Pending scan(PlayerState s, String owner, double vl, String flaggedCheck, Pending best) {
        List<Threshold> list = listFor(owner);
        for (int i = 0; i < list.size(); i++) {
            Threshold t = list.get(i);
            if (!t.enabled() || vl < t.vl()) continue;
            String key = key(owner, i, t, vl);
            if (!s.firedPunishments.add(key)) continue;
            if (isRemoval(t.action())) {
                if (best == null || severity(t) > severity(best.t())) best = new Pending(t, key, owner, vl);
            } else {
                execute(s, owner, t, vl, flaggedCheck);
            }
        }
        return best;
    }

    private static String key(String owner, int index, Threshold t, double vl) {
        int bucket = t.repeatEvery() > 0 ? (int) ((vl - t.vl()) / t.repeatEvery()) : 0;
        return owner + "#" + index + "#" + bucket;
    }

    private List<Threshold> listFor(String owner) {
        return TOTAL_ID.equals(owner) ? totalThresholds : perCheck.getOrDefault(owner, defaults);
    }

    private static boolean isRemoval(PunishAction a) {
        return a == PunishAction.KICK || a.isBan();
    }

    private static long severity(Threshold t) {
        return switch (t.action()) {
            case KICK -> 1L;
            case TEMPBAN -> 10L + Math.max(0, t.durationMs()) / 1000L;
            case BAN -> Long.MAX_VALUE / 2;
            default -> 0L;
        };
    }

    /** After a rejoin: do not replay alerts/setbacks already triggered; removal thresholds stay armed. */
    public void seed(PlayerState s) {
        for (Map.Entry<String, Double> e : s.vlSnapshot().entrySet()) seedOwner(s, e.getKey(), e.getValue());
        seedOwner(s, TOTAL_ID, s.totalVl());
    }

    private void seedOwner(PlayerState s, String owner, double vl) {
        List<Threshold> list = listFor(owner);
        for (int i = 0; i < list.size(); i++) {
            Threshold t = list.get(i);
            if (!t.enabled() || isRemoval(t.action()) || vl < t.vl()) continue;
            s.firedPunishments.add(key(owner, i, t, vl));
        }
    }

    /** Re-arms thresholds of one owner (a check id or TOTAL_ID) once its VL dropped below them. */
    public void onDecay(PlayerState s, String owner, double newVl) {
        if (s.firedPunishments.isEmpty()) return;
        List<Threshold> list = listFor(owner);
        for (int i = 0; i < list.size(); i++) {
            if (newVl < list.get(i).vl()) {
                String prefix = owner + "#" + i + "#";
                s.firedPunishments.removeIf(k -> k.startsWith(prefix));
            }
        }
    }

    private void execute(PlayerState s, String owner, Threshold t, double vl, String flaggedCheck) {
        long now = System.currentTimeMillis();
        PunishAction a = t.action();
        String reason = t.reason().replace("{check}", owner).replace("{last-check}", flaggedCheck)
                .replace("{vl}", String.format(Locale.ROOT, "%.1f", vl));
        boolean banAllowed = !a.isBan() || autoBan;
        boolean real = (!a.needsEnforcement() || enforce) && banAllowed;
        String checkId = owner;

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
                s.cancelActions(TOTAL_ID.equals(owner) ? flaggedCheck : owner, now + cancelMs);
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
