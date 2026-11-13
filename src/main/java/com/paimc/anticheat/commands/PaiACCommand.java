package com.paimc.anticheat.commands;

import com.paimc.anticheat.PaiAC;
import com.paimc.anticheat.checks.Check;
import com.paimc.anticheat.checks.crystal.CrystalMacroCheck;
import com.paimc.anticheat.player.PlayerState;
import com.paimc.anticheat.storage.BanEntry;
import com.paimc.anticheat.util.Colors;
import com.paimc.anticheat.util.Durations;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class PaiACCommand implements TabExecutor {
    private static final List<String> SUBS = List.of("help", "status", "checks", "compat", "profile", "alerts", "vl", "reset",
            "exempt", "unexempt", "ban", "unban", "bans", "debug", "inspect", "reload");

    private final PaiAC plugin;

    public PaiACCommand(PaiAC plugin) {
        this.plugin = plugin;
    }

    private void send(CommandSender to, String msg) {
        to.sendMessage(Colors.color(plugin.config().msg("prefix", "&8[&bPaiAC&8] &7") + msg));
    }

    private boolean need(CommandSender s, String perm) {
        if (s.hasPermission(perm) || s.hasPermission("paiac.admin")) return true;
        send(s, "&cYou do not have permission (" + perm + ").");
        return false;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] a) {
        String sub = a.length == 0 ? "help" : a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help" -> help(sender);
            case "status" -> { if (need(sender, "paiac.view")) status(sender); }
            case "checks" -> { if (need(sender, "paiac.view")) checks(sender); }
            case "compat" -> { if (need(sender, "paiac.view")) compat(sender); }
            case "profile" -> { if (need(sender, "paiac.view")) profile(sender); }
            case "alerts" -> alerts(sender, a);
            case "vl" -> { if (need(sender, "paiac.view")) vl(sender, a); }
            case "reset" -> { if (need(sender, "paiac.admin")) reset(sender, a); }
            case "exempt" -> { if (need(sender, "paiac.admin")) exempt(sender, a, true); }
            case "unexempt" -> { if (need(sender, "paiac.admin")) exempt(sender, a, false); }
            case "ban" -> { if (need(sender, "paiac.admin")) ban(sender, a); }
            case "unban" -> { if (need(sender, "paiac.admin")) unban(sender, a); }
            case "bans" -> { if (need(sender, "paiac.admin")) bans(sender); }
            case "debug" -> { if (need(sender, "paiac.admin")) debug(sender); }
            case "inspect" -> { if (need(sender, "paiac.view")) inspect(sender, a); }
            case "reload" -> { if (need(sender, "paiac.admin")) reload(sender); }
            default -> send(sender, "&cUnknown subcommand. Try &f/paiac help");
        }
        return true;
    }

    private void help(CommandSender s) {
        send(s, "&fPaiAC commands:");
        for (String l : new String[]{
                "&b/paiac status &7- platform, mode, storage", "&b/paiac checks &7- checks, flags, timing",
                "&b/paiac compat &7- compatibility & limits", "&b/paiac profile &7- crystal profile / calibration state",
                "&b/paiac alerts [on|off] &7- toggle your alerts", "&b/paiac vl <player> &7- violation levels + recent evidence",
                "&b/paiac reset <player> [check] &7- clear VL", "&b/paiac exempt <player> [minutes] &7- temporary exemption",
                "&b/paiac unexempt <player>", "&b/paiac ban <player> <duration|perm> <reason> &7- PaiAC ban",
                "&b/paiac unban <player>", "&b/paiac bans", "&b/paiac debug &7- toggle debug logging", "&b/paiac inspect <player> &7- why a player is (not) being checked", "&b/paiac reload"}) {
            s.sendMessage(Colors.color(l));
        }
    }

    private void status(CommandSender s) {
        send(s, "&fPlatform: &7" + plugin.platform().describe() + (plugin.platform().inSupportedRange() ? "" : " &e(outside 1.21.1-1.21.11)"));
        send(s, "&fTPS est: &7" + String.format(Locale.ROOT, "%.1f", plugin.tps().tps()) + " &fTracked players: &7" + plugin.states().all().size() + "&8/&7" + Bukkit.getOnlinePlayers().size() + " online");
        send(s, "&fMode: &7" + (plugin.punishments().enforcing() ? "&cENFORCE" : "&aDETECTION_ONLY") + " &fauto-ban: &7" + plugin.punishments().autoBanEnabled());
        send(s, "&fStorage: &7" + (plugin.storage().available() ? "SQLite" : "memory only") + " &fdropped writes: &7" + plugin.storage().dropped());
        send(s, "&fPackets: &7" + plugin.packets().name());
        for (String l : plugin.xray().report()) send(s, "&fAnti-xray: &7" + l);
    }

    private void checks(CommandSender s) {
        for (Check c : plugin.checks().all()) {
            String state = c.faulted() ? "&4FAULTED" : c.enabled() ? "&aON" : "&8off";
            send(s, "&f" + c.id() + " " + state + " &7calls=" + c.stats().calls.sum() + " flags=" + c.stats().flags.sum() + " avg=" + String.format(Locale.ROOT, "%.1f", c.stats().avgMicros())
                    + "us max=" + c.stats().maxNs.get() / 1000 + "us err=" + c.stats().errors.sum() + " lagSkips=" + c.stats().lagSkips.sum());
        }
    }

    private void compat(CommandSender s) {
        send(s, "&fTargeted: &7Paper/Folia/Spigot 1.21.1 - 1.21.11 (compiled against 1.21.1 API). Real-server testing: &enot yet performed.");
        send(s, "&fFolia scheduler path: &7" + (plugin.platform().folia ? "active" : "inactive on this server"));
        send(s, "&fReduced because no packet library is used:");
        for (String f : plugin.packets().degradedFeatures()) s.sendMessage(Colors.color("&8 - &7" + f));
        for (Check c : plugin.checks().all()) {
            if (!c.limitations().isEmpty()) s.sendMessage(Colors.color("&8 - &f" + c.id() + "&7: " + c.limitations()));
        }
    }

    private void profile(CommandSender s) {
        Check c = plugin.checks().get("crystal-macro");
        if (c instanceof CrystalMacroCheck cm) {
            send(s, "&fCrystal profile: &7" + cm.profileName() + " &e(UNCALIBRATED defaults - tune on your server)");
            send(s, "&fBaseline collection: &7" + c.settings().bool("baseline-collection", false)
                    + " &8(set crystal.macro.baseline-collection: true in checks/crystal.yml; data goes to the 'baselines' table, nothing is flagged)");
        }
    }

    private void alerts(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) {
            send(s, "&cPlayers only.");
            return;
        }
        if (!p.hasPermission("paiac.alerts")) {
            send(s, "&cYou do not have permission (paiac.alerts).");
            return;
        }
        PlayerState st = plugin.states().getOrCreate(p);
        if (a.length > 1) st.alertsEnabled = a[1].equalsIgnoreCase("on");
        else st.alertsEnabled = !st.alertsEnabled;
        send(s, "Alerts " + (st.alertsEnabled ? "&aenabled" : "&cdisabled"));
    }

    private PlayerState online(CommandSender s, String[] a, int idx) {
        if (a.length <= idx) {
            send(s, "&cSpecify a player.");
            return null;
        }
        Player p = Bukkit.getPlayerExact(a[idx]);
        if (p == null) {
            send(s, "&cPlayer not online: " + a[idx]);
            return null;
        }
        return plugin.states().getOrCreate(p);
    }

    private void vl(CommandSender s, String[] a) {
        PlayerState st = online(s, a, 1);
        if (st == null) return;
        Map<String, Double> m = st.vlSnapshot();
        send(s, "&f" + st.name + " &7total VL " + String.format(Locale.ROOT, "%.1f", st.totalVl()));
        if (m.isEmpty()) send(s, "&7No violations.");
        m.forEach((k, v) -> send(s, "&8 - &f" + k + " &7" + String.format(Locale.ROOT, "%.1f", v)));
        synchronized (st.recentEvidence) {
            int n = 0;
            for (String line : st.recentEvidence) {
                if (n++ >= 5) break;
                s.sendMessage(Colors.color("&8   > &7" + line));
            }
        }
    }

    private void reset(CommandSender s, String[] a) {
        PlayerState st = online(s, a, 1);
        if (st == null) return;
        if (a.length > 2) st.clearVl(a[2]);
        else st.clearVl();
        send(s, "&7Violations cleared for &f" + st.name + (a.length > 2 ? " &7(" + a[2] + ")" : ""));
    }

    private void exempt(CommandSender s, String[] a, boolean on) {
        PlayerState st = online(s, a, 1);
        if (st == null) return;
        if (!on) {
            st.exemptUntilMs = 0;
            send(s, "&7Exemption removed for &f" + st.name);
            return;
        }
        long minutes = 10;
        if (a.length > 2) {
            try {
                minutes = Math.max(1, Math.min(1440, Long.parseLong(a[2])));
            } catch (NumberFormatException e) {
                send(s, "&cMinutes must be a number.");
                return;
            }
        }
        st.exemptUntilMs = System.currentTimeMillis() + minutes * 60_000L;
        send(s, "&f" + st.name + " &7exempt from all checks for " + minutes + " min");
    }

    private void ban(CommandSender s, String[] a) {
        if (a.length < 4) {
            send(s, "&cUsage: /paiac ban <player> <duration|perm> <reason>");
            return;
        }
        long dur;
        try {
            dur = Durations.parse(a[2]);
        } catch (IllegalArgumentException e) {
            send(s, "&c" + e.getMessage() + " (examples: 30m, 2h, 7d, perm)");
            return;
        }
        Player online = Bukkit.getPlayerExact(a[1]);
        UUID id;
        String name;
        if (online != null) {
            id = online.getUniqueId();
            name = online.getName();
        } else {
            OfflinePlayer op = Bukkit.getOfflinePlayer(a[1]);
            if (!op.hasPlayedBefore() && !op.isOnline()) {
                send(s, "&cUnknown player (never joined): " + a[1]);
                return;
            }
            id = op.getUniqueId();
            name = op.getName() == null ? a[1] : op.getName();
        }
        String reason = String.join(" ", Arrays.copyOfRange(a, 3, a.length));
        plugin.punishments().applyBan(id, name, reason, dur, s.getName(), online);
        send(s, "&7Banned &f" + name + " &7(" + Durations.format(dur) + ")");
    }

    private void unban(CommandSender s, String[] a) {
        if (a.length < 2) {
            send(s, "&cUsage: /paiac unban <player>");
            return;
        }
        UUID id = plugin.bans().findByName(a[1]);
        if (id == null) {
            Player online = Bukkit.getPlayerExact(a[1]);
            if (online != null) id = online.getUniqueId();
        }
        if (id == null) {
            send(s, "&cNo active PaiAC ban found for " + a[1]);
            return;
        }
        send(s, plugin.bans().unban(id) ? "&7Unbanned &f" + a[1] : "&cNo active ban.");
    }

    private void bans(CommandSender s) {
        List<BanEntry> list = new ArrayList<>(plugin.bans().list());
        if (list.isEmpty()) {
            send(s, "&7No active PaiAC bans.");
            return;
        }
        for (BanEntry b : list) {
            send(s, "&f" + b.name() + " &7- " + (b.expires() == -1 ? "permanent" : Durations.format(b.expires() - System.currentTimeMillis()) + " left")
                    + " &8(" + b.reason() + ")");
        }
    }

    private void inspect(CommandSender s, String[] a) {
        if (a.length < 2) {
            send(s, "&cUsage: /paiac inspect <player>");
            return;
        }
        Player p = Bukkit.getPlayerExact(a[1]);
        if (p == null) {
            send(s, "&cPlayer not online: " + a[1]);
            return;
        }
        PlayerState st = plugin.states().get(p.getUniqueId());
        if (st == null) {
            send(s, "&cNo PaiAC state for this player: events are NOT reaching the plugin (listener problem).");
            return;
        }
        long now = System.currentTimeMillis();
        long ago = st.lastMoveSeenMs == 0 ? -1 : now - st.lastMoveSeenMs;
        send(s, "&f" + p.getName() + " &7gamemode=" + p.getGameMode() + " op=" + p.isOp() + " allowFlight=" + p.getAllowFlight()
                + " flying=" + p.isFlying() + " gliding=" + p.isGliding() + " inVehicle=" + p.isInsideVehicle());
        send(s, "&7hasPermission(paiac.bypass)=" + (p.hasPermission("paiac.bypass") ? "&cTRUE (all checks skipped)" : "&afalse")
                + " &7bypassAll=" + st.bypassAll + " tempExemptLeft=" + Math.max(0, st.exemptUntilMs - now) + "ms");
        send(s, "&7move events seen=" + st.moveEvents + " last=" + (ago < 0 ? "never" : ago + "ms ago")
                + " &7last move verdict: &f" + st.lastExempt);
        send(s, "&7ping=" + com.paimc.anticheat.util.CompatUtil.ping(p) + "ms tps=" + String.format(Locale.ROOT, "%.1f", plugin.tps().tps())
                + " (VL is skipped when tps<" + plugin.settings().lagSkipTps + " or ping>" + plugin.settings().lagSkipPingMs + ")");
        send(s, "&7total VL=" + String.format(Locale.ROOT, "%.1f", st.totalVl()) + " (details: /paiac vl " + p.getName() + ")");
    }

    private void debug(CommandSender s) {
        boolean now = !plugin.debugEnabled();
        plugin.setDebugOverride(now);
        send(s, "Debug logging " + (now ? "&aON" : "&cOFF") + " &7(console)");
    }

    private void reload(CommandSender s) {
        List<String> w = plugin.reload();
        send(s, "&aConfiguration reloaded." + (w.isEmpty() ? "" : " &e" + w.size() + " warning(s), see console."));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            for (String s : SUBS) if (s.startsWith(a[0].toLowerCase(Locale.ROOT))) out.add(s);
        } else if (a.length == 2) {
            String sub = a[0].toLowerCase(Locale.ROOT);
            if (List.of("vl", "reset", "exempt", "unexempt", "ban", "inspect").contains(sub)) {
                for (Player p : Bukkit.getOnlinePlayers()) if (p.getName().toLowerCase(Locale.ROOT).startsWith(a[1].toLowerCase(Locale.ROOT))) out.add(p.getName());
            } else if (sub.equals("alerts")) {
                out.addAll(List.of("on", "off"));
            } else if (sub.equals("unban")) {
                for (BanEntry b : plugin.bans().list()) if (b.name() != null) out.add(b.name());
            }
        } else if (a.length == 3 && a[0].equalsIgnoreCase("reset")) {
            for (Check c : plugin.checks().all()) out.add(c.id());
        } else if (a.length == 3 && a[0].equalsIgnoreCase("ban")) {
            out.addAll(List.of("30m", "1d", "7d", "perm"));
        }
        return out;
    }
}
