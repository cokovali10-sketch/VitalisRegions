package com.vitalis.rg.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Msg.color must never throw: legacy, MiniMessage, mixed, broken tags, null. */
class MsgTest {

    @Test
    void legacy() {
        assertNotNull(Msg.color("&aПривет &eмир"));
    }

    @Test
    void gradient() {
        assertNotNull(Msg.color("<gradient:#5DFF6E:#00E5FF>Регион</gradient>"));
    }

    @Test
    void mixedPrefixStyle() {
        // real usage: gradient prefix + legacy body
        assertNotNull(Msg.color("<gradient:#5DFF6E:#00E5FF>[RG]</gradient> <gray>&aСоздан &fhome"));
    }

    @Test
    void namedColorsAndDecorations() {
        assertNotNull(Msg.color("<red>Опасно</red> <bold>жирно</bold> <rainbow>радуга</rainbow>"));
    }

    @Test
    void clickable() {
        assertNotNull(Msg.color("&e/rg info home ")
                .append(net.kyori.adventure.text.Component.text("[открыть]")));
    }

    @Test
    void brokenTagFallsBack() {
        assertNotNull(Msg.color("<gradient:oops>сломано"));
        assertNotNull(Msg.color("<не тег> а просто скобки"));
    }

    @Test
    void nullSafe() {
        assertNotNull(Msg.color(null));
        assertEquals("", Msg.plain(null));
        assertEquals("", Msg.strip(null));
    }
}
