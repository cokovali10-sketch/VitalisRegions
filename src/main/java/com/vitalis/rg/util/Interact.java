package com.vitalis.rg.util;

import org.bukkit.Material;

/**
 * What right-click on a block means for outsiders:
 * MECH — buttons, levers, doors, hatches, gates, bells, repeaters (flag "mechanisms" decides);
 * LOCKED — chests, workstations, beds and the rest (owners/members only, always);
 * OPEN — everything else (decorations, grass...), free for everyone.
 */
public final class Interact {
    public enum Kind { MECH, LOCKED, OPEN }

    private Interact() {}

    public static Kind of(Material m) {
        String n = m.name();
        // mechanisms: guests may use them only when the region flag is ON
        if (n.contains("BUTTON") || n.contains("LEVER")
                || n.contains("DOOR") || n.contains("GATE")
                || n.equals("REPEATER") || n.equals("COMPARATOR")
                || n.equals("BELL") || n.equals("NOTE_BLOCK")) {
            return Kind.MECH;
        }
        // locked: containers, workstations and specials — owners/members only, always
        if (n.contains("CHEST") || n.equals("BARREL") || n.equals("ENDER_CHEST")
                || n.contains("FURNACE") || n.contains("BED")
                || n.equals("ANVIL") || n.equals("CHIPPED_ANVIL") || n.equals("DAMAGED_ANVIL")
                || n.equals("ENCHANTING_TABLE") || n.equals("BREWING_STAND")
                || n.equals("HOPPER") || n.equals("DROPPER") || n.equals("DISPENSER")
                || n.equals("CRAFTER") || n.equals("JUKEBOX") || n.equals("LECTERN")
                || n.equals("COMPOSTER") || n.equals("CAKE")
                || n.equals("RESPAWN_ANCHOR") || n.equals("LODESTONE")
                || n.contains("SHULKER_BOX") || n.equals("CRAFTING_TABLE")
                || n.equals("CARTOGRAPHY_TABLE") || n.equals("FLETCHING_TABLE")
                || n.equals("GRINDSTONE") || n.equals("LOOM") || n.equals("SMITHING_TABLE")
                || n.equals("STONECUTTER") || n.equals("BEACON") || n.equals("DRAGON_EGG")
                || n.contains("CANDLE") || n.contains("FLOWER_POT")) {
            return Kind.LOCKED;
        }
        return Kind.OPEN;
    }
}
