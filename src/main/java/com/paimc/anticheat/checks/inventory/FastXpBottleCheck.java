package com.paimc.anticheat.checks.inventory;

import com.paimc.anticheat.PaiAC;
import com.paimc.anticheat.checks.Check;
import com.paimc.anticheat.config.CheckSettings;
import com.paimc.anticheat.player.PlayerState;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.ProjectileLaunchEvent;

import java.util.ArrayDeque;

/**
 * Experience bottles thrown faster than the vanilla right-click rate (one every 4 ticks = 200 ms).
 * Protects, not only detects: excess throws are cancelled immediately (cancel-excess), so the exploit
 * is blocked even while punishments are in DETECTION_ONLY.
 */
public final class FastXpBottleCheck extends Check {
    private static final class Data {
        final ArrayDeque<Long> times = new ArrayDeque<>();
        long last;
    }

    public FastXpBottleCheck(PaiAC plugin) {
        super(plugin, "fast-xp", "inventory", "inventory", "inventory.fast-xp",
                "Experience bottles thrown faster than the vanilla use rate");
    }

    @Override
    public String limitations() {
        return "Rate limit only: legit lag bursts can occasionally lose one throw (cancelled bottle is not consumed). Other fast-use exploits (bow, food) are not covered.";
    }

    @Override
    public void onProjectileLaunch(PlayerState s, Player p, ProjectileLaunchEvent e) {
        String t = e.getEntity().getType().name();
        if (!t.contains("EXPERIENCE_BOTTLE") && !t.contains("EXP_BOTTLE")) return;

        CheckSettings cs = settings();
        Data d = s.attachment("fast-xp", Data::new);
        long now = System.currentTimeMillis();
        long window = cs.lng("window-ms", 1000);
        int max = cs.integer("max-per-window", 6);
        long minGap = cs.lng("min-gap-ms", 100);

        while (!d.times.isEmpty() && d.times.peekFirst() < now - window) d.times.pollFirst();
        long prevLast = d.last;
        d.times.addLast(now);
        d.last = now;

        boolean burst = prevLast != 0 && now - prevLast < minGap;
        boolean over = d.times.size() > max;
        if (burst || over) {
            if (cs.bool("cancel-excess", true)) {
                e.setCancelled(true);
                d.times.pollLast(); // the cancelled throw must not count against the next legit one
                d.last = prevLast;
            }
            fail(s, d.times.size() + (burst ? 1 : 0), max,
                    (burst ? "gap=" + (now - prevLast) + "ms<" + minGap + "ms " : "") + "throws/window=" + (d.times.size() + 1));
        } else {
            pass(s);
        }
    }
}
