package com.vitalis.rg.menu;

import com.vitalis.rg.Regions;
import com.vitalis.rg.model.Region;
import com.vitalis.rg.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * All management happens in chat with clickable buttons.
 * No GUI windows: /area prints this menu, buttons run hidden /area routes.
 */
public class ChatMenu {

    public enum InputKind { CREATE_NAME, ADD_OWNER, DEL_OWNER, ADD_MEMBER, DEL_MEMBER }

    public record Pending(String region, InputKind kind) {}

    private final Regions plugin;
    private final Map<UUID, Pending> inputs = new HashMap<>();
    /** "uuid:region" -> delete confirm deadline */
    private final Map<String, Long> delConfirm = new HashMap<>();

    public ChatMenu(Regions plugin) {
        this.plugin = plugin;
    }

    public void markInput(Player p, String region, InputKind kind) {
        inputs.put(p.getUniqueId(), new Pending(region, kind));
    }

    public Pending takeInput(Player p) {
        return inputs.remove(p.getUniqueId());
    }

    public void cancelInput(Player p) {
        inputs.remove(p.getUniqueId());
    }

    /** Two-step delete inside chat menu. */
    public boolean confirmDelete(Player p, String region, long windowMs) {
        String key = p.getUniqueId() + ":" + region.toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();
        Long until = delConfirm.get(key);
        if (until != null && until > now) {
            delConfirm.remove(key);
            return true;
        }
        delConfirm.put(key, now + windowMs);
        return false;
    }

    // ---------------- buttons ----------------

    /** Green/red clickable button. */
    public static Component btn(String text, NamedTextColor color, String command, String hover) {
        return Component.text("[" + text + "]")
                .color(color)
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text(hover)));
    }

    /** Button that inserts text into chat instead of running. */
    public static Component insertBtn(String text, NamedTextColor color, String toInsert, String hover) {
        return Component.text("[" + text + "]")
                .color(color)
                .clickEvent(ClickEvent.suggestCommand(toInsert))
                .hoverEvent(HoverEvent.showText(Component.text(hover)));
    }

    private void send(Player p, String msg) {
        p.sendMessage(Msg.color(plugin.cfg().prefix + msg));
    }

    private void clickSound(Player p) {
        if (plugin.cfg().sounds) p.playSound(p.getLocation(),
                org.bukkit.Sound.UI_BUTTON_CLICK, 0.7f, 1f);
    }

    // ---------------- main menu (/area) ----------------

    public void mainMenu(Player p) {
        clickSound(p);
        List<Region> mine = new ArrayList<>();
        for (Region r : plugin.storage().all()) {
            if (r.isOwner(p.getUniqueId()) || r.isMember(p.getUniqueId())) mine.add(r);
        }
        int max = plugin.cfg().maxPerPlayer;
        p.sendMessage(Msg.color("<gradient:#5DFF6E:#00E5FF>Регионы</gradient> <gray>(" + mine.size() + "/" + max + "):"));
        if (mine.isEmpty()) {
            send(p, "&cРегионов нет. &7Создай первый кнопкой ниже.");
        }
        for (Region r : mine) {
            String role = r.isOwner(p.getUniqueId()) ? "<green>владелец" : "<aqua>игрок";
            p.sendMessage(Msg.color(plugin.cfg().prefix + "&f" + r.name + " &7"
                            + r.sizeX() + "x" + r.sizeY() + "x" + r.sizeZ() + " " + role + " ")
                    .append(btn("настроить", NamedTextColor.GREEN, "/area sel " + r.name, "Открыть настройки"))
                    .append(Component.text(" "))
                    .append(btn("тп", NamedTextColor.AQUA, "/area tp " + r.name, "Телепорт в регион")));
        }
        // selection row
        var sel = plugin.selections().get(p.getUniqueId());
        p.sendMessage(Component.empty());
        if (sel.complete()) {
            p.sendMessage(Msg.color(plugin.cfg().prefix + "&7Выделено, создавай: ")
                    .append(btn("+ Создать регион", NamedTextColor.GREEN, "/area mk", "Придумать название в чате")));
        } else {
            p.sendMessage(Msg.color(plugin.cfg().prefix + "&7Выдели территорию: ")
                    .append(btn("точка 1", NamedTextColor.GREEN, "/area pos1", "Поставить точку 1 где стоишь"))
                    .append(Component.text(" "))
                    .append(btn("точка 2", NamedTextColor.GREEN, "/area pos2", "Поставить точку 2 где стоишь"))
                    .append(Component.text(" "))
                    .append(btn("жезл", NamedTextColor.GOLD, "/area gwand", "Выдать золотой топор-жезл")));
        }
    }

    // ---------------- region menu ----------------

    public void regionMenu(Player p, Region r) {
        clickSound(p);
        boolean owner = r.isOwner(p.getUniqueId());
        boolean member = r.isMember(p.getUniqueId());
        if (!owner && !member) {
            send(p, "&cНет доступа.");
            return;
        }
        p.sendMessage(Msg.color("<gradient:#5DFF6E:#00E5FF>Регион " + r.name + "</gradient> "
                + "<gray>" + r.world + " " + r.sizeX() + "x" + r.sizeY() + "x" + r.sizeZ()
                + " | владелец: " + r.ownerName));
        // owners: names, then [+] [−] (nick typed in chat)
        var oLine = Component.text();
        oLine.append(Component.text("Владельцы: ").color(NamedTextColor.GOLD));
        for (Map.Entry<UUID, String> e : r.owners.entrySet()) {
            oLine.append(Component.text(e.getValue() + " ").color(NamedTextColor.WHITE));
        }
        if (owner) {
            oLine.append(btn("+", NamedTextColor.GREEN, "/area ownadd " + r.name, "Добавить: ник в чат"))
                    .append(Component.text(" "))
                    .append(btn("−", NamedTextColor.RED, "/area owndel " + r.name, "Убрать: ник в чат"));
        }
        p.sendMessage(oLine.build());
        // members: names, then [+] [−]
        var mLine = Component.text();
        mLine.append(Component.text("Игроки: ").color(NamedTextColor.AQUA));
        if (r.members.isEmpty()) mLine.append(Component.text("— ").color(NamedTextColor.GRAY));
        int shown = 0;
        for (Map.Entry<UUID, String> e : r.members.entrySet()) {
            if (shown++ >= 10) {
                mLine.append(Component.text("...и ещё " + (r.members.size() - 10)).color(NamedTextColor.GRAY));
                break;
            }
            mLine.append(Component.text(e.getValue() + " ").color(NamedTextColor.WHITE));
        }
        if (owner) {
            mLine.append(btn("+", NamedTextColor.GREEN, "/area memadd " + r.name, "Добавить: ник в чат"))
                    .append(Component.text(" "))
                    .append(btn("−", NamedTextColor.RED, "/area memdel " + r.name, "Убрать: ник в чат"));
        }
        p.sendMessage(mLine.build());
        // flags (owners only clickable, members see state)
        var fLine = Component.text();
        fLine.append(Component.text("Флаги: ").color(NamedTextColor.GRAY));
        fLine.append(flagBtn("Огонь", r.fireSpread, owner ? "/area tgl " + r.name + " fire" : null)).append(Component.text(" "));
        fLine.append(flagBtn("PVP", r.pvp, owner ? "/area tgl " + r.name + " pvp" : null)).append(Component.text(" "));
        fLine.append(flagBtn("Взрывы", r.explosions, owner ? "/area tgl " + r.name + " expl" : null)).append(Component.text(" "));
        fLine.append(flagBtn("Механизмы", r.mechanisms, owner ? "/area tgl " + r.name + " mech" : null)).append(Component.text(" "));
        fLine.append(flagBtn("Хомы", r.homes, owner ? "/area tgl " + r.name + " homes" : null));
        p.sendMessage(fLine.build());
        // actions
        var aLine = Component.text();
        if (member || owner) {
            aLine.append(btn("Телепорт", NamedTextColor.AQUA, "/area tp " + r.name, "В центр региона")).append(Component.text(" "));
        }
        if (owner) {
            aLine.append(btn("Удалить", NamedTextColor.RED, "/area del " + r.name, "Удаление с подтверждением"));
        }
        p.sendMessage(aLine.build());
    }

    private static Component flagBtn(String name, boolean on, String command) {
        Component c = Component.text("[" + name + ": " + (on ? "ВКЛ" : "ВЫКЛ") + "]")
                .color(on ? NamedTextColor.GREEN : NamedTextColor.RED);
        if (command == null) {
            return c.hoverEvent(HoverEvent.showText(Component.text("Только владелец меняет")));
        }
        return c.clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text("Клик — переключить")));
    }
}
