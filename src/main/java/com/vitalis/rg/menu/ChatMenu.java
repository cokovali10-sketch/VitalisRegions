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
 * All management happens in chat with clickable buttons (Heaven style).
 * No GUI windows: /area prints this menu, buttons run hidden /area routes.
 */
public class ChatMenu {

    public enum InputKind { CREATE_NAME, RENAME, ADD_OWNER, DEL_OWNER, ADD_MEMBER, DEL_MEMBER }

    public record Pending(String region, InputKind kind, boolean vert) {}

    private final Regions plugin;
    private final Map<UUID, Pending> inputs = new HashMap<>();
    /** "uuid:region" -> delete confirm deadline */
    private final Map<String, Long> delConfirm = new HashMap<>();

    public ChatMenu(Regions plugin) {
        this.plugin = plugin;
    }

    public void markInput(Player p, String region, InputKind kind) {
        markInput(p, region, kind, false);
    }

    public void markInput(Player p, String region, InputKind kind, boolean vert) {
        inputs.put(p.getUniqueId(), new Pending(region, kind, vert));
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

    // ---------------- style ----------------

    /** Heaven gradient header. */
    public String grad(String text) {
        return "<gradient:" + plugin.cfg().themeFrom + ":" + plugin.cfg().themeTo + ">" + text + "</gradient>";
    }

    private static final String LINE = "<gray><st>                                        </st>";

    private void frameTop(Player p, String title) {
        p.sendMessage(Msg.color(LINE));
        p.sendMessage(Msg.color("  " + grad("◆ " + title + " ◆")));
    }

    private void frameBottom(Player p) {
        p.sendMessage(Msg.color(LINE));
    }

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

    private static String flagState(boolean on) {
        return on ? "<green>● ВКЛ" : "<red>● ВЫКЛ";
    }

    // ---------------- main menu (/area) ----------------

    public void mainMenu(Player p) {
        clickSound(p);
        List<Region> mine = new ArrayList<>();
        for (Region r : plugin.storage().all()) {
            if (r.isOwner(p.getUniqueId()) || r.isMember(p.getUniqueId())) mine.add(r);
        }
        int max = plugin.cfg().maxPerPlayer;
        frameTop(p, "Регионы (" + mine.size() + "/" + max + ")");
        if (mine.isEmpty()) {
            send(p, "&cРегионов нет. &7Создай первый кнопкой ниже.");
        }
        for (Region r : mine) {
            String role = r.isOwner(p.getUniqueId()) ? "<gold>★" : "<aqua>●";
            Component hover = Msg.color("<gray>" + r.world + " " + r.sizeX() + "x" + r.sizeY() + "x" + r.sizeZ()
                    + "\nВладелец: " + r.ownerName
                    + "\nИгроков: " + r.members.size()
                    + "\nОгонь/взрывы: " + (r.fireSpread ? "да" : "нет") + "/" + (r.explosions ? "да" : "нет"));
            p.sendMessage(Msg.color(plugin.cfg().prefix + "&f" + r.name + " " + role + " ")
                    .hoverEvent(HoverEvent.showText(hover))
                    .append(btn("настроить", NamedTextColor.GREEN, "/area sel " + r.name, "Открыть настройки")));
        }
        // selection row
        var sel = plugin.selections().get(p.getUniqueId());
        p.sendMessage(Component.empty());
        if (sel.complete()) {
            p.sendMessage(Msg.color(plugin.cfg().prefix + "&7Выделено, как приватим?"));
            p.sendMessage(Msg.color(plugin.cfg().prefix + "")
                    .append(btn("На всю высоту", NamedTextColor.GOLD, "/area mkvert", "От bedrock до неба"))
                    .append(Component.text(" "))
                    .append(btn("Как выделил", NamedTextColor.GREEN, "/area mk", "По выбранной высоте")));
        } else {
            p.sendMessage(Msg.color(plugin.cfg().prefix + "&7Выдели территорию: ")
                    .append(btn("точка 1", NamedTextColor.GREEN, "/area pos1", "Поставить точку 1 где стоишь"))
                    .append(Component.text(" "))
                    .append(btn("точка 2", NamedTextColor.GREEN, "/area pos2", "Поставить точку 2 где стоишь"))
                    .append(Component.text(" "))
                    .append(btn("жезл", NamedTextColor.GOLD, "/area gwand", "Выдать золотой топор-жезл")));
        }
        frameBottom(p);
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
        frameTop(p, "Регион " + r.name);
        send(p, "<gray>" + r.world + " · " + r.sizeX() + "x" + r.sizeY() + "x" + r.sizeZ()
                + " · владелец " + r.ownerName);
        // owners: ★ names, then [+] [-] — hover explains, colors differ from members
        var oLine = Component.text();
        oLine.append(Component.text("★ ").color(NamedTextColor.GOLD)
                .hoverEvent(HoverEvent.showText(Component.text("Владельцы: всё можно"))));
        for (Map.Entry<UUID, String> e : r.owners.entrySet()) {
            oLine.append(Component.text(e.getValue() + " ").color(NamedTextColor.YELLOW));
        }
        if (owner) {
            oLine.append(btn("+", NamedTextColor.GREEN, "/area ownadd " + r.name, "Добавить: ник в окно"))
                    .append(Component.text(" "))
                    .append(btn("-", NamedTextColor.RED, "/area owndel " + r.name, "Убрать: выбор из списка"));
        }
        p.sendMessage(oLine.build());
        // members: ● names, then [+] [-]
        var mLine = Component.text();
        mLine.append(Component.text("● ").color(NamedTextColor.AQUA)
                .hoverEvent(HoverEvent.showText(Component.text("Игроки: всё можно, настраивать нельзя"))));
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
            mLine.append(btn("+", NamedTextColor.GREEN, "/area memadd " + r.name, "Добавить: ник в окно"))
                    .append(Component.text(" "))
                    .append(btn("-", NamedTextColor.RED, "/area memdel " + r.name, "Убрать: выбор из списка"));
        }
        p.sendMessage(mLine.build());
        // flags: ◆ + green/red states (owners clickable, members see state)
        var fLine = Component.text();
        fLine.append(Component.text("◆ ").color(NamedTextColor.GRAY)
                .hoverEvent(HoverEvent.showText(Component.text("Флаги — только для чужих. Своим можно всё"))));
        fLine.append(flagBtn("Огонь", r.fireSpread, owner ? "/area tgl " + r.name + " fire" : null)).append(Component.text(" "));
        fLine.append(flagBtn("Взрывы", r.explosions, owner ? "/area tgl " + r.name + " expl" : null)).append(Component.text(" "));
        fLine.append(flagBtn("Механизмы", r.mechanisms, owner ? "/area tgl " + r.name + " mech" : null)).append(Component.text(" "));
        fLine.append(flagBtn("Хомы", r.homes, owner ? "/area tgl " + r.name + " homes" : null));
        p.sendMessage(fLine.build());
        // actions
        if (owner) {
            var aLine = Component.text();
            aLine.append(btn("Высота", NamedTextColor.GOLD, "/area vert " + r.name, "Растянуть от bedrock до неба")).append(Component.text(" "));
            aLine.append(btn("Переименовать", NamedTextColor.YELLOW, "/area ren " + r.name, "Новое название в окно")).append(Component.text(" "));
            aLine.append(btn("Удалить", NamedTextColor.RED, "/area del " + r.name, "Удаление с подтверждением"));
            p.sendMessage(aLine.build());
        }
        frameBottom(p);
    }

    /** Delete confirm step with explicit Yes/Cancel buttons. */
    public void deleteConfirmMenu(Player p, Region r) {
        p.sendMessage(Msg.color(plugin.cfg().prefix + "<red>Точно удалить регион " + r.name + "?"));
        p.sendMessage(Msg.color(plugin.cfg().prefix + "")
                .append(btn("Да, удалить", NamedTextColor.RED, "/area delyes " + r.name, "Безвозвратно"))
                .append(Component.text(" "))
                .append(btn("Отмена", NamedTextColor.GREEN, "/area sel " + r.name, "Вернуться")));
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

    /** Small helper for tests: renders a button without server. */
    public static String buttonText(String text) {
        return "[" + text + "]";
    }
}
