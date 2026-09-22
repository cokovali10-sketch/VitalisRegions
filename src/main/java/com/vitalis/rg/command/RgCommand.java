package com.vitalis.rg.command;

import com.vitalis.rg.Regions;
import com.vitalis.rg.menu.ChatMenu;
import com.vitalis.rg.model.Region;
import com.vitalis.rg.select.SelectionManager;
import com.vitalis.rg.util.Msg;
import com.vitalis.rg.util.Perms;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Public: /area (menu), /area pos1, /area pos2, /area remove.
 * The rest is chat buttons running hidden routes (m, sel, mk, tgl, tp, del...).
 */
public class RgCommand implements CommandExecutor, TabCompleter {
    private final Regions plugin;
    /** player -> region awaiting remove confirm + deadline */
    private final Map<UUID, Long> removeConfirm = new HashMap<>();

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
            // location-aware: standing in a region -> its menu (if owner/member),
            // stranger's region -> no info, wilderness -> main menu
            org.bukkit.Location loc = p.getLocation();
            Region here = plugin.storage().at(loc.getWorld().getName(),
                    loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
            if (here != null) {
                if (here.isOwner(p.getUniqueId()) || here.isMember(p.getUniqueId())) {
                    plugin.menus().regionMenu(p, here);
                } else {
                    send(p, "&cЭто чужой регион &f" + here.name + "&c, информации нет.");
                    send(p, "&7Свои регионы — кнопками ниже.");
                    plugin.menus().mainMenu(p);
                }
                return true;
            }
            plugin.menus().mainMenu(p);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "pos1" -> {
                SelectionManager.Selection s = plugin.selections().get(p.getUniqueId());
                s.pos1 = p.getLocation().clone();
                send(p, "&aТочка 1: &f" + xyz(s.pos1));
                if (s.complete()) plugin.menus().mainMenu(p);
            }
            case "pos2" -> {
                SelectionManager.Selection s = plugin.selections().get(p.getUniqueId());
                s.pos2 = p.getLocation().clone();
                send(p, "&aТочка 2: &f" + xyz(s.pos2));
                if (s.complete()) plugin.menus().mainMenu(p);
            }
            case "remove" -> handleRemove(p, args);
            case "help", "?" -> helpMenu(p);
            // ---- hidden button routes (not in tab-complete) ----
            case "m" -> plugin.menus().mainMenu(p);
            case "sel" -> {
                if (args.length < 2) return true;
                Region r = plugin.storage().get(args[1]);
                if (r == null) {
                    send(p, "&cРегиона нет.");
                    return true;
                }
                plugin.menus().regionMenu(p, r);
            }
            case "mk" -> askCreateName(p, false);
            case "mkvert" -> askCreateName(p, true);
            case "vert" -> {
                if (args.length < 2) return true;
                expandVert(p, args[1]);
            }
            case "tgl" -> {
                if (args.length < 3) return true;
                toggleFlag(p, args[1], args[2]);
            }
            case "tp" -> {
                if (args.length < 2) return true;
                teleport(plugin, p, args[1]);
            }
            case "del" -> {
                if (args.length < 2) return true;
                deleteMenu(p, args[1]);
            }
            case "delyes" -> {
                if (args.length < 2) return true;
                Region r = plugin.storage().get(args[1]);
                if (r == null || !r.isOwner(p.getUniqueId())) {
                    send(p, "&cТолько владелец.");
                    return true;
                }
                plugin.storage().remove(r.name);
                plugin.storage().saveAsync();
                send(p, "&aРегион &f" + r.name + " &aудалён.");
                plugin.menus().mainMenu(p);
            }
            case "ren" -> {
                if (args.length < 2) return true;
                Region r = plugin.storage().get(args[1]);
                if (r == null || !r.isOwner(p.getUniqueId())) {
                    send(p, "&cТолько владелец.");
                    return true;
                }
                p.sendMessage(Msg.color(plugin.cfg().prefix + "&eВведи новое название в чат &7(латиница 3-16, пустое = отмена, 30 сек):"));
                plugin.menus().markInput(p, r.name, ChatMenu.InputKind.RENAME);
                scheduleInputTimeout(p);
            }
            case "owndel" -> {
                if (args.length < 2) return true;
                askNick(p, args[1], true);
            }
            case "memdel" -> {
                if (args.length < 2) return true;
                askNick(p, args[1], false);
            }
            case "ownadd", "memadd" -> {
                if (args.length < 2) return true;
                Region r = plugin.storage().get(args[1]);
                if (r == null || !r.isOwner(p.getUniqueId())) {
                    send(p, "&cТолько владелец.");
                    return true;
                }
                boolean owner = sub.equals("ownadd");
                p.sendMessage(Msg.color(plugin.cfg().prefix + "&eВведи ник "
                        + (owner ? "владельца" : "игрока") + " в чат &7(пустое = отмена, 30 сек):"));
                plugin.menus().markInput(p, r.name,
                        owner ? ChatMenu.InputKind.ADD_OWNER : ChatMenu.InputKind.ADD_MEMBER);
                scheduleInputTimeout(p);
            }
            case "gwand" -> giveWand(p);
            case "reload" -> {
                if (!Perms.has(p, "admin")) {
                    send(p, "&cНет доступа.");
                    return true;
                }
                plugin.cfg().load(plugin);
                send(p, "&aКонфиг перезагружен (регионы не тронуты).");
            }
            default -> plugin.menus().mainMenu(p);
        }
        return true;
    }

    private void scheduleInputTimeout(Player p) {
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (plugin.menus().takeInput(p) != null) {
                p.sendMessage(Msg.color(plugin.cfg().prefix + "&7Ввод отменён (таймаут)."));
            }
        }, 30L * 20L);
    }

    /** Super simple help: 3 commands + buttons do the rest. */
    private void helpMenu(Player p) {
        send(p, "<gradient:#5DFF6E:#00E5FF>Как приватить:</gradient>");
        send(p, "&71. Встань в один угол, введи &e/area pos1");
        send(p, "&72. Встань в другой угол, введи &e/area pos2");
        send(p, "&73. Введи &e/area &7и нажми &a[+ Создать регион]");
        send(p, "&7Владельцев и игроков добавляют кнопки &a[+] &7и &c[−] &7— ник пишется в чат.");
        send(p, "&7Удалить регион: &e/area remove <название>");
    }

    private String xyz(Location l) {
        return l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ()
                + " (" + l.getWorld().getName() + ")";
    }

    /** Teleport to region center top (members/owners only). Shared with chat buttons. */
    public static void teleport(Regions plugin, Player p, String name) {
        Region r = plugin.storage().get(name);
        if (r == null) {
            p.sendMessage(Msg.color(plugin.cfg().prefix + "&cРегиона &f" + name + " &cнет."));
            return;
        }
        if (!r.canBuild(p.getUniqueId()) && !Perms.has(p, "bypass")) {
            p.sendMessage(Msg.color(plugin.cfg().prefix + "&cТолько владельцы и участники."));
            return;
        }
        org.bukkit.World w = org.bukkit.Bukkit.getWorld(r.world);
        if (w == null) {
            p.sendMessage(Msg.color(plugin.cfg().prefix + "&cМир &f" + r.world + " &cне загружен."));
            return;
        }
        int x = (r.minX + r.maxX) / 2;
        int z = (r.minZ + r.maxZ) / 2;
        int y = w.getHighestBlockYAt(x, z) + 1;
        if (y <= w.getMinHeight()) y = Math.min(r.maxY + 1, w.getMaxHeight() - 1);
        p.teleport(new Location(w, x + 0.5, y, z + 0.5));
        p.sendMessage(Msg.color(plugin.cfg().prefix + "&aТелепорт в &f" + r.name));
        if (plugin.cfg().sounds) {
            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1f);
        }
    }

    // ---------------- public: remove ----------------

    private void handleRemove(Player p, String[] args) {
        if (args.length < 2) {
            send(p, "&eИспользование: &f/area remove <название>");
            return;
        }
        Region r = plugin.storage().get(args[1]);
        if (r == null) {
            send(p, "&cРегиона &f" + args[1] + " &cнет.");
            return;
        }
        if (!r.isOwner(p.getUniqueId()) && !Perms.has(p, "admin")) {
            send(p, "&cТолько владелец или админ.");
            return;
        }
        long now = System.currentTimeMillis();
        Long until = removeConfirm.get(p.getUniqueId());
        if (until == null || until < now) {
            removeConfirm.put(p.getUniqueId(), now + 15000L);
            send(p, "&cПовтори &f/area remove " + r.name + " &cдля подтверждения (15 сек).");
            return;
        }
        removeConfirm.remove(p.getUniqueId());
        plugin.storage().remove(r.name);
        plugin.storage().saveAsync();
        send(p, "&aРегион &f" + r.name + " &aудалён.");
    }

    // ---------------- hidden: create ----------------

    private void askCreateName(Player p, boolean vert) {
        SelectionManager.Selection s = plugin.selections().get(p.getUniqueId());
        if (!s.complete()) {
            send(p, "&cСначала выдели точки кнопками ниже.");
            plugin.menus().mainMenu(p);
            return;
        }
        if (!Perms.has(p, "create")) {
            send(p, "&cНет доступа.");
            return;
        }
        p.sendMessage(Msg.color(plugin.cfg().prefix + "&eВведи название региона в чат "
                + (vert ? "&6(на всю высоту) " : "")
                + "&7(латиница 3-16, пустое = отмена, 30 сек):"));
        plugin.menus().markInput(p, "", ChatMenu.InputKind.CREATE_NAME, vert);
        scheduleInputTimeout(p);
    }

    /** Called from chat input with the typed name. */
    public void createFromChat(Player p, String name) {
        createFromChat(p, name, false);
    }

    /** Called from chat input with the typed name. */
    public void createFromChat(Player p, String name, boolean vert) {
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
            send(p, "&cВыделение слетело, выбери точки заново.");
            return;
        }
        String world = s.pos1.getWorld().getName();
        if (plugin.cfg().blockedWorlds.contains(world.toLowerCase(Locale.ROOT))) {
            send(p, "&cВ этом мире создавать нельзя.");
            return;
        }
        int y1 = s.pos1.getBlockY(), y2 = s.pos2.getBlockY();
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
        if (plugin.cfg().sounds) {
            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1f);
        }
        plugin.menus().regionMenu(p, r);
    }

    private void expandVert(Player p, String name) {
        Region r = plugin.storage().get(name);
        if (r == null || !r.isOwner(p.getUniqueId())) {
            send(p, "&cТолько владелец.");
            return;
        }
        org.bukkit.World w = org.bukkit.Bukkit.getWorld(r.world);
        if (w == null) {
            send(p, "&cМир не загружен.");
            return;
        }
        // rebuild with full height (Region fields are mutable for this)
        r.minY = w.getMinHeight();
        r.maxY = w.getMaxHeight() - 1;
        plugin.storage().saveAsync();
        send(p, "&aРегион &f" + r.name + " &aрастянут на всю высоту.");
        plugin.menus().regionMenu(p, r);
    }

    // ---------------- hidden: flags / members ----------------

    private void toggleFlag(Player p, String name, String flag) {
        Region r = plugin.storage().get(name);
        if (r == null || !r.isOwner(p.getUniqueId())) {
            send(p, "&cТолько владелец.");
            return;
        }
        String label;
        switch (flag.toLowerCase(Locale.ROOT)) {
            case "fire" -> { r.fireSpread = !r.fireSpread; label = "Огонь"; }
            case "pvp" -> { r.pvp = !r.pvp; label = "PVP"; }
            case "expl" -> { r.explosions = !r.explosions; label = "Взрывы"; }
            case "mech" -> { r.mechanisms = !r.mechanisms; label = "Механизмы"; }
            case "homes" -> { r.homes = !r.homes; label = "Хомы"; }
            default -> { return; }
        }
        plugin.storage().saveAsync();
        if (plugin.cfg().sounds) {
            p.playSound(p.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.7f, 1f);
        }
        plugin.menus().regionMenu(p, r);
        boolean on = flag.equalsIgnoreCase("fire") ? r.fireSpread
                : flag.equalsIgnoreCase("pvp") ? r.pvp
                : flag.equalsIgnoreCase("expl") ? r.explosions
                : flag.equalsIgnoreCase("mech") ? r.mechanisms : r.homes;
        send(p, "&7" + label + ": " + (on ? "&aВКЛ" : "&cВЫКЛ"));
    }

    /** Minus button: ask nick in chat, removal happens on reply. */
    private void askNick(Player p, String region, boolean owner) {
        Region r = plugin.storage().get(region);
        if (r == null || !r.isOwner(p.getUniqueId())) {
            send(p, "&cТолько владелец.");
            return;
        }
        p.sendMessage(Msg.color(plugin.cfg().prefix + "&eВведи ник для удаления в чат &7(пустое = отмена, 30 сек):"));
        plugin.menus().markInput(p, r.name,
                owner ? ChatMenu.InputKind.DEL_OWNER : ChatMenu.InputKind.DEL_MEMBER);
        scheduleInputTimeout(p);
    }

    private void deleteMenu(Player p, String name) {
        Region r = plugin.storage().get(name);
        if (r == null || !r.isOwner(p.getUniqueId())) {
            send(p, "&cТолько владелец.");
            return;
        }
        plugin.menus().deleteConfirmMenu(p, r);
    }

    private void giveWand(Player p) {
        for (org.bukkit.inventory.ItemStack it : p.getInventory().getContents()) {
            if (it == null || it.getType() != plugin.cfg().wand) continue;
            if (!it.hasItemMeta() || !it.getItemMeta().hasDisplayName()) continue;
            if (Msg.plain(it.getItemMeta().displayName()).equals("Жезл региона")) {
                send(p, "&eЖезл уже в инвентаре.");
                return;
            }
        }
        org.bukkit.inventory.ItemStack wand = new org.bukkit.inventory.ItemStack(plugin.cfg().wand);
        org.bukkit.inventory.meta.ItemMeta m = wand.getItemMeta();
        m.displayName(Msg.color("&aЖезл региона"));
        m.lore(List.of(Msg.color("&7ЛКМ — точка 1, ПКМ — точка 2")));
        wand.setItemMeta(m);
        Map<Integer, org.bukkit.inventory.ItemStack> left = p.getInventory().addItem(wand);
        for (org.bukkit.inventory.ItemStack l : left.values()) p.getWorld().dropItemNaturally(p.getLocation(), l);
        send(p, "&aЖезл выдан: &fЛКМ — точка 1, ПКМ — точка 2");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            return filter(args[0], List.of("pos1", "pos2", "remove", "help"));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("remove") && sender instanceof Player p) {
            List<String> names = new ArrayList<>();
            for (Region r : plugin.storage().all()) {
                if (r.isOwner(p.getUniqueId())) names.add(r.name);
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
