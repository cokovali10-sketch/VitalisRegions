package com.vitalis.rg.config;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Typed wrapper over config.yml. */
public class RgConfig {
    public String prefix;
    public Material wand;

    public int maxPerPlayer;
    public Set<String> blockedWorlds = new HashSet<>();

    public double claimPrice;
    public String symbol;

    public boolean defaultPvp;
    public boolean defaultExplosions;

    public String titleInfo;
    public boolean sounds;

    public void load(JavaPlugin plugin) {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();

        prefix = c.getString("general.prefix", "&8[&aRG&8] ");
        try {
            wand = Material.valueOf(c.getString("general.wand", "GOLDEN_AXE").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            wand = Material.GOLDEN_AXE;
        }

        maxPerPlayer = c.getInt("limits.max-per-player", 100);
        blockedWorlds.clear();
        for (String w : c.getStringList("limits.blocked-worlds")) {
            blockedWorlds.add(w.toLowerCase(Locale.ROOT));
        }

        claimPrice = c.getDouble("economy.claim-price", 0.0);
        symbol = c.getString("economy.symbol", "$");

        defaultPvp = c.getBoolean("protection.default-pvp", false);
        defaultExplosions = c.getBoolean("protection.default-explosions", false);

        titleInfo = c.getString("gui.title-info", "&8Регион &a{name}");
        sounds = c.getBoolean("gui.sounds", true);
    }
}
