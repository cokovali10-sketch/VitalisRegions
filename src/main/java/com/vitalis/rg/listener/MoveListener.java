package com.vitalis.rg.listener;

import com.vitalis.rg.Regions;
import com.vitalis.rg.model.Region;
import com.vitalis.rg.util.Msg;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enter/leave actionbar toasts. Memory-only (no storage writes):
 * first sighting per session is silent, messages only on CHANGE.
 * Only runs when the player actually crosses a block border.
 */
public class MoveListener implements Listener {
    private final Regions plugin;
    /** uuid -> region name, "" = wilderness */
    private final Map<UUID, String> last = new ConcurrentHashMap<>();

    public MoveListener(Regions plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Location t = e.getTo();
        if (t == null || t.getWorld() == null) return;
        Location f = e.getFrom();
        if (f.getBlockX() == t.getBlockX() && f.getBlockY() == t.getBlockY()
                && f.getBlockZ() == t.getBlockZ()) return;
        check(e.getPlayer(), t);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        Location t = e.getTo();
        if (t == null || t.getWorld() == null) return;
        check(e.getPlayer(), t);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        last.remove(e.getPlayer().getUniqueId());
        if (plugin.borders() != null) plugin.borders().clear(e.getPlayer().getUniqueId());
    }

    private void check(Player p, Location t) {
        if (!plugin.cfg().greetEnabled) return;
        Region r;
        try {
            r = plugin.storage().at(t.getWorld().getName(),
                    t.getBlockX(), t.getBlockY(), t.getBlockZ());
        } catch (Exception ex) {
            return;
        }
        String key = r == null ? "" : r.name;
        String prev = last.put(p.getUniqueId(), key);
        if (prev == null) return; // first sighting: silent
        if (prev.equals(key)) return;
        try {
            if (r == null) {
                p.sendActionBar(Msg.color(plugin.cfg().greetLeave));
            } else {
                p.sendActionBar(Msg.color(text(plugin.cfg().greetEnter, r)));
            }
        } catch (Exception ignored) {}
    }

    /** Pure formatting (tested): %region% + %owner%. */
    public static String text(String format, Region r) {
        return format.replace("%region%", r.name)
                .replace("%owner%", r.ownerName == null ? "?" : r.ownerName);
    }
}
