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
    public boolean defaultFireSpread;
    public boolean defaultMechanisms;
    public boolean defaultHomes;

    public String titleInfo;
    public boolean sounds;
    public String themeFrom;
    public String themeTo;
    public boolean enterTitle;
    public boolean greetEnabled;
    public String greetEnter;
    public String greetLeave;
    public boolean borderEnabled;
    public int borderInterval;
    public int borderStep;
    public String borderParticle;
    public String borderColor;
    public float borderSize;
    public int borderMaxDistance;
    public int borderMaxParticles;

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
        defaultFireSpread = c.getBoolean("protection.default-fire-spread", false);
        defaultMechanisms = c.getBoolean("protection.default-mechanisms", true);
        defaultHomes = c.getBoolean("protection.default-homes", true);

        titleInfo = c.getString("gui.title-info", "&8Регион &a{name}");
        sounds = c.getBoolean("gui.sounds", true);
        themeFrom = c.getString("theme.from", "#FFD76A");
        themeTo = c.getString("theme.to", "#7AD9FF");
        enterTitle = c.getBoolean("theme.enter-title", true);
        greetEnabled = c.getBoolean("greet.enabled", true);
        greetEnter = c.getString("greet.enter", "&6◆ &f%region% &7· &f%owner%");
        greetLeave = c.getString("greet.leave", "&7Дикая местность");

        borderEnabled = c.getBoolean("border.enabled", true);
        borderInterval = c.getInt("border.interval", 10);
        borderStep = c.getInt("border.step", 1);
        borderParticle = c.getString("border.particle", "DUST");
        borderColor = c.getString("border.color", "#7AD9FF");
        borderSize = (float) c.getDouble("border.size", 0.9);
        borderMaxDistance = c.getInt("border.max-distance", 64);
        borderMaxParticles = c.getInt("border.max-particles", 400);
    }
}
