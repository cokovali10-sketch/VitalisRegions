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
        return handle(p, args);
    }

    /** Shared logic for Bukkit executor and Paper Brigadier wrapper. */
    public boolean handle(Player p, String[] args) {
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
                ChatMenu.sDelete(plugin, p);
                plugin.menus().mainMenu(p);
            }
            case "ren" -> {
                if (args.length < 2) return true;
                Region r = owned(p, args[1]);
                if (r == null) return true;
                try {
                    com.vitalis.rg.menu.DialogMenu.textInput(plugin, p,
                            Msg.color(plugin.menus().grad("Переименовать " + r.name)),
                            "Новое название (латиница 3-16):",
                            text -> applyRename(p, r.name, text));
                } catch (Throwable t) {
                    p.sendMessage(Msg.color(plugin.cfg().prefix + "&eВведи новое название в чат &7(латиница 3-16, пустое = отмена, 30 сек):"));
                    plugin.menus().markInput(p, r.name, ChatMenu.InputKind.RENAME);
                    scheduleInputTimeout(p);
                }
            }
            case "owndel" -> {
                if (args.length < 2) return true;
                Region r = owned(p, args[1]);
                if (r == null) return true;
                openDelDialog(p, r, true);
            }
            case "memdel" -> {
                if (args.length < 2) return true;
                Region r = owned(p, args[1]);
                if (r == null) return true;
                openDelDialog(p, r, false);
            }
            case "ownadd", "memadd" -> {
                if (args.length < 2) return true;
                Region r = owned(p, args[1]);
                if (r == null) return true;
                openAddDialog(p, r, sub.equals("ownadd"));
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

    private void scheduleInputTimeout(Player p) {
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (plugin.menus().takeInput(p) != null) {
                p.sendMessage(Msg.color(plugin.cfg().prefix + "&7Ввод отменён (таймаут)."));
            }
        }, 30L * 20L);
    }

    /** Region by name if viewer is its owner, else a message + null. */
    private Region owned(Player p, String name) {
        Region r = plugin.storage().get(name);
        if (r == null) {
            send(p, "&cРегиона нет.");
            return null;
        }
        if (!r.isOwner(p.getUniqueId())) {
            send(p, "&cТолько владелец.");
            return null;
        }
        return r;
    }

    // ---------------- dialog windows (+ chat fallback) ----------------

    private void openAddDialog(Player p, Region r, boolean owner) {
        // straight to the online list (empty filter = everyone); refine narrows it down
        openMatchDialog(p, r, owner, "");
    }

    /** Online nicks as one-click buttons: prefix matches first, then any occurrence. */
    private void openMatchDialog(Player p, Region r, boolean owner, String prefix) {
        String q = prefix.toLowerCase(Locale.ROOT);
        List<com.vitalis.rg.menu.DialogMenu.DialogMenuEntry> starts = new ArrayList<>();
        List<com.vitalis.rg.menu.DialogMenu.DialogMenuEntry> contains = new ArrayList<>();
        for (Player o : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (o.getUniqueId().equals(p.getUniqueId())) continue;
            if (r.isOwner(o.getUniqueId()) || r.isMember(o.getUniqueId())) continue;
            String low = o.getName().toLowerCase(Locale.ROOT);
            com.vitalis.rg.menu.DialogMenu.DialogMenuEntry e =
                    new com.vitalis.rg.menu.DialogMenu.DialogMenuEntry(
                            o.getUniqueId().toString(), o.getName());
            if (low.startsWith(q)) {
                if (starts.size() < 24) starts.add(e);
            } else if (!q.isEmpty() && low.contains(q)) {
                if (starts.size() + contains.size() < 24) contains.add(e);
            }
        }
        List<com.vitalis.rg.menu.DialogMenu.DialogMenuEntry> matched = new ArrayList<>(starts);
        matched.addAll(contains);
        if (matched.isEmpty()) {
            // nobody online matches: full manual entry (covers offline players)
            openTypeDialog(p, r, owner);
            return;
        }
        try {
            List<io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput.OptionEntry> opts = new ArrayList<>();
            opts.add(io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput.OptionEntry
                    .create("__refine__", net.kyori.adventure.text.Component.text("Уточнить буквы («" + prefix + "»)"), true));
            opts.add(io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput.OptionEntry
                    .create("__type__", net.kyori.adventure.text.Component.text("Ввести ник полностью"), false));
            for (com.vitalis.rg.menu.DialogMenu.DialogMenuEntry e : matched) {
                opts.add(io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput.OptionEntry
                        .create(e.id(), net.kyori.adventure.text.Component.text(e.nick()), false));
            }
            String listTitle = prefix.isEmpty()
                    ? "Кто онлайн · " + r.name
                    : "«" + prefix + "» · найдено " + matched.size() + " · " + r.name;
            com.vitalis.rg.menu.DialogMenu.select(plugin, p,
                    Msg.color(plugin.menus().grad(listTitle)), opts,
                    id -> {
                        if (id.isEmpty()) {
                            send(p, "&7Отменено.");
                            return;
                        }
                        if (id.equals("__type__")) {
                            openTypeDialog(p, r, owner);
                            return;
                        }
                        if (id.equals("__refine__")) {
                            openRefineDialog(p, r, owner, prefix);
                            return;
                        }
                        applyAddUuid(p, r.name, id, owner);
                    });
        } catch (Throwable t) {
            openTypeDialog(p, r, owner);
        }
    }

    /** Refine filter: text window pre-filled with current letters (add more or erase). */
    private void openRefineDialog(Player p, Region r, boolean owner, String prefix) {
        try {
            com.vitalis.rg.menu.DialogMenu.textInput(plugin, p,
                    Msg.color(plugin.menus().grad((owner ? "Добавить владельца" : "Добавить игрока") + " · " + r.name)),
                    "Буквы ника (можно стереть):", prefix,
                    text -> {
                        if (text.isEmpty()) {
                            send(p, "&7Отменено.");
                            return;
                        }
                        openMatchDialog(p, r, owner, text);
                    });
        } catch (Throwable t) {
            openMatchDialog(p, r, owner, prefix);
        }
    }

    /** Manual nick entry window (also covers offline players). */
    private void openTypeDialog(Player p, Region r, boolean owner) {
        String what = owner ? "владельца" : "игрока";
        try {
            com.vitalis.rg.menu.DialogMenu.textInput(plugin, p,
                    Msg.color(plugin.menus().grad((owner ? "Добавить владельца" : "Добавить игрока") + " · " + r.name)),
                    "Ник " + what + ":",
                    text -> {
                        if (text.isEmpty()) {
                            send(p, "&7Отменено.");
                            return;
                        }
                        applyAdd(p, r.name, text, owner);
                    });
        } catch (Throwable t) {
            p.sendMessage(Msg.color(plugin.cfg().prefix + "&eВведи ник " + what + " в чат &7(пустое = отмена, 30 сек):"));
            plugin.menus().markInput(p, r.name,
                    owner ? ChatMenu.InputKind.ADD_OWNER : ChatMenu.InputKind.ADD_MEMBER);
            scheduleInputTimeout(p);
        }
    }

    /** Shared add-by-UUID logic (online picker window). */
    public void applyAddUuid(Player p, String region, String uuidStr, boolean owner) {
        Region r = owned(p, region);
        if (r == null) return;
        UUID uuid;
        try {
            uuid = UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
            return;
        }
        org.bukkit.OfflinePlayer op = org.bukkit.Bukkit.getOfflinePlayer(uuid);
        String realName = op.getName() != null ? op.getName() : uuidStr;
        if (owner) {
            if (r.owners.containsKey(uuid)) {
                send(p, "&e" + realName + " &7уже владелец.");
            } else {
                r.owners.put(uuid, realName);
                r.members.remove(uuid);
                plugin.storage().saveAsync();
                send(p, "&a" + realName + " &7теперь владелец &f" + r.name);
            }
        } else {
            if (r.isOwner(uuid)) {
                send(p, "&e" + realName + " &7уже владелец.");
            } else if (r.members.containsKey(uuid)) {
                send(p, "&e" + realName + " &7уже в списке.");
            } else {
                r.members.put(uuid, realName);
                plugin.storage().saveAsync();
                send(p, "&a" + realName + " &7добавлен в &f" + r.name);
            }
        }
        ChatMenu.sPlus(plugin, p);
        plugin.menus().regionMenu(p, r);
    }

    private void openDelDialog(Player p, Region r, boolean owner) {
        Map<UUID, String> map = owner ? r.owners : r.members;
        List<com.vitalis.rg.menu.DialogMenu.DialogMenuEntry> entries = new ArrayList<>();
        for (Map.Entry<UUID, String> e : map.entrySet()) {
            if (owner && e.getKey().equals(r.owner)) continue; // creator stays
            entries.add(new com.vitalis.rg.menu.DialogMenu.DialogMenuEntry(e.getKey().toString(), e.getValue()));
            if (entries.size() >= 25) break; // dialog window stays readable
        }
        if (entries.isEmpty()) {
            send(p, owner ? "&7Других владельцев нет." : "&7Список игроков пуст.");
            return;
        }
        if ((owner ? r.owners.size() : r.members.size()) > 25) {
            send(p, "&7Показаны первые 25.");
        }
        try {
            List<io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput.OptionEntry> opts = new ArrayList<>();
            for (com.vitalis.rg.menu.DialogMenu.DialogMenuEntry e : entries) {
                opts.add(io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput.OptionEntry
                        .create(e.id(), net.kyori.adventure.text.Component.text(e.nick()), false));
            }
            com.vitalis.rg.menu.DialogMenu.select(plugin, p,
                    Msg.color(plugin.menus().grad("Кого убрать · " + r.name)), opts,
                    id -> {
                        if (id.isEmpty()) {
                            send(p, "&7Отменено.");
                            return;
                        }
                        applyDelUuid(p, r.name, id, owner);
                    });
        } catch (Throwable t) {
            p.sendMessage(Msg.color(plugin.cfg().prefix + "&eВведи ник для удаления в чат &7(пустое = отмена, 30 сек):"));
            plugin.menus().markInput(p, r.name,
                    owner ? ChatMenu.InputKind.DEL_OWNER : ChatMenu.InputKind.DEL_MEMBER);
            scheduleInputTimeout(p);
        }
    }

    /** Shared add logic: dialog submit + chat fallback. */
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
                send(p, "&a" + realName + " &7теперь владелец &f" + r.name);
            }
        } else {
            if (r.isOwner(uuid)) {
                send(p, "&e" + realName + " &7уже владелец.");
            } else if (r.members.containsKey(uuid)) {
                send(p, "&e" + realName + " &7уже в списке.");
            } else {
                r.members.put(uuid, realName);
                plugin.storage().saveAsync();
                send(p, "&a" + realName + " &7добавлен в &f" + r.name);
            }
        }
        ChatMenu.sPlus(plugin, p);
        plugin.menus().regionMenu(p, r);
    }

    /** Shared delete-by-UUID logic (picker window). */
    @SuppressWarnings("deprecation")
    public void applyDelUuid(Player p, String region, String uuidStr, boolean owner) {
        Region r = owned(p, region);
        if (r == null) return;
        UUID uuid;
        try {
            uuid = UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
            return;
        }
        if (owner) {
            if (r.owners.remove(uuid) != null) {
                plugin.storage().saveAsync();
                send(p, "&eИгрок &7больше не владелец.");
            }
        } else {
            if (r.members.remove(uuid) != null) {
                plugin.storage().saveAsync();
                send(p, "&eИгрок &7убран из региона.");
            }
        }
        ChatMenu.sMinus(plugin, p);
        plugin.menus().regionMenu(p, r);
    }

    /** Shared delete-by-nick logic (chat fallback). */
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

    /** Shared rename logic (dialog + chat fallback). */
    public void applyRename(Player p, String old, String text) {
        Region r = owned(p, old);
        if (r == null) return;
        if (plugin.storage().rename(old, text)) {
            plugin.storage().saveAsync();
            send(p, "&aРегион &f" + old + " &aтеперь &f" + text);
            Region updated = plugin.storage().get(text);
            if (updated != null) plugin.menus().regionMenu(p, updated);
        } else {
            send(p, "&cПлохо: имя занято или не подходит (латиница 3-16).");
        }
    }

    /** Super simple help: 3 commands + buttons do the rest. */
    private void helpMenu(Player p) {
        send(p, "<gradient:#5DFF6E:#00E5FF>Как приватить:</gradient>");
        send(p, "&71. Встань в один угол, введи &e/area pos1");
        send(p, "&72. Встань в другой угол, введи &e/area pos2");
        send(p, "&73. Введи &e/area &7и выбери &6[На всю высоту] &7или &a[Как выделил]");
        send(p, "&7Владельцев и игроков добавляют кнопки &a[+] &7и &c[-] &7— ник пишется в чат.");
        send(p, "&7Удалить регион: &e/area remove <название>");
    }

    private String xyz(Location l) {
        return l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ()
                + " (" + l.getWorld().getName() + ")";
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
        ChatMenu.sDelete(plugin, p);
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
        try {
            com.vitalis.rg.menu.DialogMenu.textInput(plugin, p,
                    Msg.color(plugin.menus().grad("Новый регион" + (vert ? " · на всю высоту" : ""))),
                    "Название (латиница 3-16):",
                    text -> {
                        if (text.isEmpty()) {
                            send(p, "&7Отменено.");
                            return;
                        }
                        createFromChat(p, text, vert);
                    });
        } catch (Throwable t) {
            p.sendMessage(Msg.color(plugin.cfg().prefix + "&eВведи название региона в чат "
                    + (vert ? "&6(на всю высоту) " : "")
                    + "&7(латиница 3-16, пустое = отмена, 30 сек):"));
            plugin.menus().markInput(p, "", ChatMenu.InputKind.CREATE_NAME, vert);
            scheduleInputTimeout(p);
        }
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
                send(p, "&cТут уже есть регион &f" + o.name + " &c— выбери другое место (края тоже заняты).");
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
        int oldMin = r.minY, oldMax = r.maxY;
        r.minY = w.getMinHeight();
        r.maxY = w.getMaxHeight() - 1;
        // expanded region must not swallow neighbours — revert on overlap
        for (Region o : plugin.storage().all()) {
            if (o == r || o.name.equalsIgnoreCase(r.name)) continue;
            if (o.intersects(r)) {
                r.minY = oldMin;
                r.maxY = oldMax;
                send(p, "&cТут уже есть регион &f" + o.name + " &c— на всю высоту не растянуть.");
                return;
            }
        }
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
