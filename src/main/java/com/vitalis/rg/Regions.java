package com.vitalis.rg;

import com.vitalis.rg.command.RgCommand;
import com.vitalis.rg.config.RgConfig;
import com.vitalis.rg.economy.VaultHook;
import com.vitalis.rg.menu.ChatMenu;
import com.vitalis.rg.listener.GuiListener;
import com.vitalis.rg.listener.ProtectListener;
import com.vitalis.rg.select.SelectionManager;
import com.vitalis.rg.storage.RegionStorage;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * VitalisRegions - Paper/Purpur 1.21.11.
 * /area, /area pos1/pos2/remove + chat button menus. No GUI windows.
 */
public class Regions extends JavaPlugin {
    private final RgConfig cfg = new RgConfig();
    private RegionStorage storage;
    private final SelectionManager selections = new SelectionManager();
    private ChatMenu menus;
    private RgCommand commands;
    private final VaultHook economy = new VaultHook(this);
    /** target uuid -> warmup task (kick to spawn in 3s, cancels on move/quit) */
    private final Map<UUID, org.bukkit.scheduler.BukkitTask> kickWarmups = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        cfg.load(this);
        storage = new RegionStorage(this);
        storage.load();
        menus = new ChatMenu(this);

        if (economy.setup()) {
            getLogger().info("Vault hooked, claim price: " + cfg.claimPrice);
        } else {
            getLogger().info("Vault not found, claims are free.");
        }

        commands = new RgCommand(this);
        // Paper plugins register commands in code (no YAML commands support).
        // Fallback to Bukkit registration keeps Spigot working too.
        try {
            registerCommand("area", "Regions menu",
                    java.util.List.of("region", "regions", "rg"),
                    new com.vitalis.rg.command.AreaCommand(commands));
        } catch (NoSuchMethodError e) {
            if (getCommand("area") != null) {
                getCommand("area").setExecutor(commands);
                getCommand("area").setTabCompleter(commands);
            }
            getLogger().warning("Paper command API missing, Bukkit fallback used.");
        }
        getServer().getPluginManager().registerEvents(new GuiListener(this), this);
        getServer().getPluginManager().registerEvents(new ProtectListener(this), this);

        getLogger().info("VitalisRegions enabled, regions: " + storage.all().size());
    }

    @Override
    public void onDisable() {
        if (storage != null) storage.save();
        getLogger().info("VitalisRegions disabled, data saved.");
    }

    public RgConfig cfg() { return cfg; }
    public RegionStorage storage() { return storage; }
    public SelectionManager selections() { return selections; }
    public ChatMenu menus() { return menus; }
    public RgCommand commands() { return commands; }
    public VaultHook economy() { return economy; }
    public Map<UUID, org.bukkit.scheduler.BukkitTask> kickWarmups() { return kickWarmups; }
}
