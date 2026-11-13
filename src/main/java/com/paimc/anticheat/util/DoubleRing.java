package com.paimc.anticheat.util;

/** Fixed-capacity ring buffer of doubles with simple statistics. Not thread-safe (owned by one player thread). */
public final class DoubleRing {
    private final double[] buf;
    private int head;
    private int size;

    public DoubleRing(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be >= 1");
        this.buf = new double[capacity];
    }

    public void add(double v) {
        if (size < buf.length) {
            buf[(head + size) % buf.length] = v;
            size++;
        } else {
            buf[head] = v;
            head = (head + 1) % buf.length;
        }
    }

    public int size() { return size; }

    public int capacity() { return buf.length; }

    /** i = 0 is the oldest sample. */
    public double get(int i) {
        if (i < 0 || i >= size) throw new IndexOutOfBoundsException("index " + i + " size " + size);
        return buf[(head + i) % buf.length];
    }

    public void clear() { head = 0; size = 0; }

    public double mean() {
        if (size == 0) return 0;
        double s = 0;
        for (int i = 0; i < size; i++) s += get(i);
        return s / size;
    }

    /** Population standard deviation. */
    public double stdDev() {
        if (size < 2) return 0;
        double m = mean();
        double s = 0;
        for (int i = 0; i < size; i++) {
            double d = get(i) - m;
            s += d * d;
        }
        return Math.sqrt(s / size);
    }

    public double min() {
        if (size == 0) return 0;
        double r = Double.POSITIVE_INFINITY;
        for (int i = 0; i < size; i++) r = Math.min(r, get(i));
        return r;
    }

    public double max() {
        if (size == 0) return 0;
        double r = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < size; i++) r = Math.max(r, get(i));
        return r;
    }
}
