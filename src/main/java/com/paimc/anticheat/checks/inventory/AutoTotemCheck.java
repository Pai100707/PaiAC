package com.paimc.anticheat.checks.inventory;

import com.paimc.anticheat.PaiAC;
import com.paimc.anticheat.checks.Check;
import com.paimc.anticheat.config.CheckSettings;
import com.paimc.anticheat.player.PlayerState;
import com.paimc.anticheat.util.CompatUtil;
import com.paimc.anticheat.util.DoubleRing;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

/** Time between a totem pop and the next totem being moved to the off-hand. */
public final class AutoTotemCheck extends Check {
    private static final class Data {
        long popNs;
        boolean pending;
        final DoubleRing refillMs = new DoubleRing(8);
    }

    public AutoTotemCheck(PaiAC plugin) {
        super(plugin, "auto-totem", "inventory", "inventory", "inventory.auto-totem",
                "Totem re-equipped faster than human reaction, or with metronomic consistency");
    }

    @Override
    public String limitations() {
        return "Only sees refills done through inventory clicks or the swap-hands key; times include network latency.";
    }

    @Override
    public void onTotemPop(PlayerState s, Player p, EntityResurrectEvent e) {
        if (e.isCancelled()) return;
        Data d = s.attachment("auto-totem", Data::new);
        d.popNs = System.nanoTime();
        d.pending = true;
    }

    @Override
    public void onSwapHands(PlayerState s, Player p, PlayerSwapHandItemsEvent e) {
        Data d = s.attachment("auto-totem", Data::new);
        if (d.pending && isTotem(e.getOffHandItem())) refilled(s, p, d);
    }

    @Override
    public void onInventoryClick(PlayerState s, Player p, InventoryClickEvent e) {
        Data d = s.attachment("auto-totem", Data::new);
        if (!d.pending) return;
        boolean toOffhand = false;
        if (e.getClick() == ClickType.SWAP_OFFHAND && isTotem(e.getCurrentItem())) toOffhand = true;
        else if (e.getClickedInventory() == p.getInventory() && e.getSlot() == 40 && isTotem(e.getCursor())) toOffhand = true;
        if (toOffhand) refilled(s, p, d);
    }

    private void refilled(PlayerState s, Player p, Data d) {
        CheckSettings cs = settings();
        double ms = (System.nanoTime() - d.popNs) / 1.0E6;
        d.pending = false;
        if (ms > cs.dbl("timeout-ms", 3000)) return;
        d.refillMs.add(ms);

        double min = cs.dbl("min-refill-ms", 80);
        int ping = CompatUtil.ping(p);
        if (ms < min) {
            failWeighted(s, 2.0, ms, min, "refill " + String.format("%.0f", ms) + "ms ping=" + ping);
            return;
        }
        int need = cs.integer("min-samples", 4);
        if (d.refillMs.size() >= need) {
            double mean = d.refillMs.mean();
            double std = d.refillMs.stdDev();
            if (mean < cs.dbl("consistency-mean-limit-ms", 300) && std < cs.dbl("min-stddev-ms", 15)) {
                fail(s, std, cs.dbl("min-stddev-ms", 15), "consistent refill mean=" + String.format("%.0f", mean) + "ms std=" + String.format("%.1f", std) + "ms");
                return;
            }
        }
        pass(s);
    }

    private static boolean isTotem(ItemStack it) {
        return it != null && it.getType() == Material.TOTEM_OF_UNDYING;
    }
}
