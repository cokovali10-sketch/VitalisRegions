package com.vitalis.rg.util;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static com.vitalis.rg.util.Interact.Kind.*;

/** Outsiders: MECH only if flag on, LOCKED never, OPEN always. */
class InteractTest {

    @Test
    void mechanisms() {
        assertEquals(MECH, Interact.of(Material.OAK_BUTTON));
        assertEquals(MECH, Interact.of(Material.STONE_BUTTON));
        assertEquals(MECH, Interact.of(Material.LEVER));
        assertEquals(MECH, Interact.of(Material.OAK_DOOR));
        assertEquals(MECH, Interact.of(Material.OAK_TRAPDOOR));
        assertEquals(MECH, Interact.of(Material.OAK_FENCE_GATE));
        assertEquals(MECH, Interact.of(Material.REPEATER));
        assertEquals(MECH, Interact.of(Material.COMPARATOR));
        assertEquals(MECH, Interact.of(Material.BELL));
        assertEquals(MECH, Interact.of(Material.NOTE_BLOCK));
    }

    @Test
    void locked() {
        assertEquals(LOCKED, Interact.of(Material.CHEST));
        assertEquals(LOCKED, Interact.of(Material.BARREL));
        assertEquals(LOCKED, Interact.of(Material.FURNACE));
        assertEquals(LOCKED, Interact.of(Material.CRAFTING_TABLE));
        assertEquals(LOCKED, Interact.of(Material.ENCHANTING_TABLE));
        assertEquals(LOCKED, Interact.of(Material.RED_BED));
        assertEquals(LOCKED, Interact.of(Material.HOPPER));
        assertEquals(LOCKED, Interact.of(Material.SHULKER_BOX));
    }

    @Test
    void open() {
        assertEquals(OPEN, Interact.of(Material.STONE));
        assertEquals(OPEN, Interact.of(Material.DIRT));
        assertEquals(OPEN, Interact.of(Material.OAK_LOG));
        assertEquals(OPEN, Interact.of(Material.GLASS));
    }
}
