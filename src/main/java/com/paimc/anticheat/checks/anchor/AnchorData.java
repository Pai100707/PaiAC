package com.paimc.anticheat.checks.anchor;

import com.paimc.anticheat.player.PlayerState;
import com.paimc.anticheat.util.DoubleRing;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.RespawnAnchor;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Shared timeline of anchor actions. Updated by the listener AFTER the checks ran, so checks see previous values. */
public final class AnchorData {
    public long placeNs;
    public long chargeNs;
    public long lastSlotNs;
    public final DoubleRing slotIntervalsMs = new DoubleRing(8);

    public static AnchorData of(PlayerState s) {
        return s.attachment("anchor-data", AnchorData::new);
    }

    public static AnchorAction classify(Player p, PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return AnchorAction.NONE;
        Block b = e.getClickedBlock();
        if (b == null || b.getType() != Material.RESPAWN_ANCHOR) return AnchorAction.NONE;
        int charges = charges(b);
        ItemStack it = e.getItem();
        if (it != null && it.getType() == Material.GLOWSTONE) {
            return charges < 4 ? AnchorAction.CHARGE : AnchorAction.NONE;
        }
        if (e.getHand() != EquipmentSlot.HAND) return AnchorAction.NONE;
        if (p.getInventory().getItemInOffHand().getType() == Material.GLOWSTONE && charges < 4) return AnchorAction.NONE;
        if (b.getWorld().getEnvironment() == World.Environment.NETHER) return AnchorAction.NONE;
        return charges > 0 ? AnchorAction.EXPLODE : AnchorAction.NONE;
    }

    private static int charges(Block b) {
        return b.getBlockData() instanceof RespawnAnchor ra ? ra.getCharges() : 0;
    }

    public void afterInteract(AnchorAction a) {
        long now = System.nanoTime();
        if (a == AnchorAction.CHARGE) chargeNs = now;
        else if (a == AnchorAction.EXPLODE) {
            chargeNs = 0;
            placeNs = 0;
        }
    }

    public void afterPlace() { placeNs = System.nanoTime(); }

    public void afterHeld() {
        long now = System.nanoTime();
        if (lastSlotNs != 0) {
            double ms = (now - lastSlotNs) / 1.0E6;
            if (ms < 2000) slotIntervalsMs.add(ms);
            else slotIntervalsMs.clear();
        }
        lastSlotNs = now;
    }
}
