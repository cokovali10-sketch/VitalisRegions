package com.vitalis.rg;

import com.vitalis.rg.command.RgCommand;
import com.vitalis.rg.config.RgConfig;
import com.vitalis.rg.economy.VaultHook;
import com.vitalis.rg.gui.RegionGui;
import com.vitalis.rg.listener.GuiListener;
import com.vitalis.rg.listener.ProtectListener;
import com.vitalis.rg.select.SelectionManager;
import com.vitalis.rg.storage.RegionStorage;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * VitalisRegions v1.0.0 - Paper/Purpur 1.21.11.
 * /area pos1/pos2/wand/create/delete/list/info + GUI members/owners + protection.
 */
public class Regions extends JavaPlugin {
    private final RgConfig cfg = new RgConfig();
    private RegionStorage storage;
    private final SelectionManager selections = new SelectionManager();
    private RegionGui gui;
    private final VaultHook economy = new VaultHook(this);

    @Override
    public void onEnable() {
        cfg.load(this);
        storage = new RegionStorage(this);
        storage.load();
        gui = new RegionGui(cfg, storage);

        if (economy.setup()) {
            getLogger().info("Vault hooked, claim price: " + cfg.claimPrice);
        } else {
            getLogger().info("Vault not found, claims are free.");
        }

        RgCommand cmd = new RgCommand(this);
        if (getCommand("rg") != null) {
            getCommand("rg").setExecutor(cmd);
            getCommand("rg").setTabCompleter(cmd);
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
    public RegionGui gui() { return gui; }
    public VaultHook economy() { return economy; }
}
