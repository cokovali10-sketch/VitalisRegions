package com.vitalis.rg.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Cuboid region: two corners, owner + owners/members, two flags. */
public class Region {
    public final String name;
    public final String world;
    public int minX, minY, minZ;
    public int maxX, maxY, maxZ;
    public UUID owner;
    public String ownerName;
    /** uuid -> name, includes owner */
    public final Map<UUID, String> owners = new LinkedHashMap<>();
    /** uuid -> name */
    public final Map<UUID, String> members = new LinkedHashMap<>();
    public boolean pvp;
    public boolean explosions;
    /** fire may spread inside/from outside */
    public boolean fireSpread;
    /** redstone + dispensers work */
    public boolean mechanisms;
    /** outsiders may /sethome here */
    public boolean homes;
    public final long createdAt;

    public Region(String name, String world,
                  int x1, int y1, int z1, int x2, int y2, int z2,
                  UUID owner, String ownerName,
                  boolean pvp, boolean explosions, boolean fireSpread,
                  boolean mechanisms, boolean homes) {
        this.name = name;
        this.world = world;
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
        this.owner = owner;
        this.ownerName = ownerName;
        this.owners.put(owner, ownerName);
        this.pvp = pvp;
        this.explosions = explosions;
        this.fireSpread = fireSpread;
        this.mechanisms = mechanisms;
        this.homes = homes;
        this.createdAt = System.currentTimeMillis();
    }

    public boolean contains(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        if (!loc.getWorld().getName().equals(world)) return false;
        int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public boolean contains(String worldName, int x, int y, int z) {
        if (!worldName.equals(world)) return false;
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public boolean intersects(Region o) {
        if (!world.equals(o.world)) return false;
        return minX <= o.maxX && maxX >= o.minX
                && minY <= o.maxY && maxY >= o.minY
                && minZ <= o.maxZ && maxZ >= o.minZ;
    }

    public boolean isOwner(UUID uuid) {
        return owners.containsKey(uuid);
    }

    public boolean isMember(UUID uuid) {
        return members.containsKey(uuid);
    }

    public boolean canBuild(UUID uuid) {
        return isOwner(uuid) || isMember(uuid);
    }

    public int sizeX() { return maxX - minX + 1; }
    public int sizeY() { return maxY - minY + 1; }
    public int sizeZ() { return maxZ - minZ + 1; }
    public long volume() { return (long) sizeX() * sizeY() * sizeZ(); }

    public Location center() {
        World w = Bukkit.getWorld(world);
        if (w == null) return null;
        return new Location(w, (minX + maxX) / 2.0, (minY + maxY) / 2.0, (minZ + maxZ) / 2.0);
    }
}
