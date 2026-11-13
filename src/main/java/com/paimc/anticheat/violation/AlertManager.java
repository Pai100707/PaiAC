package com.paimc.anticheat.violation;

import com.paimc.anticheat.PaiAC;
import com.paimc.anticheat.player.PlayerState;
import com.paimc.anticheat.util.Colors;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.concurrent.ConcurrentHashMap;

public final class AlertManager {
    private final PaiAC plugin;
    private final ConcurrentHashMap<String, Long> lastAlert = new ConcurrentHashMap<>();

    public AlertManager(PaiAC plugin) {
        this.plugin = plugin;
    }

    public void alert(PlayerState s, String checkId, double observed, double expected, double vl, int ping, double tps, String details) {
        long now = System.currentTimeMillis();
        String key = s.uuid + ":" + checkId;
        Long prev = lastAlert.get(key);
        if (prev != null && now - prev < plugin.settings().alertCooldownMs) return;
        lastAlert.put(key, now);

        String template = plugin.config().msg("alert",
                "{prefix}&f{player} &7failed &c{check} &7(vl &f{vl}&7) &8| &7obs &f{observed} &7exp &f{expected} &8| &7{ping}ms {tps}tps");
        String msg = apply(template, s.name, checkId, vl, observed, expected, ping, tps)
                .replace("{total}", String.format("%.1f", s.totalVl()));
        plugin.getLogger().info(org.bukkit.ChatColor.stripColor(Colors.color(msg)) + " | total vl " + String.format("%.1f", s.totalVl()) + " | " + details);
        sendToStaff(msg);
    }

    /** Free-form staff notice (threshold actions, dry-run notices...). */
    public void notice(String message) {
        String msg = Colors.color(plugin.config().msg("prefix", "&8[&bPaiAC&8] &7") + message);
        plugin.getLogger().info(org.bukkit.ChatColor.stripColor(msg));
        sendToStaffRaw(msg);
    }

    private String apply(String t, String player, String check, double vl, double obs, double exp, int ping, double tps) {
        return t.replace("{prefix}", plugin.config().msg("prefix", "&8[&bPaiAC&8] &7"))
                .replace("{player}", player)
                .replace("{check}", check)
                .replace("{vl}", String.format("%.1f", vl))
                .replace("{observed}", Colors.fmt(obs))
                .replace("{expected}", Colors.fmt(exp))
                .replace("{ping}", String.valueOf(ping))
                .replace("{tps}", String.format("%.1f", tps));
    }

    private void sendToStaff(String template) {
        sendToStaffRaw(Colors.color(template));
    }

    private void sendToStaffRaw(String colored) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerState st = plugin.states().get(p.getUniqueId());
            if (st != null && st.alertsEnabled && p.hasPermission("paiac.alerts")) p.sendMessage(colored);
        }
    }

    public void forget(PlayerState s) {
        lastAlert.keySet().removeIf(k -> k.startsWith(s.uuid.toString()));
    }
}
