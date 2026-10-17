package com.vitalis.rg.listener;

import com.vitalis.rg.model.Region;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Greeting formatting: pure string work, no server needed. */
class GreetTextTest {

    private Region region() {
        return new Region("home", "world",
                0, 60, 0, 10, 70, 10,
                UUID.randomUUID(), "Steve", false, false, false, true, true);
    }

    @Test
    void placeholdersReplaced() {
        assertEquals("◆ home · Steve",
                MoveListener.text("◆ %region% · %owner%", region()));
    }

    @Test
    void nullOwnerSafe() {
        Region r = region();
        r.ownerName = null;
        assertEquals("?", MoveListener.text("%owner%", r).trim());
    }
}
