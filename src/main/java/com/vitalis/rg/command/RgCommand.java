package com.vitalis.rg.command;

import com.vitalis.rg.Regions;
import com.vitalis.rg.model.Region;
import com.vitalis.rg.select.SelectionManager;
import com.vitalis.rg.util.Msg;
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
 * /rg help inline: pos1, pos2, wand, create, delete, list, info.
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
        if (!p.hasPermission("rg.use")) {
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
                    send(p, "&eИспользование: &f/rg info <название>");
                    return true;
                }
                Region r = plugin.storage().get(args[1]);
                if (r == null) {
                    send(p, "&cРегиона &f" + args[1] + " &cнет.");
                    return true;
                }
                plugin.gui().openInfo(p, r);
            }
            default -> help(p);
        }
        return true;
    }

    private void help(Player p) {
        send(p, "&aРегионы — команды:");
        send(p, "&e/rg pos1 &7— точка 1 (где стоишь)");
        send(p, "&e/rg pos2 &7— точка 2 (где стоишь)");
        send(p, "&e/rg wand &7— дать жезл (ЛКМ/ПКМ по блоку)");
        send(p, "&e/rg create <имя> &7— создать регион");
        send(p, "&e/rg delete <имя> &7— удалить (2 раза подряд)");
        send(p, "&e/rg list &7— твои регионы");
        send(p, "&e/rg info <имя> &7— окно: владельцы/игроки +/-, флаги");
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
                    p.getUniqueId(), p.getName(), false, false);
            send(p, "&7Выделено: &f" + tmp.sizeX() + "x" + tmp.sizeY() + "x" + tmp.sizeZ()
                    + " &7→ &e/rg create <имя>");
        }
    }

    private void handleCreate(Player p, String[] args) {
        if (!p.hasPermission("rg.create")) {
            send(p, "&cНет доступа.");
            return;
        }
        if (args.length < 2) {
            send(p, "&eИспользование: &f/rg create <имя>");
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
            send(p, "&cСначала выдели точки: &e/rg pos1 &cи &e/rg pos2");
            return;
        }
        String world = s.pos1.getWorld().getName();
        if (plugin.cfg().blockedWorlds.contains(world.toLowerCase(Locale.ROOT))) {
            send(p, "&cВ этом мире создавать нельзя.");
            return;
        }
        Region r = new Region(name, world,
                s.pos1.getBlockX(), s.pos1.getBlockY(), s.pos1.getBlockZ(),
                s.pos2.getBlockX(), s.pos2.getBlockY(), s.pos2.getBlockZ(),
                p.getUniqueId(), p.getName(),
                plugin.cfg().defaultPvp, plugin.cfg().defaultExplosions);

        boolean unlimited = p.hasPermission("rg.unlimited");
        if (!unlimited) {
            int mine = plugin.storage().countBy(p.getUniqueId());
            if (mine >= plugin.cfg().maxPerPlayer) {
                send(p, "&cЛимит регионов: &e" + mine + "/" + plugin.cfg().maxPerPlayer);
                return;
            }
            int maxSide = plugin.cfg().maxSide;
            if (r.sizeX() > maxSide || r.sizeY() > maxSide || r.sizeZ() > maxSide) {
                send(p, "&cСлишком большой: макс. сторона &e" + maxSide
                        + " &7(у тебя " + r.sizeX() + "x" + r.sizeY() + "x" + r.sizeZ() + ")");
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
        send(p, "&aРегион &f" + name + " &aсоздан: &f"
                + r.sizeX() + "x" + r.sizeY() + "x" + r.sizeZ());
        send(p, "&7Настройка игроков и флагов: &e/rg info " + name);
        if (plugin.cfg().sounds) p.playSound(p.getLocation(),
                org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1f);
    }

    private void handleDelete(Player p, String[] args) {
        if (args.length < 2) {
            send(p, "&eИспользование: &f/rg delete <имя>");
            return;
        }
        Region r = plugin.storage().get(args[1]);
        if (r == null) {
            send(p, "&cРегиона &f" + args[1] + " &cнет.");
            return;
        }
        boolean owner = r.isOwner(p.getUniqueId());
        if (!owner && !p.hasPermission("rg.admin")) {
            send(p, "&cТолько владелец или админ.");
            return;
        }
        long now = System.currentTimeMillis();
        Long until = deleteConfirm.get(p.getUniqueId());
        if (until == null || until < now) {
            deleteConfirm.put(p.getUniqueId(), now + 15000L);
            send(p, "&cПовтори &f/rg delete " + r.name + " &cдля подтверждения (15 сек).");
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
            send(p, "&7У тебя нет регионов. Выдели точки и: &e/rg create <имя>");
            return;
        }
        send(p, "&aТвои регионы (&e" + mine.size() + "&a):");
        for (Region r : mine) {
            p.sendMessage(Msg.color("&7- &f" + r.name + " &7" + r.world + " "
                    + r.sizeX() + "x" + r.sizeY() + "x" + r.sizeZ()
                    + " &8| &e/rg info " + r.name));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            return filter(args[0], List.of("pos1", "pos2", "wand", "create", "delete", "list", "info"));
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
