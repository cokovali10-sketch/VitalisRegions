package com.vitalis.rg.listener;

import com.vitalis.rg.Regions;
import com.vitalis.rg.menu.ChatMenu;
import com.vitalis.rg.model.Region;
import com.vitalis.rg.select.SelectionManager;
import com.vitalis.rg.util.Msg;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
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

import java.util.UUID;

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

    @EventHandler
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        ChatMenu.Pending in = plugin.menus().takeInput(p);
        if (in == null) return;
        e.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> applyInput(p, in, text));
    }

    @SuppressWarnings("deprecation")
    private void applyInput(Player p, ChatMenu.Pending in, String text) {
        if (text.isEmpty()) {
            p.sendMessage(Msg.color(plugin.cfg().prefix + "&7Отменено."));
            return;
        }
        if (in.kind() == ChatMenu.InputKind.CREATE_NAME) {
            plugin.commands().createFromChat(p, text);
            return;
        }
        Region r = plugin.storage().get(in.region());
        if (r == null) {
            p.sendMessage(Msg.color(plugin.cfg().prefix + "&cРегион уже удалён."));
            return;
        }
        if (!r.isOwner(p.getUniqueId())) {
            p.sendMessage(Msg.color(plugin.cfg().prefix + "&cТолько владелец."));
            return;
        }
        OfflinePlayer op = Bukkit.getOfflinePlayer(text);
        if (!op.hasPlayedBefore() && !op.isOnline()) {
            p.sendMessage(Msg.color(plugin.cfg().prefix + "&cИгрок &f" + text + " &cне найден (ник с ошибкой?)."));
            return;
        }
        UUID uuid = op.getUniqueId();
        String realName = op.getName() != null ? op.getName() : text;
        if (in.kind() == ChatMenu.InputKind.ADD_OWNER) {
            if (r.owners.containsKey(uuid)) {
                p.sendMessage(Msg.color(plugin.cfg().prefix + "&e" + realName + " &7уже владелец."));
            } else {
                r.owners.put(uuid, realName);
                r.members.remove(uuid);
                plugin.storage().saveAsync();
                p.sendMessage(Msg.color(plugin.cfg().prefix + "&a" + realName + " &7теперь владелец &f" + r.name));
            }
        } else if (in.kind() == ChatMenu.InputKind.DEL_OWNER) {
            if (uuid.equals(r.owner)) {
                p.sendMessage(Msg.color(plugin.cfg().prefix + "&cСоздателя убрать нельзя."));
            } else if (r.owners.remove(uuid) != null) {
                plugin.storage().saveAsync();
                p.sendMessage(Msg.color(plugin.cfg().prefix + "&e" + realName + " &7больше не владелец."));
            } else {
                p.sendMessage(Msg.color(plugin.cfg().prefix + "&7Такого владельца нет."));
            }
        } else if (in.kind() == ChatMenu.InputKind.DEL_MEMBER) {
            if (r.members.remove(uuid) != null) {
                plugin.storage().saveAsync();
                p.sendMessage(Msg.color(plugin.cfg().prefix + "&e" + realName + " &7убран из региона."));
            } else {
                p.sendMessage(Msg.color(plugin.cfg().prefix + "&7Такого игрока нет."));
            }
        } else {
            if (r.isOwner(uuid)) {
                p.sendMessage(Msg.color(plugin.cfg().prefix + "&e" + realName + " &7уже владелец."));
            } else if (r.members.containsKey(uuid)) {
                p.sendMessage(Msg.color(plugin.cfg().prefix + "&e" + realName + " &7уже в списке."));
            } else {
                r.members.put(uuid, realName);
                plugin.storage().saveAsync();
                p.sendMessage(Msg.color(plugin.cfg().prefix + "&a" + realName + " &7добавлен в &f" + r.name));
            }
        }
        if (plugin.cfg().sounds) {
            p.playSound(p.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.7f, 1f);
        }
        plugin.menus().regionMenu(p, r);
    }
}
