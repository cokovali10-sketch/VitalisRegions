package com.vitalis.rg.menu;

import com.vitalis.rg.Regions;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Native Paper dialog windows (no mods, no NMS).
 * Plus (+) opens a text window, minus (−) opens a nick picker.
 * Closing with Esc does nothing by design.
 */
public final class DialogMenu {
    private DialogMenu() {}

    private static ClickCallback.Options opts() {
        return ClickCallback.Options.builder()
                .uses(10)
                .lifetime(Duration.ofMinutes(5))
                .build();
    }

    /** Text input window. onSubmit runs on main thread with trimmed text ("" when empty). */
    public static void textInput(Regions plugin, Player p, Component title,
                                 String label, Consumer<String> onSubmit) {
        textInput(plugin, p, title, label, "", onSubmit);
    }

    /** Same, with pre-filled text (for refining the filter letter by letter). */
    public static void textInput(Regions plugin, Player p, Component title,
                                 String label, String initial, Consumer<String> onSubmit) {
        UUID uuid = p.getUniqueId();
        DialogAction ok = DialogAction.customClick((response, audience) -> {
            if (!(audience instanceof Player pl) || !pl.getUniqueId().equals(uuid)) return;
            String text = "";
            try {
                text = response.getText("nick");
            } catch (Exception ignored) {}
            String t = text == null ? "" : text.trim();
            Bukkit.getScheduler().runTask(plugin, () -> onSubmit.accept(t));
        }, opts());
        ActionButton yes = ActionButton.builder(Component.text("Готово")).action(ok).build();
        ActionButton no = ActionButton.builder(Component.text("Отмена")).build();
        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(title)
                        .inputs(List.of(DialogInput.text("nick", Component.text(label))
                                .maxLength(16)
                                .initial(initial == null ? "" : initial)
                                .build()))
                        .canCloseWithEscape(true)
                        .build())
                .type(DialogType.confirmation(yes, no)));
        p.showDialog(dialog);
    }

    /** Nick picker window. onPick runs on main thread with option id ("" when empty). */
    public static void select(Regions plugin, Player p, Component title,
                              List<SingleOptionDialogInput.OptionEntry> options,
                              Consumer<String> onPick) {
        UUID uuid = p.getUniqueId();
        DialogAction ok = DialogAction.customClick((response, audience) -> {
            if (!(audience instanceof Player pl) || !pl.getUniqueId().equals(uuid)) return;
            String id = "";
            try {
                id = response.getText("pick");
            } catch (Exception ignored) {}
            String t = id == null ? "" : id.trim();
            Bukkit.getScheduler().runTask(plugin, () -> onPick.accept(t));
        }, opts());
        ActionButton yes = ActionButton.builder(Component.text("Убрать")).action(ok).build();
        ActionButton no = ActionButton.builder(Component.text("Отмена")).build();
        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(title)
                        .inputs(List.of(DialogInput.singleOption("pick",
                                        Component.text("Выбери ник:"), options).build()))
                        .canCloseWithEscape(true)
                        .build())
                .type(DialogType.confirmation(yes, no)));
        p.showDialog(dialog);
    }

    /** One row of the nick picker: stable uuid + display nick. */
    public record DialogMenuEntry(String id, String nick) {
    }
}
