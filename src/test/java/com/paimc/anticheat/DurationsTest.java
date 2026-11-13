package com.paimc.anticheat;

import com.paimc.anticheat.util.Durations;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DurationsTest {
    @Test
    void parsesUnitsAndCombinations() {
        assertEquals(30_000L, Durations.parse("30s"));
        assertEquals(90 * 60_000L, Durations.parse("1h30m"));
        assertEquals(86_400_000L, Durations.parse("1d"));
        assertEquals(604_800_000L, Durations.parse("1W"));
    }

    @Test
    void permanent() {
        assertEquals(-1L, Durations.parse("perm"));
        assertEquals(-1L, Durations.parse("-1"));
    }

    @Test
    void rejectsGarbage() {
        assertThrows(IllegalArgumentException.class, () -> Durations.parse("abc"));
        assertThrows(IllegalArgumentException.class, () -> Durations.parse("10"));
        assertThrows(IllegalArgumentException.class, () -> Durations.parse("0m"));
        assertThrows(IllegalArgumentException.class, () -> Durations.parse(""));
    }

    @Test
    void formats() {
        assertEquals("1h 30m", Durations.format(90 * 60_000L));
        assertEquals("permanent", Durations.format(-1));
    }
}
