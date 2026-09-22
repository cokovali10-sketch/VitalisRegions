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
        return com.vitalis.rg.util.Perms.has(p, "bypass") || p.isOp();
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
    // MECH (buttons, levers, doors, hatches, gates...): outsiders use them only if flag ON.
    // LOCKED (chests, workstations...): owners/members only, always.

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        // RIGHT click only: left click is covered by BlockBreakEvent (avoids double deny),
        // PHYSICAL (pressure plates) stays usable for everyone
        if (e.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        if (bypass(p)) return;
        Block b = e.getClickedBlock();
        if (b == null) return;
        com.vitalis.rg.util.Interact.Kind kind =
                com.vitalis.rg.util.Interact.of(b.getType());
        if (kind == com.vitalis.rg.util.Interact.Kind.OPEN) return;
        // wand handles its own clicks
        if (p.getInventory().getItemInMainHand().getType() == plugin.cfg().wand) return;
        Region r = plugin.storage().at(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
        if (r == null) return;
        if (r.canBuild(p.getUniqueId())) return;
        if (kind == com.vitalis.rg.util.Interact.Kind.MECH && r.mechanisms) return;
        e.setCancelled(true);
        deny(p, r);
    }

    // ---------------- trespass ----------------
    // Frames/armor stands on чужой walls + pearl/chorus jumps inside.

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingPlace(org.bukkit.event.hanging.HangingPlaceEvent e) {
        Player p = e.getPlayer();
        if (p == null || bypass(p)) return;
        Block b = e.getBlock();
        Region r = plugin.storage().at(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
        if (r == null) return;
        if (!r.canBuild(p.getUniqueId())) {
            e.setCancelled(true);
            deny(p, r);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleportIn(org.bukkit.event.player.PlayerTeleportEvent e) {
        org.bukkit.event.player.PlayerTeleportEvent.TeleportCause cause = e.getCause();
        if (cause != org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.ENDER_PEARL
                && cause != org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT) return;
        Player p = e.getPlayer();
        if (bypass(p)) return;
        org.bukkit.Location to = e.getTo();
        if (to == null || to.getWorld() == null) return;
        Region r = plugin.storage().at(to.getWorld().getName(),
                to.getBlockX(), to.getBlockY(), to.getBlockZ());
        if (r == null) return;
        if (!r.canBuild(p.getUniqueId())) {
            e.setCancelled(true);
            p.sendMessage(Msg.color(plugin.cfg().prefix + "&cНельзя попасть в регион &f" + r.name + " &cтаким способом."));
        }
    }

    // ---------------- entities & co ----------------
    // Punching frames/paintings, stripping armor stands, breaking boats,
    // shearing чужой sheep — all of it is grief too.

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreak(org.bukkit.event.hanging.HangingBreakByEntityEvent e) {
        if (!(e.getRemover() instanceof Player p) || bypass(p)) return;
        org.bukkit.Location loc = e.getEntity().getLocation();
        Region r = plugin.storage().at(loc.getWorld().getName(),
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        if (r == null) return;
        if (!r.canBuild(p.getUniqueId())) {
            e.setCancelled(true);
            deny(p, r);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArmorStand(org.bukkit.event.player.PlayerArmorStandManipulateEvent e) {
        Player p = e.getPlayer();
        if (bypass(p)) return;
        org.bukkit.Location loc = e.getRightClicked().getLocation();
        Region r = plugin.storage().at(loc.getWorld().getName(),
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        if (r == null) return;
        if (!r.canBuild(p.getUniqueId())) {
            e.setCancelled(true);
            deny(p, r);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVehicleBreak(org.bukkit.event.vehicle.VehicleDestroyEvent e) {
        if (!(e.getAttacker() instanceof Player p) || bypass(p)) return;
        org.bukkit.Location loc = e.getVehicle().getLocation();
        Region r = plugin.storage().at(loc.getWorld().getName(),
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        if (r == null) return;
        if (!r.canBuild(p.getUniqueId())) {
            e.setCancelled(true);
            deny(p, r);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityInteract(org.bukkit.event.player.PlayerInteractEntityEvent e) {
        Player p = e.getPlayer();
        if (bypass(p)) return;
        org.bukkit.Location loc = e.getRightClicked().getLocation();
        Region r = plugin.storage().at(loc.getWorld().getName(),
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        if (r == null) return;
        if (!r.canBuild(p.getUniqueId())) {
            e.setCancelled(true);
            deny(p, r);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTrample(PlayerInteractEvent e) {
        // PHYSICAL = pressure plates (free) + farmland trample (deny for outsiders)
        if (e.getAction() != org.bukkit.event.block.Action.PHYSICAL) return;
        Player p = e.getPlayer();
        if (bypass(p)) return;
        Block b = e.getClickedBlock();
        if (b == null || b.getType() != Material.FARMLAND) return;
        Region r = plugin.storage().at(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
        if (r == null) return;
        if (!r.canBuild(p.getUniqueId())) {
            e.setCancelled(true);
        }
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
    // Full cancel: no crater AND no entity damage (otherwise creepers still hurt through the flag)

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        Region r = plugin.storage().at(e.getLocation().getWorld().getName(),
                e.getLocation().getBlockX(), e.getLocation().getBlockY(), e.getLocation().getBlockZ());
        if (r != null && !r.explosions) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        Block b = e.getBlock();
        Region r = plugin.storage().at(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
        if (r != null && !r.explosions) {
            e.setCancelled(true);
        }
    }

    // ---------------- pistons ----------------
    // Blocks must not cross region borders (classic piston grief in/out).

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(org.bukkit.event.block.BlockPistonExtendEvent e) {
        Block piston = e.getBlock();
        String home = regionNameAt(piston);
        for (Block moved : e.getBlocks()) {
            Block dest = moved.getRelative(e.getDirection());
            if (!sameRegion(home, regionNameAt(moved)) || !sameRegion(home, regionNameAt(dest))) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(org.bukkit.event.block.BlockPistonRetractEvent e) {
        if (!e.isSticky()) return;
        Block piston = e.getBlock();
        String home = regionNameAt(piston);
        for (Block moved : e.getBlocks()) {
            if (!sameRegion(home, regionNameAt(moved))) {
                e.setCancelled(true);
                return;
            }
        }
    }

    private String regionNameAt(Block b) {
        Region r = plugin.storage().at(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
        return r == null ? null : r.name.toLowerCase(java.util.Locale.ROOT);
    }

    private boolean sameRegion(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    // ---------------- fire / lava / nature ----------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onIgnite(org.bukkit.event.block.BlockIgniteEvent e) {
        Block b = e.getBlock();
        Region r = plugin.storage().at(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
        if (r == null) return;
        if (e.getCause() == org.bukkit.event.block.BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL
                || e.getCause() == org.bukkit.event.block.BlockIgniteEvent.IgniteCause.FIREBALL) {
            Player p = e.getPlayer();
            if (p != null && !bypass(p) && !r.canBuild(p.getUniqueId())) {
                e.setCancelled(true);
                deny(p, r);
                return;
            }
        }
        // lightning / lava / spread fire never starts inside a region
        if (e.getPlayer() == null) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBurn(org.bukkit.event.block.BlockBurnEvent e) {
        // burning block itself destroyed by fire: forbidden while the flag is OFF
        // (covers eternal fire / pre-lit blocks, not just new ignition)
        Block b = e.getBlock();
        Region r = plugin.storage().at(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
        if (r != null && !r.fireSpread) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFireSpread(org.bukkit.event.block.BlockSpreadEvent e) {
        Block target = e.getNewState().getBlock();
        Region r = plugin.storage().at(target.getWorld().getName(),
                target.getX(), target.getY(), target.getZ());
        if (r == null || r.fireSpread) return;
        Block source = e.getSource();
        if (!sameRegion(r.name.toLowerCase(java.util.Locale.ROOT), regionNameAt(source))) {
            e.setCancelled(true);
        }
    }

    // ---------------- mechanisms flag ----------------
    // OFF = redstone frozen + dispensers silent. Pistons stay always guarded (see above).

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRedstone(org.bukkit.event.block.BlockRedstoneEvent e) {
        Block b = e.getBlock();
        Region r = plugin.storage().at(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
        if (r != null && !r.mechanisms) {
            e.setNewCurrent(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDispense(org.bukkit.event.block.BlockDispenseEvent e) {
        Block b = e.getBlock();
        Region r = plugin.storage().at(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
        if (r != null && !r.mechanisms) {
            e.setCancelled(true);
        }
    }

    // ---------------- homes flag ----------------
    // OFF = outsiders cannot /sethome here (Essentials + aliases).

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSetHome(org.bukkit.event.player.PlayerCommandPreprocessEvent e) {
        String msg = e.getMessage().toLowerCase(java.util.Locale.ROOT);
        if (!msg.equals("/sethome") && !msg.startsWith("/sethome ")
                && !msg.equals("/esethome") && !msg.startsWith("/esethome ")
                && !msg.equals("/essentials:sethome") && !msg.startsWith("/essentials:sethome ")) return;
        Player p = e.getPlayer();
        if (bypass(p)) return;
        org.bukkit.Location loc = p.getLocation();
        Region r = plugin.storage().at(loc.getWorld().getName(),
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        if (r == null || r.homes) return;
        if (!r.canBuild(p.getUniqueId())) {
            e.setCancelled(true);
            p.sendMessage(Msg.color(plugin.cfg().prefix + "&cВ регионе &f" + r.name + " &cставить хомы запрещено."));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLiquidFlow(org.bukkit.event.block.BlockFromToEvent e) {
        Block target = e.getToBlock();
        String t = regionNameAt(target);
        // water/lava may flow inside one region or in the wild, but never INTO a чужой region
        if (t != null && !sameRegion(t, regionNameAt(e.getBlock()))) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEndermanGrief(org.bukkit.event.entity.EntityChangeBlockEvent e) {
        if (e.getEntityType() != org.bukkit.entity.EntityType.ENDERMAN) return;
        Block b = e.getBlock();
        if (plugin.storage().at(b.getWorld().getName(), b.getX(), b.getY(), b.getZ()) != null) {
            e.setCancelled(true);
        }
    }

}
