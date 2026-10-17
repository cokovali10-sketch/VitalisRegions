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
 * Public: /area (menu), /area pos1, /area pos2, /area remove, /area help.
 * No region names: regions are auto-numbered inside, everything works
 * by location (where you stand) and chat buttons.
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

    /** Storage lookup for Brigadier suggest. */
    public Region region(String name) {
        return plugin.storage().get(name);
    }

    /** All regions for Brigadier suggest. */
    public java.util.Collection<Region> allRegions() {
        return plugin.storage().all();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Only players.");
            return true;
        }
        return handle(p, args);
    }

    /** Shared logic for Bukkit executor and Paper Brigadier wrapper. */
    public boolean handle(Player p, String[] args) {
        if (!Perms.has(p, "use")) {
            send(p, "&cНет доступа.");
            return true;
        }
        if (args.length == 0) {
            // location-aware: standing in own region -> its menu,
            // stranger's region -> no info, wilderness -> main menu
            Region here = regionAt(p);
            if (here != null) {
                if (here.isOwner(p.getUniqueId()) || here.isMember(p.getUniqueId())
                        || Perms.canManage(p, here)) {
                    plugin.menus().regionMenu(p, here);
                } else {
                    send(p, "&cЭто чужой регион, информации нет.");
                    send(p, "&7Не строится, хотя тебя добавили? Значит блокирует WorldGuard — встань сюда и введи &f/rg info&7, покажи админу.");
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
            case "mk" -> doCreate(p, false);
            case "mkvert" -> doCreate(p, true);
            case "vert" -> {
                if (args.length < 2) return true;
                expandVert(p, args[1]);
            }
            case "tgl" -> {
                if (args.length < 3) return true;
                toggleFlag(p, args[1], args[2]);
            }
            case "del" -> {
                if (args.length < 2) return true;
                deleteMenu(p, args[1]);
            }
            case "delyes" -> {
                if (args.length < 2) return true;
                Region r = plugin.storage().get(args[1]);
                if (r == null || !Perms.canManage(p, r)) {
                    send(p, "&cТолько владелец.");
                    return true;
                }
                plugin.storage().remove(r.name);
                plugin.storage().saveAsync();
                send(p, "&aРегион удалён.");
                ChatMenu.sDelete(plugin, p);
                plugin.menus().mainMenu(p);
            }
            case "owndel" -> {
                if (args.length < 2) return true;
                Region r = owned(p, args[1]);
                if (r == null) return true;
                if (args.length >= 3) {
                    applyDelNick(p, r.name, args[2], true);
                    return true;
                }
                askDelNick(p, r, true);
            }
            case "memdel" -> {
                if (args.length < 2) return true;
                Region r = owned(p, args[1]);
                if (r == null) return true;
                if (args.length >= 3) {
                    applyDelNick(p, r.name, args[2], false);
                    return true;
                }
                askDelNick(p, r, false);
            }
            case "ownadd", "memadd" -> {
                if (args.length < 2) return true;
                Region r = owned(p, args[1]);
                if (r == null) return true;
                boolean isOwner = sub.equals("ownadd");
                if (args.length >= 3) {
                    applyAdd(p, r.name, args[2], isOwner);
                    return true;
                }
                askAddNick(p, r, isOwner);
            }
            case "kick" -> {
                if (args.length < 2) {
                    send(p, "&eИспользование: &f/area kick <ник>");
                    return true;
                }
                kickPlayer(p, args[1]);
            }
            case "border" -> {
                if (args.length >= 2) {
                    toggleBorder(p, plugin.storage().get(args[1]));
                } else {
                    toggleBorder(p, regionAt(p));
                }
            }

            case "gwand" -> {
                if (!Perms.has(p, "use")) {
                    send(p, "&cНет доступа.");
                    return true;
                }
                giveWand(p);
            }
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

    private Region regionAt(Player p) {
        org.bukkit.Location loc = p.getLocation();
        return plugin.storage().at(loc.getWorld().getName(),
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    /** Toggle particle outline for one region (own/member/admin only). */
    private void toggleBorder(Player p, Region r) {
        if (r == null) {
            send(p, "&cТы не стоишь в регионе. &7Встань внутри или открой меню региона.");
            return;
        }
        if (!r.isOwner(p.getUniqueId()) && !r.isMember(p.getUniqueId())
                && !Perms.canManage(p, r)) {
            send(p, "&cЧужой регион.");
            return;
        }
        boolean now = plugin.borders().toggle(p, r);
        if (now) {
            ChatMenu.sToggle(plugin, p);
            send(p, "&aГраницы региона &f" + r.name + " &aвключены. &7Кликни ещё раз, чтобы скрыть.");
        } else {
            ChatMenu.sToggle(plugin, p);
            send(p, "&7Границы региона &f" + r.name + " &7скрыты.");
        }
    }

    private void scheduleInputTimeout(Player p) {
        ChatMenu.Pending cur = plugin.menus().peekInput(p);
        if (cur == null) return;
        long token = cur.token();
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            plugin.menus().timeoutInput(p, token);
        }, 30L * 20L);
    }

    /** Region by internal id if viewer may manage it (owner, op or area.admin). */
    private Region owned(Player p, String id) {
        Region r = plugin.storage().get(id);
        if (r == null) {
            send(p, "&cРегиона нет.");
            return null;
        }
        if (!Perms.canManage(p, r)) {
            send(p, "&cТолько владелец.");
            return null;
        }
        return r;
    }

    // ---------------- public: remove ----------------
    // No args = region under feet. Name args kept only for compat.

    private void handleRemove(Player p, String[] args) {
        Region r;
        if (args.length >= 2) {
            r = plugin.storage().get(args[1]);
            if (r == null) {
                send(p, "&cРегиона нет.");
                return;
            }
        } else {
            r = regionAt(p);
            if (r == null) {
                send(p, "&cВстань внутрь региона, который хочешь удалить.");
                return;
            }
        }
        if (!Perms.canManage(p, r)) {
            send(p, "&cТолько владелец или админ.");
            return;
        }
        long now = System.currentTimeMillis();
        Long until = removeConfirm.get(p.getUniqueId());
        if (until == null || until < now) {
            removeConfirm.put(p.getUniqueId(), now + 15000L);
            send(p, "&cПовтори &f/area remove &cдля подтверждения (15 сек).");
            return;
        }
        removeConfirm.remove(p.getUniqueId());
        plugin.storage().remove(r.name);
        plugin.storage().saveAsync();
        send(p, "&aРегион " + r.name + " удалён.");
        ChatMenu.sDelete(plugin, p);
    }

    // ---------------- create (auto-numbered, no names) ----------------

    /** Random individual 5-digit number per region (10000-99999). */
    private String nextId(Player p) {
        java.util.Random rnd = new java.util.Random();
        for (int i = 0; i < 1000; i++) {
            String id = String.valueOf(10000 + rnd.nextInt(90000));
            if (plugin.storage().get(id) == null) return id;
        }
        return String.valueOf(10000 + rnd.nextInt(90000)) + (System.currentTimeMillis() % 10);
    }

    private void doCreate(Player p, boolean vert) {
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
        String id = nextId(p);
        Region r = new Region(id, world,
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
                send(p, "&cТут уже занято — выбери другое место (края тоже заняты).");
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
        send(p, "<gradient:#5DFF6E:#00E5FF>Регион " + id + " создан!</gradient> <gray>"
                + r.sizeX() + "x" + r.sizeY() + "x" + r.sizeZ()
                + (vert ? " (на всю высоту)" : "") + " — бесплатно");
        if (plugin.cfg().sounds) {
            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1f);
        }
        plugin.menus().regionMenu(p, r);
    }

    private void expandVert(Player p, String id) {
        Region r = owned(p, id);
        if (r == null) return;
        org.bukkit.World w = org.bukkit.Bukkit.getWorld(r.world);
        if (w == null) {
            send(p, "&cМир не загружен.");
            return;
        }
        int oldMin = r.minY, oldMax = r.maxY;
        r.minY = w.getMinHeight();
        r.maxY = w.getMaxHeight() - 1;
        // expanded region must not swallow neighbours — revert on overlap
        for (Region o : plugin.storage().all()) {
            if (o == r || o.name.equalsIgnoreCase(r.name)) continue;
            if (o.intersects(r)) {
                r.minY = oldMin;
                r.maxY = oldMax;
                send(p, "&cТут уже занято — на всю высоту не растянуть.");
                return;
            }
        }
        plugin.storage().saveAsync();
        send(p, "&aРегион растянут на всю высоту.");
        plugin.menus().regionMenu(p, r);
    }

    // ---------------- hidden: flags / members (nick in chat) ----------------

    private void askAddNick(Player p, Region r, boolean owner) {
        String what = owner ? "владельца" : "игрока";
        p.sendMessage(Msg.color(plugin.cfg().prefix + "&eВведи ник " + what + " в чат &7(пустое = отмена, 30 сек):"));
        plugin.menus().markInput(p, r.name,
                owner ? ChatMenu.InputKind.ADD_OWNER : ChatMenu.InputKind.ADD_MEMBER);
        scheduleInputTimeout(p);
    }

    private void askDelNick(Player p, Region r, boolean owner) {
        p.sendMessage(Msg.color(plugin.cfg().prefix + "&eВведи ник для удаления в чат &7(пустое = отмена, 30 сек):"));
        plugin.menus().markInput(p, r.name,
                owner ? ChatMenu.InputKind.DEL_OWNER : ChatMenu.InputKind.DEL_MEMBER);
        scheduleInputTimeout(p);
    }

    /** Shared add logic: chat command + chat input. */
    public void applyAdd(Player p, String region, String nick, boolean owner) {
        Region r = owned(p, region);
        if (r == null) return;
        org.bukkit.OfflinePlayer op = org.bukkit.Bukkit.getOfflinePlayer(nick);
        if (!op.hasPlayedBefore() && !op.isOnline()) {
            send(p, "&cИгрок &f" + nick + " &cне найден (ник с ошибкой?).");
            return;
        }
        UUID uuid = op.getUniqueId();
        String realName = op.getName() != null ? op.getName() : nick;
        if (owner) {
            if (r.owners.containsKey(uuid)) {
                send(p, "&e" + realName + " &7уже владелец.");
            } else {
                r.owners.put(uuid, realName);
                r.members.remove(uuid);
                plugin.storage().saveAsync();
                send(p, "&a" + realName + " &7теперь владелец.");
            }
        } else {
            if (r.isOwner(uuid)) {
                send(p, "&e" + realName + " &7уже владелец.");
            } else if (r.members.containsKey(uuid)) {
                send(p, "&e" + realName + " &7уже в списке.");
            } else {
                r.members.put(uuid, realName);
                plugin.storage().saveAsync();
                send(p, "&a" + realName + " &7добавлен.");
            }
        }
        ChatMenu.sPlus(plugin, p);
        plugin.menus().regionMenu(p, r);
    }

    /** Shared delete-by-nick logic (chat command + chat input). */
    @SuppressWarnings("deprecation")
    public void applyDelNick(Player p, String region, String nick, boolean owner) {
        Region r = owned(p, region);
        if (r == null) return;
        org.bukkit.OfflinePlayer op = org.bukkit.Bukkit.getOfflinePlayer(nick);
        UUID uuid = op.getUniqueId();
        if (owner) {
            if (uuid.equals(r.owner)) {
                send(p, "&cСоздателя убрать нельзя.");
                return;
            }
            if (r.owners.remove(uuid) != null) {
                plugin.storage().saveAsync();
                send(p, "&e" + nick + " &7больше не владелец.");
            } else {
                send(p, "&7Такого владельца нет.");
            }
        } else {
            if (r.members.remove(uuid) != null) {
                plugin.storage().saveAsync();
                send(p, "&e" + nick + " &7убран из региона.");
            } else {
                send(p, "&7Такого игрока нет.");
            }
        }
        ChatMenu.sMinus(plugin, p);
        plugin.menus().regionMenu(p, r);
    }

    private void toggleFlag(Player p, String id, String flag) {
        Region r = owned(p, id);
        if (r == null) return;
        String label;
        switch (flag.toLowerCase(Locale.ROOT)) {
            case "fire" -> { r.fireSpread = !r.fireSpread; label = "Огонь"; }
            case "expl" -> { r.explosions = !r.explosions; label = "Взрывы"; }
            case "mech" -> { r.mechanisms = !r.mechanisms; label = "Механизмы"; }
            case "homes" -> { r.homes = !r.homes; label = "Хомы"; }
            default -> { return; }
        }
        plugin.storage().saveAsync();
        ChatMenu.sToggle(plugin, p);
        plugin.menus().regionMenu(p, r);
        boolean on = flag.equalsIgnoreCase("fire") ? r.fireSpread
                : flag.equalsIgnoreCase("expl") ? r.explosions
                : flag.equalsIgnoreCase("mech") ? r.mechanisms : r.homes;
        send(p, "&7" + label + ": " + (on ? "&aВКЛ" : "&cВЫКЛ"));
    }

    /** Minus button: ask nick in chat, removal happens on reply. */
    private void deleteMenu(Player p, String id) {
        Region r = owned(p, id);
        if (r == null) return;
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

    /** Kick from own region to spawn with 3s warmup. Owner only, target must stand inside. */
    private void kickPlayer(Player kicker, String nick) {
        Player target = org.bukkit.Bukkit.getPlayerExact(nick);
        if (target == null) {
            send(kicker, "&cИгрок &f" + nick + " &cне в сети.");
            return;
        }
        if (target.getUniqueId().equals(kicker.getUniqueId())) {
            send(kicker, "&cСебя кикнуть нельзя.");
            return;
        }
        org.bukkit.Location tloc = target.getLocation();
        Region in = null;
        for (Region r : plugin.storage().all()) {
            if (!r.contains(tloc.getWorld().getName(), tloc.getBlockX(), tloc.getBlockY(), tloc.getBlockZ())) continue;
            if (r.isOwner(kicker.getUniqueId())) {
                in = r;
                break;
            }
        }
        if (in == null) {
            send(kicker, "&cИгрок не в границах твоего региона. Кикать можно только у себя.");
            return;
        }
        if (plugin.kickWarmups().containsKey(target.getUniqueId())) {
            send(kicker, "&7Его уже кикают.");
            return;
        }
        send(kicker, "&e" + target.getName() + " &7будет на спавне через 3 сек. Сдвинется — отмена.");
        target.sendMessage(Msg.color(plugin.cfg().prefix + "&cВас перенесет на спавн через 3 секунды."));
        if (plugin.cfg().sounds) {
            target.playSound(target.getLocation(), org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 0.8f);
        }
        org.bukkit.scheduler.BukkitTask task = org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            plugin.kickWarmups().remove(target.getUniqueId());
            if (!target.isOnline()) return;
            org.bukkit.World w = org.bukkit.Bukkit.getWorlds().isEmpty()
                    ? target.getWorld() : org.bukkit.Bukkit.getWorlds().get(0);
            target.teleport(w.getSpawnLocation());
            target.sendMessage(Msg.color(plugin.cfg().prefix + "&aТы на спавне."));
        }, 60L);
        plugin.kickWarmups().put(target.getUniqueId(), task);
    }

    /** Super simple help: 3 commands + buttons do the rest. */
    private void helpMenu(Player p) {
        send(p, "<gradient:#5DFF6E:#00E5FF>Как приватить:</gradient>");
        send(p, "&71. Встань в один угол, введи &e/area pos1");
        send(p, "&72. Встань в другой угол, введи &e/area pos2");
        send(p, "&73. Введи &e/area &7и выбери &6[На всю высоту] &7или &a[Как выделил]");
        send(p, "&7Владельцев и игроков добавляют кнопки &a[+] &7и &c[-] &7— ник пишется в чат.");
        send(p, "&7Удалить регион: встань в него и введи &e/area remove");
        send(p, "&7Кикнуть чужого со своей территории: &e/area kick <ник> &7(только владелец, через 3 сек на спавн)");
    }

    private String xyz(Location l) {
        return l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ()
                + " (" + l.getWorld().getName() + ")";
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            return filter(args[0], List.of("pos1", "pos2", "remove", "help", "kick"));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("kick") && sender instanceof Player) {
            List<String> names = new ArrayList<>();
            for (Player o : org.bukkit.Bukkit.getOnlinePlayers()) names.add(o.getName());
            return filter(args[1], names);
        }
        // nick completion for add/del while typing
        if ((args[0].equalsIgnoreCase("ownadd") || args[0].equalsIgnoreCase("memadd"))
                && args.length == 3 && sender instanceof Player) {
            List<String> names = new ArrayList<>();
            for (Player o : org.bukkit.Bukkit.getOnlinePlayers()) names.add(o.getName());
            return filter(args[2], names);
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
