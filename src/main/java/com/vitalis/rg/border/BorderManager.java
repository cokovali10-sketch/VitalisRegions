package com.vitalis.rg.border;

import com.vitalis.rg.Regions;
import com.vitalis.rg.model.Region;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Per-player region outline drawn with particles. Toggle is client-side
 * (memory only) so we never write to storage on a click. A single repeating
 * task renders only regions near each viewer and only up to max-particles.
 */
public class BorderManager {
    private final Regions plugin;
    /** viewer uuid -> toggled region names (lowercase) */
    private final Map<UUID, Set<String>> on = new HashMap<>();
    private BukkitTask task;
    private Particle particle = Particle.DUST;
    private boolean dust = true;
    private Color color = Color.fromRGB(0x7AD9FF);

    public BorderManager(Regions plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.cfg().borderEnabled) return;
        resolveParticle();
        stop();
        long interval = Math.max(2, plugin.cfg().borderInterval);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, interval, interval);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void clear(UUID uuid) {
        on.remove(uuid);
    }

    private void resolveParticle() {
        String name = plugin.cfg().borderParticle;
        try {
            particle = Particle.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Unknown border particle '" + name
                    + "', using DUST.");
            particle = Particle.DUST;
        }
        dust = particle == Particle.DUST;
        color = parseColor(plugin.cfg().borderColor);
    }

    private static Color parseColor(String hex) {
        try {
            String h = hex.startsWith("#") ? hex.substring(1) : hex;
            return Color.fromRGB(Integer.parseInt(h, 16));
        } catch (Exception e) {
            return Color.fromRGB(0x7AD9FF);
        }
    }

    /** Toggle for one region; returns the new state (true = shown). */
    public boolean toggle(Player p, Region r) {
        Set<String> set = on.computeIfAbsent(p.getUniqueId(), k -> new HashSet<>());
        String key = r.name.toLowerCase(Locale.ROOT);
        if (set.remove(key)) {
            if (set.isEmpty()) on.remove(p.getUniqueId());
            return false;
        }
        set.add(key);
        return true;
    }

    public boolean isOn(Player p, Region r) {
        Set<String> set = on.get(p.getUniqueId());
        return set != null && set.contains(r.name.toLowerCase(Locale.ROOT));
    }

    private void tick() {
        if (on.isEmpty()) return;
        int maxDist = plugin.cfg().borderMaxDistance;
        double maxDistSq = (double) maxDist * maxDist;
        for (Player p : Bukkit.getOnlinePlayers()) {
            Set<String> set = on.get(p.getUniqueId());
            if (set == null || set.isEmpty()) continue;
            Location pl = p.getLocation();
            for (String key : new ArrayList<>(set)) {
                Region r = plugin.storage().get(key);
                if (r == null) {
                    set.remove(key);
                    continue;
                }
                if (!r.world.equals(p.getWorld().getName())) continue;
                if (distanceSq(pl, r) > maxDistSq) continue;
                draw(p, r);
            }
            if (set.isEmpty()) on.remove(p.getUniqueId());
        }
    }

    private static double distanceSq(Location l, Region r) {
        double x = clamp(l.getX(), r.minX, r.maxX + 1);
        double y = clamp(l.getY(), r.minY, r.maxY + 1);
        double z = clamp(l.getZ(), r.minZ, r.maxZ + 1);
        double dx = l.getX() - x, dy = l.getY() - y, dz = l.getZ() - z;
        return dx * dx + dy * dy + dz * dz;
    }

    private static double clamp(double v, double min, double max) {
        return v < min ? min : (v > max ? max : v);
    }

    /** Outer box coords so the line sits on the block border. */
    private void draw(Player p, Region r) {
        int x1 = r.minX, y1 = r.minY, z1 = r.minZ;
        int x2 = r.maxX + 1, y2 = r.maxY + 1, z2 = r.maxZ + 1;
        int dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        // rough point count of the 12 edges, pick a step that fits the cap
        long pts = 4L * dy + 4L * dx + 4L * dz + 12L;
        int cap = Math.max(8, plugin.cfg().borderMaxParticles);
        int step = Math.max(plugin.cfg().borderStep,
                (int) Math.ceil((double) pts / cap));

        // 4 vertical edges
        for (int x : new int[]{x1, x2}) {
            for (int z : new int[]{z1, z2}) {
                for (int y = y1; y <= y2; y += step) spawn(p, x, y, z);
            }
        }
        // bottom + top horizontal rings
        for (int y : new int[]{y1, y2}) {
            for (int x = x1; x <= x2; x += step) {
                spawn(p, x, y, z1);
                spawn(p, x, y, z2);
            }
            for (int z = z1; z <= z2; z += step) {
                spawn(p, x1, y, z);
                spawn(p, x2, y, z);
            }
        }
    }

    private void spawn(Player p, double x, double y, double z) {
        if (dust) {
            p.spawnParticle(particle, x, y, z, 1, 0, 0, 0,
                    new Particle.DustOptions(color, plugin.cfg().borderSize));
        } else {
            p.spawnParticle(particle, x, y, z, 1, 0, 0, 0, 0);
        }
    }
}
