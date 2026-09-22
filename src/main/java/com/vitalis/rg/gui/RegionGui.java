package com.vitalis.rg.gui;

import com.vitalis.rg.config.RgConfig;
import com.vitalis.rg.model.Region;
import com.vitalis.rg.storage.RegionStorage;
import com.vitalis.rg.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

/** Region info GUI (36 slots): owners/members with +/- via chat, flag toggles, delete. */
public class RegionGui {

    public enum InputKind { ADD_OWNER, DEL_OWNER, ADD_MEMBER, DEL_MEMBER }

    public record PendingInput(String region, InputKind kind) {}

    private final RgConfig cfg;
    private final RegionStorage storage;

    /** viewer -> open region name */
    private final Map<UUID, String> open = new HashMap<>();
    /** viewer -> chat input waiting */
    private final Map<UUID, PendingInput> inputs = new HashMap<>();
    /** viewer -> region awaiting delete confirm + deadline */
    private final Map<UUID, Long> deleteConfirm = new HashMap<>();

    public RegionGui(RgConfig cfg, RegionStorage storage) {
        this.cfg = cfg;
        this.storage = storage;
    }

    public String openRegion(Player p) {
        return open.get(p.getUniqueId());
    }

    public void markInput(Player p, String region, InputKind kind) {
        inputs.put(p.getUniqueId(), new PendingInput(region, kind));
    }

    public PendingInput takeInput(Player p) {
        return inputs.remove(p.getUniqueId());
    }

    public void cancelInput(Player p) {
        inputs.remove(p.getUniqueId());
        deleteConfirm.remove(p.getUniqueId());
    }

    public boolean confirmDelete(Player p, String region, long windowMs) {
        Long until = deleteConfirm.get(p.getUniqueId());
        long now = System.currentTimeMillis();
        if (until != null && until > now && open.getOrDefault(p.getUniqueId(), "").equalsIgnoreCase(region)) {
            deleteConfirm.remove(p.getUniqueId());
            return true;
        }
        deleteConfirm.put(p.getUniqueId(), now + windowMs);
        return false;
    }

    // ---------------- info window ----------------

    public void openInfo(Player p, Region r) {
        open.put(p.getUniqueId(), r.name);
        Inventory inv = Bukkit.createInventory(new InfoHolder(), 36,
                Msg.color(cfg.titleInfo.replace("{name}", r.name)));

        ItemStack fill = filler();
        for (int s = 0; s < 36; s++) inv.setItem(s, fill);

        inv.setItem(4, button(Material.BOOK, "&e" + r.name,
                "&7Мир: &f" + r.world,
                "&7Размер: &f" + r.sizeX() + "x" + r.sizeY() + "x" + r.sizeZ(),
                "&7Углы: &f" + r.minX + "," + r.minY + "," + r.minZ + " &7— &f" + r.maxX + "," + r.maxY + "," + r.maxZ,
                "&7Владелец: &f" + r.ownerName));

        // owners row
        inv.setItem(9, button(Material.PAPER, "&6Владельцы: &e" + r.owners.size()));
        inv.setItem(10, button(Material.LIME_WOOL, "&a+ Добавить",
                "&7Клик — ввести ник в чат"));
        int slot = 11;
        for (Map.Entry<UUID, String> e : r.owners.entrySet()) {
            if (slot > 15) break;
            inv.setItem(slot++, head(e.getKey(), e.getValue(), "&6Владелец"));
        }
        if (r.owners.size() > 5) {
            inv.setItem(15, button(Material.PAPER, "&7... и ещё " + (r.owners.size() - 4),
                    "&7Показаны первые 4"));
        }
        inv.setItem(16, button(Material.RED_WOOL, "&c− Убрать",
                "&7Клик — ввести ник в чат"));

        // members row
        inv.setItem(18, button(Material.PAPER, "&bИгроки: &e" + r.members.size()));
        inv.setItem(19, button(Material.LIME_WOOL, "&a+ Добавить",
                "&7Клик — ввести ник в чат"));
        slot = 20;
        List<Map.Entry<UUID, String>> members = new ArrayList<>(r.members.entrySet());
        for (Map.Entry<UUID, String> e : members) {
            if (slot > 24) break;
            inv.setItem(slot++, head(e.getKey(), e.getValue(), "&bИгрок"));
        }
        if (r.members.size() > 5) {
            inv.setItem(24, button(Material.PAPER, "&7... и ещё " + (r.members.size() - 4),
                    "&7Показаны первые 4"));
        }
        inv.setItem(25, button(Material.RED_WOOL, "&c− Убрать",
                "&7Клик — ввести ник в чат"));

        // flags row: green = on, red/gray = off
        inv.setItem(27, button(Material.FIRE_CHARGE,
                r.fireSpread ? "&aОгонь: ВКЛ" : "&cОгонь: ВЫКЛ",
                "&7Распространение огня", "&7Клик — переключить"));
        inv.setItem(28, button(r.pvp ? Material.REDSTONE_TORCH : Material.LEVER,
                r.pvp ? "&aPVP: ВКЛ" : "&cPVP: ВЫКЛ",
                "&7Клик — переключить"));
        inv.setItem(29, button(Material.TNT,
                r.explosions ? "&aВзрывы: ВКЛ" : "&cВзрывы: ВЫКЛ",
                "&7Клик — переключить"));
        inv.setItem(30, button(Material.REPEATER,
                r.mechanisms ? "&aМеханизмы: ВКЛ" : "&cМеханизмы: ВЫКЛ",
                "&7Редстоун и раздатчики", "&7Клик — переключить"));
        inv.setItem(31, button(Material.RED_BED,
                r.homes ? "&aХомы: ВКЛ" : "&cХомы: ВЫКЛ",
                "&7Чужие могут ставить /sethome", "&7Клик — переключить"));
        inv.setItem(32, button(Material.REDSTONE_BLOCK, "&4Удалить регион",
                "&7Клик — запросить удаление", "&7Потом клик ещё раз для подтверждения"));
        inv.setItem(34, button(Material.BARRIER, "&cЗакрыть"));
        inv.setItem(35, button(Material.BOOK, "&eМои регионы",
                "&7Клик — список твоих регионов"));
        p.openInventory(inv);
    }

    private ItemStack head(UUID uuid, String name, String role) {
        ItemStack it = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta m = (SkullMeta) it.getItemMeta();
        try {
            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            m.setOwningPlayer(op);
        } catch (Exception ignored) {}
        m.displayName(Msg.color("&f" + name));
        m.lore(List.of(Msg.color(role)));
        it.setItemMeta(m);
        return it;
    }

    private ItemStack filler() {
        ItemStack it = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = it.getItemMeta();
        m.displayName(Msg.color("&8 "));
        it.setItemMeta(m);
        return it;
    }

    private ItemStack button(Material mat, String name, String... loreLines) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.displayName(Msg.color(name));
        if (loreLines != null && loreLines.length > 0) {
            List<Component> lore = new ArrayList<>();
            for (String s : loreLines) {
                if (s != null) lore.add(Msg.color(s));
            }
            m.lore(lore);
        }
        it.setItemMeta(m);
        return it;
    }

    public static class InfoHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }
}
