package com.vitalis.rg.listener;

import com.vitalis.rg.Regions;
import com.vitalis.rg.model.Region;
import com.vitalis.rg.util.Msg;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/** Region protection: build, containers, buckets, pvp, explosions. */
public class ProtectListener implements Listener {
    private final Regions plugin;

    public ProtectListener(Regions plugin) {
        this.plugin = plugin;
    }

    private boolean bypass(Player p) {
        return p.hasPermission("rg.bypass") || p.isOp();
    }

    private void deny(Player p, Region r) {
        p.sendMessage(Msg.color(plugin.cfg().prefix + "&cЗдесь регион &f" + r.name + " &c(" + r.ownerName + ")"));
    }

    // ---------------- blocks ----------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (bypass(p)) return;
        Block b = e.getBlock();
        Region r = plugin.storage().at(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
        if (r == null) return;
        if (!r.canBuild(p.getUniqueId())) {
            e.setCancelled(true);
            deny(p, r);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        if (bypass(p)) return;
        Block b = e.getBlockPlaced();
        Region r = plugin.storage().at(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
        if (r == null) return;
        if (!r.canBuild(p.getUniqueId())) {
            e.setCancelled(true);
            deny(p, r);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        if (e.getBlockClicked() == null) return;
        checkBucket(e.getPlayer(), e.getBlockClicked().getWorld().getName(),
                e.getBlockClicked().getX(), e.getBlockClicked().getY(), e.getBlockClicked().getZ(), e);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (e.getBlockClicked() == null) return;
        checkBucket(e.getPlayer(), e.getBlockClicked().getWorld().getName(),
                e.getBlockClicked().getX(), e.getBlockClicked().getY(), e.getBlockClicked().getZ(), e);
    }

    private void checkBucket(Player p, String world, int x, int y, int z,
                             org.bukkit.event.Cancellable e) {
        if (bypass(p)) return;
        Region r = plugin.storage().at(world, x, y, z);
        if (r == null) return;
        if (!r.canBuild(p.getUniqueId())) {
            e.setCancelled(true);
            deny(p, r);
        }
    }

    // ---------------- interact ----------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        // RIGHT click only: left click is covered by BlockBreakEvent (avoids double deny),
        // PHYSICAL (pressure plates) stays usable for everyone
        if (e.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        if (bypass(p)) return;
        Block b = e.getClickedBlock();
        if (b == null || !isProtectedUse(b.getType())) return;
        // wand handles its own clicks
        if (p.getInventory().getItemInMainHand().getType() == plugin.cfg().wand) return;
        Region r = plugin.storage().at(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
        if (r == null) return;
        if (!r.canBuild(p.getUniqueId())) {
            e.setCancelled(true);
            deny(p, r);
        }
    }

    /** Containers, doors, buttons, workstations... */
    private boolean isProtectedUse(Material m) {
        String n = m.name();
        return n.contains("CHEST") || n.equals("BARREL") || n.equals("ENDER_CHEST")
                || n.contains("FURNACE") || n.contains("DOOR") || n.contains("GATE")
                || n.contains("BUTTON") || n.contains("LEVER") || n.contains("BED")
                || n.equals("ANVIL") || n.equals("CHIPPED_ANVIL") || n.equals("DAMAGED_ANVIL")
                || n.equals("ENCHANTING_TABLE") || n.equals("BREWING_STAND")
                || n.equals("HOPPER") || n.equals("DROPPER") || n.equals("DISPENSER")
                || n.equals("CRAFTER") || n.equals("JUKEBOX") || n.equals("LECTERN")
                || n.equals("COMPOSTER") || n.equals("BELL") || n.equals("CAKE")
                || n.equals("RESPAWN_ANCHOR") || n.equals("LODESTONE")
                || n.contains("SHULKER_BOX") || n.equals("CRAFTING_TABLE")
                || n.equals("CARTOGRAPHY_TABLE") || n.equals("FLETCHING_TABLE")
                || n.equals("GRINDSTONE") || n.equals("LOOM") || n.equals("SMITHING_TABLE")
                || n.equals("STONECUTTER") || n.equals("BEACON") || n.equals("DRAGON_EGG")
                || n.contains("CANDLE") || n.contains("FLOWER_POT");
    }

    // ---------------- pvp ----------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Player attacker = null;
        if (e.getDamager() instanceof Player p) {
            attacker = p;
        } else if (e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player p) {
            attacker = p;
        }
        if (attacker == null) return;
        if (attacker.getUniqueId().equals(victim.getUniqueId())) return;
        Region r = plugin.storage().at(victim.getWorld().getName(),
                victim.getLocation().getBlockX(), victim.getLocation().getBlockY(), victim.getLocation().getBlockZ());
        if (r == null) return;
        if (!r.pvp) {
            e.setCancelled(true);
            attacker.sendMessage(Msg.color(plugin.cfg().prefix + "&cPVP выключено в регионе &f" + r.name));
        }
    }

    // ---------------- explosions ----------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        Region r = plugin.storage().at(e.getLocation().getWorld().getName(),
                e.getLocation().getBlockX(), e.getLocation().getBlockY(), e.getLocation().getBlockZ());
        if (r != null && !r.explosions) {
            e.blockList().clear();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        Block b = e.getBlock();
        Region r = plugin.storage().at(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
        if (r != null && !r.explosions) {
            e.blockList().clear();
        }
    }

}
