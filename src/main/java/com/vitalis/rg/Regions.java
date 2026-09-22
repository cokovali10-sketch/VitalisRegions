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
        if (getCommand("area") != null) {
            getCommand("area").setExecutor(commands);
            getCommand("area").setTabCompleter(commands);
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
}
