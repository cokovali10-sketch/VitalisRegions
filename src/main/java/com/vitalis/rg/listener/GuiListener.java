package com.vitalis.rg.listener;

import com.vitalis.rg.Regions;
import com.vitalis.rg.menu.ChatMenu;
import com.vitalis.rg.model.Region;
import com.vitalis.rg.select.SelectionManager;
import com.vitalis.rg.util.Msg;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Wand selection, chat nick/name input, nickname refresh. No GUI windows. */
public class GuiListener implements Listener {
    private final Regions plugin;

    public GuiListener(Regions plugin) {
        this.plugin = plugin;
    }

    // ---------------- wand ----------------

    @EventHandler
    public void onWand(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType() != plugin.cfg().wand) return;
        if (!com.vitalis.rg.util.Perms.has(p, "use")) return;
        Block b = e.getClickedBlock();
        if (b == null) return;
        SelectionManager.Selection s = plugin.selections().get(p.getUniqueId());
        if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
            e.setCancelled(true);
            s.pos1 = b.getLocation().clone();
            p.sendMessage(Msg.color(plugin.cfg().prefix + "&aТочка 1: &f" + xyz(b)));
        } else if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            e.setCancelled(true);
            s.pos2 = b.getLocation().clone();
            p.sendMessage(Msg.color(plugin.cfg().prefix + "&aТочка 2: &f" + xyz(b)));
        } else {
            return;
        }
        if (s.complete()) {
            p.sendMessage(Msg.color(plugin.cfg().prefix + "&7Готово → &e/area &7(кнопка создания)"));
        }
    }

    private String xyz(Block b) {
        return b.getX() + ", " + b.getY() + ", " + b.getZ();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.menus().cancelInput(e.getPlayer());
    }

    /** Nicknames change — refresh stored display names on join (keys stay UUID). */
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        boolean changed = false;
        for (Region r : plugin.storage().all()) {
            if (r.owner.equals(p.getUniqueId()) && !p.getName().equals(r.ownerName)) {
                r.ownerName = p.getName();
                changed = true;
            }
            if (r.owners.containsKey(p.getUniqueId()) && !p.getName().equals(r.owners.get(p.getUniqueId()))) {
                r.owners.put(p.getUniqueId(), p.getName());
                changed = true;
            }
            if (r.members.containsKey(p.getUniqueId()) && !p.getName().equals(r.members.get(p.getUniqueId()))) {
                r.members.put(p.getUniqueId(), p.getName());
                changed = true;
            }
        }
        if (changed) plugin.storage().saveAsync();
    }

    // ---------------- chat input ----------------
    // ignoreCancelled: muted/blocked messages must not be eaten as nick input

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        ChatMenu.Pending in = plugin.menus().takeInput(p);
        if (in == null) return;
        e.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> applyInput(p, in, text));
    }

    /** Chat fallback (when dialog windows are unavailable): same shared logic. */
    private void applyInput(Player p, ChatMenu.Pending in, String text) {
        if (text.isEmpty()) {
            p.sendMessage(Msg.color(plugin.cfg().prefix + "&7Отменено."));
            return;
        }
        switch (in.kind()) {
            case CREATE_NAME -> plugin.commands().createFromChat(p, text, in.vert());
            case RENAME -> plugin.commands().applyRename(p, in.region(), text);
            case ADD_OWNER -> plugin.commands().applyAdd(p, in.region(), text, true);
            case ADD_MEMBER -> plugin.commands().applyAdd(p, in.region(), text, false);
            case DEL_OWNER -> plugin.commands().applyDelNick(p, in.region(), text, true);
            case DEL_MEMBER -> plugin.commands().applyDelNick(p, in.region(), text, false);
        }
    }
}
