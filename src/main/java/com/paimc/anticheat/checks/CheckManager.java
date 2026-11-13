package com.paimc.anticheat.checks;

import com.paimc.anticheat.PaiAC;
import com.paimc.anticheat.config.ConfigManager;
import com.paimc.anticheat.player.PlayerState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Registry + dispatcher. A throwing check is isolated and auto-disabled after repeated errors. */
public final class CheckManager {
    private static final int MAX_ERRORS = 25;

    private final PaiAC plugin;
    private final Map<String, Check> byId = new LinkedHashMap<>();
    private final List<Check> list = new ArrayList<>();

    public CheckManager(PaiAC plugin) {
        this.plugin = plugin;
    }

    public void register(Check c) {
        if (byId.putIfAbsent(c.id(), c) != null) throw new IllegalStateException("duplicate check id " + c.id());
        list.add(c);
    }

    public void applyConfig(ConfigManager cfg) {
        for (Check c : list) c.applySettings(cfg.checkSettings(c.file(), c.path(), c.id()));
    }

    public Check get(String id) { return byId.get(id); }

    public List<Check> all() { return Collections.unmodifiableList(list); }

    public void dispatch(PlayerState s, Consumer<Check> action) {
        if (s.isExempt(System.currentTimeMillis())) return;
        for (int i = 0, n = list.size(); i < n; i++) {
            Check c = list.get(i);
            if (!c.enabled()) continue;
            long t0 = System.nanoTime();
            try {
                action.accept(c);
            } catch (Throwable t) {
                onError(c, t);
            } finally {
                c.stats().record(System.nanoTime() - t0);
            }
        }
    }

    private void onError(Check c, Throwable t) {
        c.stats().errors.increment();
        long n = c.stats().errors.sum();
        if (n <= 3) plugin.getLogger().log(java.util.logging.Level.WARNING, "Check '" + c.id() + "' threw an exception (isolated)", t);
        if (n >= MAX_ERRORS && !c.faulted()) {
            c.fault();
            plugin.getLogger().severe("Check '" + c.id() + "' disabled after " + n + " errors. Run /paiac reload after fixing the cause.");
        }
    }
}
