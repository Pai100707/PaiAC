package com.paimc.anticheat.storage;

import com.paimc.anticheat.PaiAC;
import com.paimc.anticheat.config.Settings;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SQLite persistence. The JDBC driver ships with Spigot/Paper, so it is loaded reflectively; when it is
 * missing PaiAC keeps running with in-memory state only. All SQL runs on one dedicated thread; the queue
 * is bounded and overflow is dropped (and counted) so a slow disk can never stall the game.
 */
public final class StorageService {
    private final PaiAC plugin;
    private Connection conn;
    private ThreadPoolExecutor exec;
    private boolean available;
    private boolean async = true;
    private final AtomicLong dropped = new AtomicLong();

    public StorageService(PaiAC plugin) {
        this.plugin = plugin;
    }

    public boolean available() { return available; }

    public long dropped() { return dropped.get(); }

    public void init(Settings s) {
        this.async = s.asyncStorage;
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            plugin.getLogger().warning("SQLite driver (org.sqlite.JDBC) not found: running WITHOUT persistence "
                    + "(violations, evidence and bans live in memory only).");
            return;
        }
        try {
            File db = new File(plugin.getDataFolder(), s.storageFile);
            if (db.getParentFile() != null) db.getParentFile().mkdirs();
            conn = DriverManager.getConnection("jdbc:sqlite:" + db.getAbsolutePath());
            try (Statement st = conn.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("PRAGMA synchronous=NORMAL");
                st.execute("CREATE TABLE IF NOT EXISTS violations(uuid TEXT NOT NULL, check_id TEXT NOT NULL, vl REAL NOT NULL, updated INTEGER NOT NULL, PRIMARY KEY(uuid, check_id))");
                st.execute("CREATE TABLE IF NOT EXISTS evidence(id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER NOT NULL, uuid TEXT NOT NULL, name TEXT, check_id TEXT NOT NULL, observed REAL, expected REAL, ping INTEGER, tps REAL, platform TEXT, mc_version TEXT, vl_before REAL, vl_after REAL, details TEXT)");
                st.execute("CREATE INDEX IF NOT EXISTS idx_evidence_uuid ON evidence(uuid, ts)");
                st.execute("CREATE TABLE IF NOT EXISTS punishments(id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER NOT NULL, uuid TEXT NOT NULL, name TEXT, check_id TEXT, action TEXT, reason TEXT, vl REAL, expires INTEGER, dry INTEGER, executor TEXT)");
                st.execute("CREATE TABLE IF NOT EXISTS bans(uuid TEXT PRIMARY KEY, name TEXT, reason TEXT, created INTEGER, expires INTEGER, by TEXT, active INTEGER NOT NULL DEFAULT 1)");
                st.execute("CREATE TABLE IF NOT EXISTS baselines(id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER NOT NULL, uuid TEXT NOT NULL, check_id TEXT NOT NULL, metric TEXT NOT NULL, v1 REAL, v2 REAL, v3 REAL, ping INTEGER)");
            }
            int cap = s.maxPendingEvidence;
            exec = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(cap),
                    r -> {
                        Thread t = new Thread(r, "PaiAC-Storage");
                        t.setDaemon(true);
                        return t;
                    },
                    (r, ex) -> dropped.incrementAndGet());
            available = true;
            if (s.evidenceRetentionDays > 0) purgeOlderThan(s.evidenceRetentionDays);
        } catch (SQLException e) {
            plugin.getLogger().severe("SQLite init failed, continuing without persistence: " + e.getMessage());
            closeQuietly();
        }
    }

    private void run(Runnable r) {
        if (!available) return;
        if (!async) {
            r.run();
            return;
        }
        try {
            exec.execute(r);
        } catch (RejectedExecutionException e) {
            dropped.incrementAndGet();
        }
    }

    private void sql(String what, SqlTask t) {
        run(() -> {
            try {
                t.run();
            } catch (SQLException e) {
                plugin.getLogger().warning("Storage error (" + what + "): " + e.getMessage());
            }
        });
    }

    private interface SqlTask {
        void run() throws SQLException;
    }

    // ---------------------------------------------------------------- writes
    public void insertEvidence(Evidence e) {
        sql("evidence", () -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO evidence(ts,uuid,name,check_id,observed,expected,ping,tps,platform,mc_version,vl_before,vl_after,details) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
                ps.setLong(1, e.ts());
                ps.setString(2, e.uuid().toString());
                ps.setString(3, e.name());
                ps.setString(4, e.checkId());
                ps.setDouble(5, e.observed());
                ps.setDouble(6, e.expected());
                ps.setInt(7, e.ping());
                ps.setDouble(8, e.tps());
                ps.setString(9, e.platform());
                ps.setString(10, e.mcVersion());
                ps.setDouble(11, e.vlBefore());
                ps.setDouble(12, e.vlAfter());
                ps.setString(13, e.details());
                ps.executeUpdate();
            }
        });
    }

    public void saveViolations(UUID id, Map<String, Double> vls) {
        long now = System.currentTimeMillis();
        sql("save-vl", () -> {
            try (PreparedStatement del = conn.prepareStatement("DELETE FROM violations WHERE uuid=?")) {
                del.setString(1, id.toString());
                del.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO violations(uuid,check_id,vl,updated) VALUES(?,?,?,?)")) {
                for (Map.Entry<String, Double> en : vls.entrySet()) {
                    if (en.getValue() <= 0) continue;
                    ps.setString(1, id.toString());
                    ps.setString(2, en.getKey());
                    ps.setDouble(3, en.getValue());
                    ps.setLong(4, now);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        });
    }

    public void insertPunishment(PunishmentRecord r) {
        sql("punishment", () -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO punishments(ts,uuid,name,check_id,action,reason,vl,expires,dry,executor) VALUES(?,?,?,?,?,?,?,?,?,?)")) {
                ps.setLong(1, r.ts());
                ps.setString(2, r.uuid().toString());
                ps.setString(3, r.name());
                ps.setString(4, r.checkId());
                ps.setString(5, r.action());
                ps.setString(6, r.reason());
                ps.setDouble(7, r.vl());
                ps.setLong(8, r.expires());
                ps.setInt(9, r.dryRun() ? 1 : 0);
                ps.setString(10, r.executor());
                ps.executeUpdate();
            }
        });
    }

    public void upsertBan(BanEntry b) {
        sql("ban", () -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT OR REPLACE INTO bans(uuid,name,reason,created,expires,by,active) VALUES(?,?,?,?,?,?,1)")) {
                ps.setString(1, b.uuid().toString());
                ps.setString(2, b.name());
                ps.setString(3, b.reason());
                ps.setLong(4, b.created());
                ps.setLong(5, b.expires());
                ps.setString(6, b.by());
                ps.executeUpdate();
            }
        });
    }

    public void deactivateBan(UUID id) {
        sql("unban", () -> {
            try (PreparedStatement ps = conn.prepareStatement("UPDATE bans SET active=0 WHERE uuid=?")) {
                ps.setString(1, id.toString());
                ps.executeUpdate();
            }
        });
    }

    public void insertBaseline(UUID id, String checkId, String metric, double v1, double v2, double v3, int ping) {
        sql("baseline", () -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO baselines(ts,uuid,check_id,metric,v1,v2,v3,ping) VALUES(?,?,?,?,?,?,?,?)")) {
                ps.setLong(1, System.currentTimeMillis());
                ps.setString(2, id.toString());
                ps.setString(3, checkId);
                ps.setString(4, metric);
                ps.setDouble(5, v1);
                ps.setDouble(6, v2);
                ps.setDouble(7, v3);
                ps.setInt(8, ping);
                ps.executeUpdate();
            }
        });
    }

    private void purgeOlderThan(int days) {
        long cutoff = System.currentTimeMillis() - days * 86_400_000L;
        sql("purge", () -> {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM evidence WHERE ts < ?")) {
                ps.setLong(1, cutoff);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM baselines WHERE ts < ?")) {
                ps.setLong(1, cutoff);
                ps.executeUpdate();
            }
        });
    }

    // ---------------------------------------------------------------- reads
    public CompletableFuture<Map<String, Double>> loadViolations(UUID id) {
        CompletableFuture<Map<String, Double>> f = new CompletableFuture<>();
        if (!available) {
            f.complete(new HashMap<>());
            return f;
        }
        run(() -> {
            Map<String, Double> out = new HashMap<>();
            try (PreparedStatement ps = conn.prepareStatement("SELECT check_id, vl FROM violations WHERE uuid=?")) {
                ps.setString(1, id.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) out.put(rs.getString(1), rs.getDouble(2));
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Storage error (load-vl): " + e.getMessage());
            }
            f.complete(out);
        });
        if (!f.isDone() && dropped.get() > 0) {
            // queue was full: do not leave the future hanging forever
            f.completeOnTimeout(new HashMap<>(), 5, TimeUnit.SECONDS);
        }
        return f;
    }

    /** Blocking; call only during startup before any async work has been queued. */
    public List<BanEntry> loadActiveBansBlocking() {
        List<BanEntry> out = new ArrayList<>();
        if (!available) return out;
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT uuid,name,reason,created,expires,by FROM bans WHERE active=1")) {
            while (rs.next()) {
                out.add(new BanEntry(UUID.fromString(rs.getString(1)), rs.getString(2), rs.getString(3),
                        rs.getLong(4), rs.getLong(5), rs.getString(6)));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not load bans: " + e.getMessage());
        }
        return out;
    }

    /** Last evidence rows for a player (blocking, command thread only, small limit). */
    public CompletableFuture<List<String>> recentEvidence(UUID id, int limit) {
        CompletableFuture<List<String>> f = new CompletableFuture<>();
        if (!available) {
            f.complete(List.of());
            return f;
        }
        run(() -> {
            List<String> rows = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT ts,check_id,observed,expected,vl_after,details FROM evidence WHERE uuid=? ORDER BY ts DESC LIMIT ?")) {
                ps.setString(1, id.toString());
                ps.setInt(2, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        rows.add(rs.getString(2) + " vl=" + String.format("%.1f", rs.getDouble(5))
                                + " obs=" + String.format("%.3f", rs.getDouble(3))
                                + " exp=" + String.format("%.3f", rs.getDouble(4))
                                + " " + rs.getString(6));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Storage error (recent-evidence): " + e.getMessage());
            }
            f.complete(rows);
        });
        f.completeOnTimeout(List.of(), 5, TimeUnit.SECONDS);
        return f;
    }

    // ---------------------------------------------------------------- lifecycle
    public void close() {
        if (exec != null) {
            exec.shutdown();
            try {
                if (!exec.awaitTermination(8, TimeUnit.SECONDS)) {
                    plugin.getLogger().warning("Storage queue did not drain in time; some data was lost.");
                    exec.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        closeQuietly();
        available = false;
    }

    private void closeQuietly() {
        try {
            if (conn != null) conn.close();
        } catch (SQLException ignored) {
            // nothing useful to do
        }
        conn = null;
    }
}
