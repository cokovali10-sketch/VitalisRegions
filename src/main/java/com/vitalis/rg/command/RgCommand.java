package com.vitalis.rg.command;

import com.vitalis.rg.Regions;
import com.vitalis.rg.model.Region;
import com.vitalis.rg.select.SelectionManager;
import com.vitalis.rg.util.Msg;
import com.vitalis.rg.util.Perms;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * /area help inline: pos1, pos2, wand, create, delete, list, info.
 * Members/owners/flags live in the info GUI (+/- buttons + chat input).
 */
public class RgCommand implements CommandExecutor, TabCompleter {
    private final Regions plugin;
    /** player -> region awaiting delete confirm + deadline */
    private final Map<UUID, Long> deleteConfirm = new HashMap<>();

    public RgCommand(Regions plugin) {
        this.plugin = plugin;
    }

    private void send(Player p, String msg) {
        p.sendMessage(Msg.color(plugin.cfg().prefix + msg));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Only players.");
            return true;
        }
        if (!Perms.has(p, "use")) {
            send(p, "&cНет доступа.");
            return true;
        }
        if (args.length == 0) {
            help(p);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "pos1" -> {
                SelectionManager.Selection s = plugin.selections().get(p.getUniqueId());
                s.pos1 = p.getLocation().clone();
                send(p, "&aТочка 1: &f" + xyz(s.pos1));
                hintComplete(p, s);
            }
            case "pos2" -> {
                SelectionManager.Selection s = plugin.selections().get(p.getUniqueId());
                s.pos2 = p.getLocation().clone();
                send(p, "&aТочка 2: &f" + xyz(s.pos2));
                hintComplete(p, s);
            }
            case "wand" -> {
                if (hasWand(p)) {
                    send(p, "&eЖезл уже в инвентаре: &fЛКМ — точка 1, ПКМ — точка 2");
                    return true;
                }
                ItemStack wand = new ItemStack(plugin.cfg().wand);
                ItemMeta m = wand.getItemMeta();
                m.displayName(Msg.color("&aЖезл региона"));
                m.lore(List.of(Msg.color("&7ЛКМ — точка 1, ПКМ — точка 2")));
                wand.setItemMeta(m);
                Map<Integer, ItemStack> left = p.getInventory().addItem(wand);
                for (ItemStack l : left.values()) p.getWorld().dropItemNaturally(p.getLocation(), l);
                send(p, "&aЖезл выдан: &fЛКМ — точка 1, ПКМ — точка 2");
            }
            case "create" -> handleCreate(p, args);
            case "delete", "remove", "del" -> handleDelete(p, args);
            case "list" -> handleList(p);
            case "info" -> {
                if (args.length < 2) {
                    send(p, "&eИспользование: &f/area info <название>");
                    return true;
                }
                Region r = plugin.storage().get(args[1]);
                if (r == null) {
                    send(p, "&cРегиона &f" + args[1] + " &cнет.");
                    return true;
                }
                plugin.gui().openInfo(p, r);
            }
            case "reload" -> {
                if (!Perms.has(p, "admin")) {
                    send(p, "&cНет доступа.");
                    return true;
                }
                plugin.cfg().load(plugin);
                send(p, "&aКонфиг перезагружен (регионы не тронуты).");
            }
            default -> help(p);
        }
        return true;
    }

    /** Our wand = same material + same name (avoids duplicates). */
    private boolean hasWand(Player p) {
        for (ItemStack it : p.getInventory().getContents()) {
            if (it == null || it.getType() != plugin.cfg().wand) continue;
            if (!it.hasItemMeta() || !it.getItemMeta().hasDisplayName()) continue;
            if (Msg.plain(it.getItemMeta().displayName()).equals("Жезл региона")) return true;
        }
        return false;
    }

    private void help(Player p) {
        send(p, "<gradient:#5DFF6E:#00E5FF>Регионы</gradient> <gray>— команды (клик — вставить в чат):");
        cmdLine(p, "/area pos1", "точка 1 (где стоишь)");
        cmdLine(p, "/area pos2", "точка 2 (где стоишь)");
        cmdLine(p, "/area wand", "жезл: ЛКМ — точка 1, ПКМ — точка 2");
        cmdLine(p, "/area create <имя>", "создать (+ vert = от bedrock до неба)");
        cmdLine(p, "/area delete <имя>", "удалить (повторить 2 раза)");
        cmdLine(p, "/area list", "твои регионы (клик — открыть)");
        cmdLine(p, "/area info <имя>", "окно: владельцы/игроки +/-, флаги");
        if (Perms.has(p, "admin")) {
            cmdLine(p, "/area reload", "перезагрузить конфиг");
        }
    }

    /** Clickable chat line: click inserts the command. */
    private void cmdLine(Player p, String command, String desc) {
        p.sendMessage(Msg.color(plugin.cfg().prefix + "&e" + command + " &7— " + desc + " ")
                .append(Component.text("[вставить]")
                        .color(NamedTextColor.GREEN)
                        .clickEvent(ClickEvent.suggestCommand(command + " "))
                        .hoverEvent(HoverEvent.showText(Component.text("Клик — вставить в чат")))));
    }

    private String xyz(Location l) {
        return l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ()
                + " (" + l.getWorld().getName() + ")";
    }

    private void hintComplete(Player p, SelectionManager.Selection s) {
        if (s.complete()) {
            Region tmp = new Region("?", s.pos1.getWorld().getName(),
                    s.pos1.getBlockX(), s.pos1.getBlockY(), s.pos1.getBlockZ(),
                    s.pos2.getBlockX(), s.pos2.getBlockY(), s.pos2.getBlockZ(),
                    p.getUniqueId(), p.getName(), false, false, false, true, true);
            send(p, "&7Выделено: &f" + tmp.sizeX() + "x" + tmp.sizeY() + "x" + tmp.sizeZ());
            p.sendMessage(Msg.color(plugin.cfg().prefix + "&7Дальше: ")
                    .append(Component.text("[создать]")
                            .color(NamedTextColor.GREEN)
                            .clickEvent(ClickEvent.suggestCommand("/area create "))
                            .hoverEvent(HoverEvent.showText(Component.text("Введи имя. Добавь vert — растянет от bedrock до неба")))));
        }
    }

    private void handleCreate(Player p, String[] args) {
        if (!Perms.has(p, "create")) {
            send(p, "&cНет доступа.");
            return;
        }
        if (args.length < 2) {
            send(p, "&eИспользование: &f/area create <имя> [vert]");
            send(p, "&7vert — растянуть от bedrock до неба.");
            return;
        }
        String name = args[1];
        if (!name.matches("[a-zA-Z0-9_-]{3,16}")) {
            send(p, "&cИмя: 3-16 символов, латиница/цифры/_/-");
            return;
        }
        if (plugin.storage().get(name) != null) {
            send(p, "&cТакое имя уже занято.");
            return;
        }
        SelectionManager.Selection s = plugin.selections().get(p.getUniqueId());
        if (!s.complete()) {
            send(p, "&cСначала выдели точки: &e/area pos1 &cи &e/area pos2");
            return;
        }
        String world = s.pos1.getWorld().getName();
        if (plugin.cfg().blockedWorlds.contains(world.toLowerCase(Locale.ROOT))) {
            send(p, "&cВ этом мире создавать нельзя.");
            return;
        }
        // vert: stretch Y from bedrock to sky
        int y1 = s.pos1.getBlockY(), y2 = s.pos2.getBlockY();
        boolean vert = args.length >= 3 && (args[2].equalsIgnoreCase("vert")
                || args[2].equalsIgnoreCase("up") || args[2].equalsIgnoreCase("full"));
        if (vert) {
            y1 = s.pos1.getWorld().getMinHeight();
            y2 = s.pos1.getWorld().getMaxHeight() - 1;
        }
        Region r = new Region(name, world,
                s.pos1.getBlockX(), y1, s.pos1.getBlockZ(),
                s.pos2.getBlockX(), y2, s.pos2.getBlockZ(),
                p.getUniqueId(), p.getName(),
                plugin.cfg().defaultPvp, plugin.cfg().defaultExplosions,
                plugin.cfg().defaultFireSpread, plugin.cfg().defaultMechanisms,
                plugin.cfg().defaultHomes);

        if (!Perms.has(p, "unlimited")) {
            int mine = plugin.storage().countBy(p.getUniqueId());
            if (mine >= plugin.cfg().maxPerPlayer) {
                send(p, "&cЛимит регионов: &e" + mine + "/" + plugin.cfg().maxPerPlayer);
                return;
            }
        }
        for (Region o : plugin.storage().all()) {
            if (o.intersects(r)) {
                send(p, "&cПересекается с регионом &f" + o.name + " &c(" + o.ownerName + ")");
                return;
            }
        }
        double price = plugin.cfg().claimPrice;
        if (price > 0 && plugin.economy().ready()) {
            if (!plugin.economy().has(p, price)) {
                send(p, "&cНужно &e" + price + plugin.cfg().symbol + " &cдля создания.");
                return;
            }
            plugin.economy().withdraw(p, price);
        }
        plugin.storage().put(r);
        plugin.storage().saveAsync();
        plugin.selections().clear(p.getUniqueId());
        send(p, "<gradient:#5DFF6E:#00E5FF>Регион " + name + " создан!</gradient> <gray>"
                + r.sizeX() + "x" + r.sizeY() + "x" + r.sizeZ()
                + (vert ? " (на всю высоту)" : "") + " — бесплатно");
        p.sendMessage(Msg.color(plugin.cfg().prefix + "&7Настройка игроков и флагов: ")
                .append(Component.text("[открыть " + name + "]")
                        .color(NamedTextColor.GREEN)
                        .clickEvent(ClickEvent.runCommand("/area info " + name))
                        .hoverEvent(HoverEvent.showText(Component.text("Клик — открыть окно региона")))));
        if (plugin.cfg().sounds) p.playSound(p.getLocation(),
                org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1f);
    }

    private void handleDelete(Player p, String[] args) {
        if (args.length < 2) {
            send(p, "&eИспользование: &f/area delete <имя>");
            return;
        }
        Region r = plugin.storage().get(args[1]);
        if (r == null) {
            send(p, "&cРегиона &f" + args[1] + " &cнет.");
            return;
        }
        boolean owner = r.isOwner(p.getUniqueId());
        if (!owner && !Perms.has(p, "admin")) {
            send(p, "&cТолько владелец или админ.");
            return;
        }
        long now = System.currentTimeMillis();
        Long until = deleteConfirm.get(p.getUniqueId());
        if (until == null || until < now) {
            deleteConfirm.put(p.getUniqueId(), now + 15000L);
            send(p, "&cПовтори &f/area delete " + r.name + " &cдля подтверждения (15 сек).");
            return;
        }
        deleteConfirm.remove(p.getUniqueId());
        plugin.storage().remove(r.name);
        plugin.storage().saveAsync();
        send(p, "&aРегион &f" + r.name + " &aудалён.");
    }

    private void handleList(Player p) {
        List<Region> mine = new ArrayList<>();
        for (Region r : plugin.storage().all()) {
            if (r.isOwner(p.getUniqueId()) || r.isMember(p.getUniqueId())) mine.add(r);
        }
        if (mine.isEmpty()) {
            send(p, "&7У тебя нет регионов. Выдели точки и: &e/area create <имя>");
            return;
        }
        send(p, "<gradient:#5DFF6E:#00E5FF>Твои регионы</gradient> <gray>(" + mine.size() + ", клик — открыть):");
        for (Region r : mine) {
            p.sendMessage(Msg.color(plugin.cfg().prefix + "&f" + r.name + " &7" + r.world + " "
                            + r.sizeX() + "x" + r.sizeY() + "x" + r.sizeZ() + " ")
                    .append(Component.text("[открыть]")
                            .color(NamedTextColor.GREEN)
                            .clickEvent(ClickEvent.runCommand("/area info " + r.name))
                            .hoverEvent(HoverEvent.showText(Component.text("Клик — окно региона")))));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            List<String> base = new ArrayList<>(List.of("pos1", "pos2", "wand", "create", "delete", "list", "info"));
            if (sender instanceof Player p && Perms.has(p, "admin")) base.add("reload");
            return filter(args[0], base);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("delete") || args[0].equalsIgnoreCase("info"))) {
            List<String> names = new ArrayList<>();
            if (sender instanceof Player p) {
                for (Region r : plugin.storage().all()) {
                    if (r.isOwner(p.getUniqueId()) || r.isMember(p.getUniqueId())) names.add(r.name);
                }
            }
            return filter(args[1], names);
        }
        return Collections.emptyList();
    }

    private List<String> filter(String prefix, List<String> opts) {
        String q = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : opts) {
            if (o.toLowerCase(Locale.ROOT).startsWith(q)) out.add(o);
        }
        return out;
    }
}
