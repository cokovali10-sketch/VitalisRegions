package com.vitalis.rg.hook;

import com.vitalis.rg.Regions;
import com.vitalis.rg.model.Region;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/**
 * %vitalisregions_mine% (current region or wilderness),
 * %vitalisregions_count% (owned), %vitalisregions_owner% (owner or -).
 * For chat/scoreboard/holograms. Registered only when PAPI is present.
 */
public class RgExpansion extends PlaceholderExpansion {
    private final Regions plugin;

    public RgExpansion(Regions plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "vitalisregions";
    }

    @Override
    public String getAuthor() {
        return "Vitalis";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer p, String params) {
        try {
            if (p == null || !p.isOnline()) return "";
            Player pl = p.getPlayer();
            if (pl == null) return "";
            switch (params.toLowerCase(java.util.Locale.ROOT)) {
                case "count":
                    return String.valueOf(plugin.storage().countBy(pl.getUniqueId()));
                case "mine":
                case "owner": {
                    Region r;
                    try {
                        org.bukkit.Location l = pl.getLocation();
                        r = plugin.storage().at(l.getWorld().getName(),
                                l.getBlockX(), l.getBlockY(), l.getBlockZ());
                    } catch (Exception e) {
                        return "";
                    }
                    if (r == null) return params.equalsIgnoreCase("owner") ? "-" : "Дикая местность";
                    return params.equalsIgnoreCase("owner")
                            ? (r.ownerName == null ? "?" : r.ownerName) : r.name;
                }
                default:
                    return "";
            }
        } catch (Exception e) {
            return "";
        }
    }
}
