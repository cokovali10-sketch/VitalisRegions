package com.vitalis.rg.util;

import com.vitalis.rg.model.Region;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.List;

/**
 * Optional WorldGuard overlap check (softdepend).
 * If WorldGuard is absent, returns empty. Never throws: all WG touches
 * stay inside this class so a missing WG jar dies here with
 * NoClassDefFoundError, caught by callers.
 */
public final class WGCheck {
    private WGCheck() {}

    /** WG region ids intersecting our region (same world, skip __global__). */
    public static List<String> overlapping(Region r) {
        List<String> out = new ArrayList<>();
        try {
            org.bukkit.World world = Bukkit.getWorld(r.world);
            if (world == null) return out;
            com.sk89q.worldguard.WorldGuard wg = com.sk89q.worldguard.WorldGuard.getInstance();
            com.sk89q.worldguard.protection.managers.RegionManager rm = wg.getPlatform()
                    .getRegionContainer().get(com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(world));
            if (rm == null) return out;
            for (com.sk89q.worldguard.protection.regions.ProtectedRegion wgR : rm.getRegions().values()) {
                if (wgR.getId().equalsIgnoreCase("__global__")) continue;
                com.sk89q.worldedit.math.BlockVector3 min = wgR.getMinimumPoint();
                com.sk89q.worldedit.math.BlockVector3 max = wgR.getMaximumPoint();
                if (r.minX <= max.x() && r.maxX >= min.x()
                        && r.minY <= max.y() && r.maxY >= min.y()
                        && r.minZ <= max.z() && r.maxZ >= min.z()) {
                    out.add(wgR.getId());
                }
            }
        } catch (Throwable ignored) {
            // no WorldGuard (or API changed) — we simply don't know
        }
        return out;
    }
}
