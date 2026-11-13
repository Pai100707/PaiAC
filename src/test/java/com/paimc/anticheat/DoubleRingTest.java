package com.paimc.anticheat;

import com.paimc.anticheat.util.DoubleRing;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DoubleRingTest {
    @Test
    void overwritesOldestWhenFull() {
        DoubleRing r = new DoubleRing(3);
        for (int i = 1; i <= 5; i++) r.add(i);
        assertEquals(3, r.size());
        assertEquals(3.0, r.get(0));
        assertEquals(5.0, r.get(2));
        assertEquals(4.0, r.mean(), 1e-9);
    }

    @Test
    void statistics() {
        DoubleRing r = new DoubleRing(8);
        for (double v : new double[]{2, 4, 4, 4, 5, 5, 7, 9}) r.add(v);
        assertEquals(5.0, r.mean(), 1e-9);
        assertEquals(2.0, r.stdDev(), 1e-9);
        assertEquals(2.0, r.min());
        assertEquals(9.0, r.max());
    }

    @Test
    void emptyAndInvalid() {
        DoubleRing r = new DoubleRing(2);
        assertEquals(0.0, r.mean());
        assertEquals(0.0, r.stdDev());
        assertThrows(IndexOutOfBoundsException.class, () -> r.get(0));
        assertThrows(IllegalArgumentException.class, () -> new DoubleRing(0));
    }
}
