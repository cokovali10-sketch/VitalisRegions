package com.vitalis.rg.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;

/** Text helpers: legacy '&' colors + Adventure. */
public final class Msg {
    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.builder().character('&').hexColors().build();

    private Msg() {}

    public static Component color(String text) {
        if (text == null) return Component.empty();
        return LEGACY.deserialize(text);
    }

    public static String plain(Component c) {
        if (c == null) return "";
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(c);
    }

    public static String strip(String text) {
        if (text == null) return "";
        return ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', text));
    }
}
