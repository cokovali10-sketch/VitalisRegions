package com.vitalis.rg.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Pure geometry + access logic, no server needed. */
class RegionTest {

    private Region region() {
        return new Region("home", "world",
                10, 60, 10, 20, 70, 20,
                UUID.randomUUID(), "Steve", false, false, false, true, true);
    }

    @Test
    void containsInside() {
        Region r = region();
        assertTrue(r.contains("world", 10, 60, 10)); // min corner inclusive
        assertTrue(r.contains("world", 20, 70, 20)); // max corner inclusive
        assertTrue(r.contains("world", 15, 65, 15));
    }

    @Test
    void containsOutside() {
        Region r = region();
        assertFalse(r.contains("world", 9, 65, 15));
        assertFalse(r.contains("world", 21, 65, 15));
        assertFalse(r.contains("world", 15, 59, 15));
        assertFalse(r.contains("world", 15, 71, 15));
    }

    @Test
    void containsWrongWorld() {
        assertFalse(region().contains("nether", 15, 65, 15));
    }

    @Test
    void intersectsOverlap() {
        Region a = region();
        Region b = new Region("b", "world", 15, 60, 15, 30, 80, 30,
                UUID.randomUUID(), "Alex", false, false, false, true, true);
        assertTrue(a.intersects(b));
        assertTrue(b.intersects(a));
    }

    @Test
    void intersectsTouchingCounts() {
        // shared border plane x=20 -> treated as overlap (safe default: deny create)
        Region a = region();
        Region b = new Region("b", "world", 20, 60, 10, 30, 70, 20,
                UUID.randomUUID(), "Alex", false, false, false, true, true);
        assertTrue(a.intersects(b));
    }

    @Test
    void intersectsSeparate() {
        Region a = region();
        Region b = new Region("b", "world", 21, 60, 10, 30, 70, 20,
                UUID.randomUUID(), "Alex", false, false, false, true, true);
        assertFalse(a.intersects(b));
        assertFalse(b.intersects(a));
    }

    @Test
    void intersectsDifferentWorld() {
        Region a = region();
        Region b = new Region("b", "nether", 10, 60, 10, 20, 70, 20,
                UUID.randomUUID(), "Alex", false, false, false, true, true);
        assertFalse(a.intersects(b));
    }

    @Test
    void sizes() {
        Region r = region();
        assertEquals(11, r.sizeX());
        assertEquals(11, r.sizeY());
        assertEquals(11, r.sizeZ());
        assertEquals(1331L, r.volume());
    }

    @Test
    void flagsStored() {
        Region r = region();
        assertFalse(r.pvp);
        assertFalse(r.explosions);
        assertFalse(r.fireSpread);
        assertTrue(r.mechanisms);
        assertTrue(r.homes);
        r.fireSpread = true;
        r.mechanisms = false;
        r.homes = false;
        assertTrue(r.fireSpread);
        assertFalse(r.mechanisms);
        assertFalse(r.homes);
    }

    @Test
    void access() {
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        Region r = new Region("home", "world", 0, 0, 0, 5, 5, 5, owner, "Owner", false, false, false, true, true);
        r.members.put(member, "Member");

        assertTrue(r.isOwner(owner));
        assertTrue(r.canBuild(owner));
        assertFalse(r.isMember(owner));

        assertTrue(r.isMember(member));
        assertTrue(r.canBuild(member));

        assertFalse(r.canBuild(stranger));
    }
}
