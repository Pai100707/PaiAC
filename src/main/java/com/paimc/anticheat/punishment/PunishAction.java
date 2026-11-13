package com.paimc.anticheat.punishment;

import java.util.Locale;

public enum PunishAction {
    ALERT, LOG, CANCEL_ACTION, SETBACK, KICK, TEMPBAN, BAN;

    public static PunishAction parse(String s) {
        if (s == null) return null;
        try {
            return valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Actions that only make sense (and are only executed) in ENFORCE mode. */
    public boolean needsEnforcement() {
        return this != ALERT && this != LOG;
    }

    public boolean isBan() {
        return this == BAN || this == TEMPBAN;
    }
}
