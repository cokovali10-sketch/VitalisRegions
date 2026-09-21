package com.vitalis.rg.select;

import org.bukkit.Location;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Per-player cuboid selection (two corners). */
public class SelectionManager {
    public static class Selection {
        public Location pos1;
        public Location pos2;

        public boolean complete() {
            return pos1 != null && pos2 != null
                    && pos1.getWorld() != null
                    && pos1.getWorld().equals(pos2.getWorld());
        }

        public void clear() {
            pos1 = null;
            pos2 = null;
        }
    }

    private final Map<UUID, Selection> map = new HashMap<>();

    public Selection get(UUID uuid) {
        return map.computeIfAbsent(uuid, k -> new Selection());
    }

    public void clear(UUID uuid) {
        map.remove(uuid);
    }
}
