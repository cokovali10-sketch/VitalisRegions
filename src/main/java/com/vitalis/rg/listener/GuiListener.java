package com.vitalis.rg.listener;

import com.vitalis.rg.Regions;
import com.vitalis.rg.gui.RegionGui;
import com.vitalis.rg.model.Region;
import com.vitalis.rg.select.SelectionManager;
import com.vitalis.rg.util.Msg;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/** Info GUI clicks, chat nick input (+/-), wand selection. */
public class GuiListener implements Listener {
    private final Regions plugin;

    public GuiListener(Regions plugin) {
        this.plugin = plugin;
    }

    private void clickSound(Player p) {
        if (plugin.cfg().sounds) p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1f);
    }

    // ---------------- wand ----------------

    @EventHandler
    public void onWand(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType() != plugin.cfg().wand) return;
        if (!p.hasPermission("rg.use")) return;
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
            p.sendMessage(Msg.color(plugin.cfg().prefix + "&7Готово → &e/rg create <имя>"));
        }
    }

    private String xyz(Block b) {
        return b.getX() + ", " + b.getY() + ", " + b.getZ();
    }

    // ---------------- GUI ----------------

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof RegionGui.InfoHolder) e.setCancelled(true);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!(e.getInventory().getHolder() instanceof RegionGui.InfoHolder)) return;
        e.setCancelled(true);
        String regionName = plugin.gui().openRegion(p);
        if (regionName == null) return;
        Region r = plugin.storage().get(regionName);
        if (r == null) {
            p.closeInventory();
            return;
        }
        boolean owner = r.isOwner(p.getUniqueId());
        int slot = e.getRawSlot();
        if (!owner) {
            // members/guests: view only, can only close
            if (slot != 34) {
                p.sendMessage(Msg.color(plugin.cfg().prefix + "&cТолько владелец меняет настройки (режим просмотра)."));
            } else {
                p.closeInventory();
            }
            return;
        }
        switch (slot) {
            case 10 -> askInput(p, r, RegionGui.InputKind.ADD_OWNER, "&eВведи ник нового владельца в чат:");
            case 16 -> askInput(p, r, RegionGui.InputKind.DEL_OWNER, "&eВведи ник владельца для удаления:");
            case 19 -> askInput(p, r, RegionGui.InputKind.ADD_MEMBER, "&eВведи ник игрока в чат:");
            case 25 -> askInput(p, r, RegionGui.InputKind.DEL_MEMBER, "&eВведи ник игрока для удаления:");
            case 28 -> {
                r.pvp = !r.pvp;
                plugin.storage().saveAsync();
                clickSound(p);
                plugin.gui().openInfo(p, r);
                p.sendMessage(Msg.color(plugin.cfg().prefix + "&7PVP: " + (r.pvp ? "&aВКЛ" : "&cВЫКЛ")));
            }
            case 30 -> {
                r.explosions = !r.explosions;
                plugin.storage().saveAsync();
                clickSound(p);
                plugin.gui().openInfo(p, r);
                p.sendMessage(Msg.color(plugin.cfg().prefix + "&7Взрывы: " + (r.explosions ? "&aВКЛ" : "&cВЫКЛ")));
            }
            case 32 -> {
                if (plugin.gui().confirmDelete(p, r.name, 15000L)) {
                    plugin.storage().remove(r.name);
                    plugin.storage().saveAsync();
                    p.sendMessage(Msg.color(plugin.cfg().prefix + "&aРегион &f" + r.name + " &aудалён."));
                    p.closeInventory();
                } else {
                    clickSound(p);
                    p.sendMessage(Msg.color(plugin.cfg().prefix + "&cКликни удаление ещё раз для подтверждения (15 сек)."));
                }
            }
            case 34 -> p.closeInventory();
            default -> {}
        }
    }

    private void askInput(Player p, Region r, RegionGui.InputKind kind, String prompt) {
        p.closeInventory();
        plugin.gui().markInput(p, r.name, kind);
        p.sendMessage(Msg.color(plugin.cfg().prefix + prompt));
        p.sendMessage(Msg.color(plugin.cfg().prefix + "&7Пустое сообщение = отмена. Есть 30 сек."));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (plugin.gui().takeInput(p) != null) {
                p.sendMessage(Msg.color(plugin.cfg().prefix + "&7Ввод отменён (таймаут)."));
            }
        }, 30L * 20L);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        // keep open-region mapping until new info opened; harmless
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.gui().cancelInput(e.getPlayer());
    }

    // ---------------- chat nick input ----------------

    @EventHandler
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        RegionGui.PendingInput in = plugin.gui().takeInput(p);
        if (in == null) return;
        e.setCancelled(true);
        String nick = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> applyInput(p, in, nick));
    }

    @SuppressWarnings("deprecation")
    private void applyInput(Player p, RegionGui.PendingInput in, String nick) {
        if (nick.isEmpty()) {
            p.sendMessage(Msg.color(plugin.cfg().prefix + "&7Отменено."));
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
        OfflinePlayer op = Bukkit.getOfflinePlayer(nick);
        if (!op.hasPlayedBefore() && !op.isOnline()) {
            p.sendMessage(Msg.color(plugin.cfg().prefix + "&cИгрок &f" + nick + " &cне найден (ник с ошибкой?)."));
            return;
        }
        UUID uuid = op.getUniqueId();
        String realName = op.getName() != null ? op.getName() : nick;
        switch (in.kind()) {
            case ADD_OWNER -> {
                if (r.owners.containsKey(uuid)) {
                    p.sendMessage(Msg.color(plugin.cfg().prefix + "&e" + realName + " &7уже владелец."));
                } else {
                    r.owners.put(uuid, realName);
                    r.members.remove(uuid);
                    plugin.storage().saveAsync();
                    p.sendMessage(Msg.color(plugin.cfg().prefix + "&a" + realName + " &7теперь владелец &f" + r.name));
                }
            }
            case DEL_OWNER -> {
                if (uuid.equals(r.owner)) {
                    p.sendMessage(Msg.color(plugin.cfg().prefix + "&cСоздателя убрать нельзя. Удали регион целиком."));
                } else if (r.owners.remove(uuid) != null) {
                    plugin.storage().saveAsync();
                    p.sendMessage(Msg.color(plugin.cfg().prefix + "&e" + realName + " &7больше не владелец."));
                } else {
                    p.sendMessage(Msg.color(plugin.cfg().prefix + "&7Такого владельца нет."));
                }
            }
            case ADD_MEMBER -> {
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
            case DEL_MEMBER -> {
                if (r.members.remove(uuid) != null) {
                    plugin.storage().saveAsync();
                    p.sendMessage(Msg.color(plugin.cfg().prefix + "&e" + realName + " &7убран из региона."));
                } else {
                    p.sendMessage(Msg.color(plugin.cfg().prefix + "&7Такого игрока нет."));
                }
            }
        }
        clickSound(p);
        plugin.gui().openInfo(p, r);
    }
}
