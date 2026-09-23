package com.vitalis.rg.util;

import org.bukkit.entity.Player;

/**
 * Permission check with backward compatibility:
 * primary node area.*, legacy rg.* still accepted (old LuckPerms setups keep working).
 */
public final class Perms {
    private Perms() {}

    public static boolean has(Player p, String node) {
        return p.hasPermission("area." + node) || p.hasPermission("rg." + node);
    }

    /** Full control over any region: its owner, server operator or area.admin. */
    public static boolean canManage(Player p, com.vitalis.rg.model.Region r) {
        return r.isOwner(p.getUniqueId()) || p.isOp() || has(p, "admin");
    }
}
