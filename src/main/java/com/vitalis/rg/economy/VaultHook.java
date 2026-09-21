package com.vitalis.rg.economy;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

/** Optional Vault bridge (claim price). Plugin works without economy. */
public class VaultHook {
    private final JavaPlugin plugin;
    private Economy economy;

    public VaultHook(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean setup() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) return false;
        try {
            RegisteredServiceProvider<Economy> rsp =
                    plugin.getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp == null) return false;
            economy = rsp.getProvider();
            return economy != null;
        } catch (NoClassDefFoundError e) {
            return false;
        }
    }

    public boolean ready() {
        return economy != null;
    }

    public boolean has(OfflinePlayer p, double amount) {
        return economy.has(p, amount);
    }

    public boolean withdraw(OfflinePlayer p, double amount) {
        return economy.withdrawPlayer(p, amount).transactionSuccess();
    }
}
