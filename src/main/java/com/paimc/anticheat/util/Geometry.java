package com.paimc.anticheat.util;

import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

public final class Geometry {
    private Geometry() {}

    /** Distance from a point to the closest point of the box (0 when inside). */
    public static double distanceToBox(double x, double y, double z, BoundingBox b) {
        double cx = Math.max(b.getMinX(), Math.min(x, b.getMaxX()));
        double cy = Math.max(b.getMinY(), Math.min(y, b.getMaxY()));
        double cz = Math.max(b.getMinZ(), Math.min(z, b.getMaxZ()));
        double dx = x - cx, dy = y - cy, dz = z - cz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public static Vector direction(float yawDeg, float pitchDeg) {
        double yaw = Math.toRadians(yawDeg);
        double pitch = Math.toRadians(pitchDeg);
        double xz = Math.cos(pitch);
        return new Vector(-xz * Math.sin(yaw), -Math.sin(pitch), xz * Math.cos(yaw));
    }

    /** Angle in degrees between two vectors (0 when either is zero-length). */
    public static double angleDeg(Vector a, Vector b) {
        double la = a.length(), lb = b.length();
        if (la < 1.0E-9 || lb < 1.0E-9) return 0;
        double d = a.dot(b) / (la * lb);
        d = Math.max(-1.0, Math.min(1.0, d));
        return Math.toDegrees(Math.acos(d));
    }

    public static float wrapDegrees(float d) {
        d %= 360f;
        if (d >= 180f) d -= 360f;
        if (d < -180f) d += 360f;
        return d;
    }
}
