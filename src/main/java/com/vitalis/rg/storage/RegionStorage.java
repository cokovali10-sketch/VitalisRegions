package com.vitalis.rg.storage;

import com.vitalis.rg.model.Region;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** YAML storage: plugins/VitalisRegions/regions.yml */
public class RegionStorage {
    private final JavaPlugin plugin;
    private final File file;
    /** lower-case name -> region */
    private final Map<String, Region> regions = new ConcurrentHashMap<>();
    /** world -> regions in it. Rebuilt wholesale on every mutation (readers always see a consistent list). */
    private final Map<String, List<Region>> byWorld = new ConcurrentHashMap<>();

    public RegionStorage(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "regions.yml");
    }

    public synchronized void load() {
        regions.clear();
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = y.getConfigurationSection("regions");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            try {
                ConfigurationSection s = sec.getConfigurationSection(key);
                if (s == null) continue;
                int[] min = toInts(s.getString("min", "0,0,0"));
                int[] max = toInts(s.getString("max", "0,0,0"));
                UUID owner = UUID.fromString(s.getString("owner"));
                Region r = new Region(key, s.getString("world", "world"),
                        min[0], min[1], min[2], max[0], max[1], max[2],
                        owner, s.getString("ownerName", "?"),
                        s.getBoolean("pvp", false), s.getBoolean("explosions", false),
                        s.getBoolean("fire-spread", false), s.getBoolean("mechanisms", true),
                        s.getBoolean("homes", true));
                readMap(s.getStringList("owners"), r.owners);
                readMap(s.getStringList("members"), r.members);
                r.owners.putIfAbsent(owner, r.ownerName);
                regions.put(key.toLowerCase(Locale.ROOT), r);
            } catch (Exception e) {
                plugin.getLogger().warning("Skip broken region " + key + ": " + e.getMessage());
            }
        }
        reindex();
    }

    private void reindex() {
        Map<String, List<Region>> fresh = new HashMap<>();
        for (Region r : regions.values()) {
            fresh.computeIfAbsent(r.world, k -> new ArrayList<>()).add(r);
        }
        byWorld.clear();
        byWorld.putAll(fresh);
    }

    private int[] toInts(String s) {
        String[] p = s.split(",");
        return new int[]{Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])};
    }

    private void readMap(List<String> list, Map<UUID, String> map) {
        for (String e : list) {
            int i = e.indexOf('|');
            if (i < 0) continue;
            try {
                map.put(UUID.fromString(e.substring(0, i)), e.substring(i + 1));
            } catch (IllegalArgumentException ignored) {}
        }
    }

    public synchronized void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Region r : regions.values()) {
            String p = "regions." + r.name;
            y.set(p + ".world", r.world);
            y.set(p + ".min", r.minX + "," + r.minY + "," + r.minZ);
            y.set(p + ".max", r.maxX + "," + r.maxY + "," + r.maxZ);
            y.set(p + ".owner", r.owner.toString());
            y.set(p + ".ownerName", r.ownerName);
            y.set(p + ".owners", writeMap(r.owners));
            y.set(p + ".members", writeMap(r.members));
            y.set(p + ".pvp", r.pvp);
            y.set(p + ".explosions", r.explosions);
            y.set(p + ".fire-spread", r.fireSpread);
            y.set(p + ".mechanisms", r.mechanisms);
            y.set(p + ".homes", r.homes);
        }
        try {
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Save failed: " + e.getMessage());
        }
    }

    private List<String> writeMap(Map<UUID, String> map) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<UUID, String> e : map.entrySet()) {
            out.add(e.getKey() + "|" + e.getValue());
        }
        return out;
    }

    public void saveAsync() {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::save);
    }

    public synchronized Region get(String name) {
        if (name == null) return null;
        return regions.get(name.toLowerCase(Locale.ROOT));
    }

    public synchronized Collection<Region> all() {
        return new ArrayList<>(regions.values());
    }

    public synchronized void put(Region r) {
        regions.put(r.name.toLowerCase(Locale.ROOT), r);
        reindex();
    }

    public synchronized Region remove(String name) {
        if (name == null) return null;
        Region removed = regions.remove(name.toLowerCase(Locale.ROOT));
        if (removed != null) reindex();
        return removed;
    }

    /** Rename: returns false if new name invalid or taken. */
    public synchronized boolean rename(String oldName, String newName) {
        if (oldName == null || newName == null) return false;
        if (!newName.matches("[a-zA-Z0-9_-]{3,16}")) return false;
        if (regions.containsKey(newName.toLowerCase(Locale.ROOT))) return false;
        Region r = regions.remove(oldName.toLowerCase(Locale.ROOT));
        if (r == null) return false;
        r.name = newName;
        regions.put(newName.toLowerCase(Locale.ROOT), r);
        reindex();
        return true;
    }

    public synchronized int countBy(UUID owner) {
        int n = 0;
        for (Region r : regions.values()) {
            if (r.owner.equals(owner)) n++;
        }
        return n;
    }

    /**
     * First region containing world+coords, or null.
     * Only scans regions of the same world (index), so protection checks stay
     * fast even with hundreds of regions across multiple worlds.
     */
    public Region at(String world, int x, int y, int z) {
        List<Region> same = byWorld.get(world);
        if (same == null) return null;
        for (Region r : same) {
            if (r.contains(world, x, y, z)) return r;
        }
        return null;
    }
}
