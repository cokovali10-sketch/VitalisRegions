package com.vitalis.rg.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;

/**
 * Text helpers: MiniMessage (gradients, rainbow) + legacy '&' colors.
 * If the text contains a '<tag>', it goes through MiniMessage,
 * otherwise through legacy colors. Broken tags fall back to legacy.
 */
public final class Msg {
    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.builder().character('&').hexColors().build();
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private Msg() {}

    public static Component color(String text) {
        if (text == null) return Component.empty();
        if (text.indexOf('<') >= 0 && text.indexOf('>') > 0) {
            try {
                // mixed mode: legacy '&' codes are converted, MiniMessage tags pass through
                return MINI.deserialize(legacyToMini(text));
            } catch (Exception e) {
                return LEGACY.deserialize(text.replaceAll("<[^>]*>", ""));
            }
        }
        return LEGACY.deserialize(text);
    }

    private static final java.util.Map<Character, String> CODES = new java.util.HashMap<>();

    static {
        CODES.put('0', "black");
        CODES.put('1', "dark_blue");
        CODES.put('2', "dark_green");
        CODES.put('3', "dark_aqua");
        CODES.put('4', "dark_red");
        CODES.put('5', "dark_purple");
        CODES.put('6', "gold");
        CODES.put('7', "gray");
        CODES.put('8', "dark_gray");
        CODES.put('9', "blue");
        CODES.put('a', "green");
        CODES.put('b', "aqua");
        CODES.put('c', "red");
        CODES.put('d', "light_purple");
        CODES.put('e', "yellow");
        CODES.put('f', "white");
        CODES.put('k', "obfuscated");
        CODES.put('l', "bold");
        CODES.put('m', "strikethrough");
        CODES.put('n', "underlined");
        CODES.put('o', "italic");
        CODES.put('r', "reset");
    }

    /** &a -> <green> etc, so legacy and MiniMessage mix freely. */
    private static String legacyToMini(String text) {
        StringBuilder out = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '&' && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(i + 1));
                String tag = CODES.get(code);
                if (tag != null) {
                    out.append('<').append(tag).append('>');
                    i++;
                    continue;
                }
            }
            out.append(c);
        }
        return out.toString();
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
