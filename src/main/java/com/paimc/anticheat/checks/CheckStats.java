package com.paimc.anticheat.checks;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public final class CheckStats {
    public final LongAdder calls = new LongAdder();
    public final LongAdder totalNs = new LongAdder();
    public final LongAdder flags = new LongAdder();
    public final LongAdder errors = new LongAdder();
    public final LongAdder lagSkips = new LongAdder();
    public final AtomicLong maxNs = new AtomicLong();

    public void record(long ns) {
        calls.increment();
        totalNs.add(ns);
        long prev;
        while (ns > (prev = maxNs.get())) {
            if (maxNs.compareAndSet(prev, ns)) break;
        }
    }

    public double avgMicros() {
        long c = calls.sum();
        return c == 0 ? 0 : totalNs.sum() / 1000.0 / c;
    }
}
