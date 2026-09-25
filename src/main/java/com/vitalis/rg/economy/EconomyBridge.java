package com.vitalis.rg.economy;

import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Reflection-only economy bridge. Zero compile-time references to any
 * economy API, so it can NEVER throw NoClassDefFoundError.
 *
 * Tries Vault legacy service (via Vault's own loader), then EssentialsX statics.
 */
public class EconomyBridge {
    private final JavaPlugin plugin;

    private Object provider;
    private Method mBalance;
    private Method mWithdraw;
    private Method mDeposit;

    private boolean essentials;
    private Method eGet;
    private Method eAdd;
    private Method eSub;
    private String mode = "none";

    public EconomyBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean setup() {
        if (tryVault()) return true;
        return tryEssentials();
    }

    public boolean ready() {
        return provider != null || essentials;
    }

    public String mode() {
        return mode;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private boolean tryVault() {
        try {
            Plugin vault = plugin.getServer().getPluginManager().getPlugin("Vault");
            if (vault == null || !vault.isEnabled()) return false;
            ClassLoader vl = vault.getClass().getClassLoader();
            Class<?> ecoClass = Class.forName("net.milkbowl.vault.economy.Economy", true, vl);
            Object rsp = plugin.getServer().getServicesManager().getRegistration((Class) ecoClass);
            if (rsp == null) return false;
            Object prov = rsp.getClass().getMethod("getProvider").invoke(rsp);
            if (prov == null) return false;
            mBalance = ecoClass.getMethod("getBalance", OfflinePlayer.class);
            mWithdraw = ecoClass.getMethod("withdrawPlayer", OfflinePlayer.class, double.class);
            mDeposit = ecoClass.getMethod("depositPlayer", OfflinePlayer.class, double.class);
            provider = prov;
            mode = "vault";
            return true;
        } catch (Throwable t) {
            provider = null;
            return false;
        }
    }

    private boolean tryEssentials() {
        try {
            Plugin ess = plugin.getServer().getPluginManager().getPlugin("Essentials");
            if (ess == null || !ess.isEnabled()) return false;
            ClassLoader el = ess.getClass().getClassLoader();
            Class<?> eco = Class.forName("com.earth2me.essentials.api.Economy", true, el);
            eGet = eco.getMethod("getMoneyExact", UUID.class);
            eAdd = eco.getMethod("add", UUID.class, BigDecimal.class);
            eSub = eco.getMethod("subtract", UUID.class, BigDecimal.class);
            essentials = true;
            mode = "essentials";
            return true;
        } catch (Throwable t) {
            essentials = false;
            return false;
        }
    }

    public double balance(OfflinePlayer p) {
        try {
            if (provider != null && mBalance != null) {
                Object v = mBalance.invoke(provider, p);
                if (v instanceof Number) return ((Number) v).doubleValue();
                return 0;
            }
            if (essentials && eGet != null) {
                Object v = eGet.invoke(null, p.getUniqueId());
                if (v instanceof BigDecimal) return ((BigDecimal) v).doubleValue();
                if (v instanceof Number) return ((Number) v).doubleValue();
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    public boolean has(OfflinePlayer p, double amount) {
        try {
            return balance(p) >= amount;
        } catch (Throwable t) {
            return false;
        }
    }

    public boolean withdraw(OfflinePlayer p, double amount) {
        try {
            if (provider != null && mWithdraw != null) {
                Object resp = mWithdraw.invoke(provider, p, amount);
                Object ok = resp.getClass().getMethod("transactionSuccess").invoke(resp);
                return Boolean.TRUE.equals(ok);
            }
            if (essentials && eSub != null) {
                eSub.invoke(null, p.getUniqueId(), BigDecimal.valueOf(amount));
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    public boolean deposit(OfflinePlayer p, double amount) {
        try {
            if (provider != null && mDeposit != null) {
                Object resp = mDeposit.invoke(provider, p, amount);
                Object ok = resp.getClass().getMethod("transactionSuccess").invoke(resp);
                return Boolean.TRUE.equals(ok);
            }
            if (essentials && eAdd != null) {
                eAdd.invoke(null, p.getUniqueId(), BigDecimal.valueOf(amount));
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /** Test seam: bind a fake provider object (methods resolved from its class). */
    public boolean bindFake(Object fake) {
        try {
            mBalance = fake.getClass().getMethod("getBalance", OfflinePlayer.class);
            mWithdraw = fake.getClass().getMethod("withdrawPlayer", OfflinePlayer.class, double.class);
            mDeposit = fake.getClass().getMethod("depositPlayer", OfflinePlayer.class, double.class);
            provider = fake;
            mode = "fake";
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
