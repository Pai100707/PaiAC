package com.paimc.anticheat;

import com.paimc.anticheat.checks.CheckManager;
import com.paimc.anticheat.checks.anchor.AnchorChargeCheck;
import com.paimc.anticheat.checks.anchor.AnchorSequenceCheck;
import com.paimc.anticheat.checks.anchor.AnchorSwitchCheck;
import com.paimc.anticheat.checks.combat.AutoClickerCheck;
import com.paimc.anticheat.checks.combat.KillAuraCheck;
import com.paimc.anticheat.checks.combat.ReachCheck;
import com.paimc.anticheat.checks.crystal.CrystalMacroCheck;
import com.paimc.anticheat.checks.inventory.AutoTotemCheck;
import com.paimc.anticheat.checks.inventory.FastXpBottleCheck;
import com.paimc.anticheat.checks.movement.BoatFlyCheck;
import com.paimc.anticheat.checks.movement.FlyCheck;
import com.paimc.anticheat.checks.movement.SpeedCheck;
import com.paimc.anticheat.checks.xray.XrayDiagnostics;
import com.paimc.anticheat.commands.PaiACCommand;
import com.paimc.anticheat.config.ConfigManager;
import com.paimc.anticheat.config.Settings;
import com.paimc.anticheat.listener.CheckListener;
import com.paimc.anticheat.listener.ConnectionListener;
import com.paimc.anticheat.packet.NoopPacketAdapter;
import com.paimc.anticheat.packet.PacketAdapter;
import com.paimc.anticheat.platform.BukkitSchedulerAdapter;
import com.paimc.anticheat.platform.FoliaSchedulerAdapter;
import com.paimc.anticheat.platform.PlatformInfo;
import com.paimc.anticheat.platform.SchedulerAdapter;
import com.paimc.anticheat.platform.TpsMonitor;
import com.paimc.anticheat.player.MovementTracker;
import com.paimc.anticheat.player.PlayerStateManager;
import com.paimc.anticheat.punishment.BanManager;
import com.paimc.anticheat.punishment.PunishmentManager;
import com.paimc.anticheat.storage.StorageService;
import com.paimc.anticheat.violation.AlertManager;
import com.paimc.anticheat.violation.EvidenceManager;
import com.paimc.anticheat.violation.ViolationManager;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;

public final class PaiAC extends JavaPlugin {
    private PlatformInfo platform;
    private SchedulerAdapter scheduler;
    private ConfigManager config;
    private TpsMonitor tps;
    private StorageService storage;
    private PlayerStateManager states;
    private MovementTracker movement;
    private CheckManager checks;
    private ViolationManager violations;
    private EvidenceManager evidence;
    private AlertManager alerts;
    private PunishmentManager punishments;
    private BanManager bans;
    private PacketAdapter packets;
    private XrayDiagnostics xray;
    private volatile boolean debugOverride;
    private boolean started;

    @Override
    public void onEnable() {
        platform = PlatformInfo.detect();
        config = new ConfigManager(this);
        config.load();
        if (platform.folia && !config.settings().foliaEnabled) {
            getLogger().severe("Folia detected but compatibility.folia.enabled=false. Disabling PaiAC.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        scheduler = createScheduler();
        packets = new NoopPacketAdapter();

        tps = new TpsMonitor();
        tps.start(scheduler);

        storage = new StorageService(this);
        storage.init(config.settings());
        bans = new BanManager(this);
        bans.load();

        states = new PlayerStateManager(this);
        movement = new MovementTracker(this);
        violations = new ViolationManager(this);
        evidence = new EvidenceManager(this);
        alerts = new AlertManager(this);
        punishments = new PunishmentManager(this);
        xray = new XrayDiagnostics(this);

        checks = new CheckManager(this);
        checks.register(new FlyCheck(this));
        checks.register(new SpeedCheck(this));
        checks.register(new BoatFlyCheck(this));
        checks.register(new ReachCheck(this));
        checks.register(new KillAuraCheck(this));
        checks.register(new AutoClickerCheck(this));
        checks.register(new CrystalMacroCheck(this));
        checks.register(new AnchorChargeCheck(this));
        checks.register(new AnchorSequenceCheck(this));
        checks.register(new AnchorSwitchCheck(this));
        checks.register(new AutoTotemCheck(this));
        checks.register(new FastXpBottleCheck(this));
        checks.applyConfig(config);
        punishments.reload();

        saveResourceIfMissing("xray/paper-world-defaults.example.yml");

        getServer().getPluginManager().registerEvents(new ConnectionListener(this), this);
        getServer().getPluginManager().registerEvents(new CheckListener(this), this);
        PaiACCommand cmd = new PaiACCommand(this);
        var pc = getCommand("paiac");
        if (pc != null) {
            pc.setExecutor(cmd);
            pc.setTabCompleter(cmd);
        }

        scheduler.runAsyncRepeating(violations::decayTick, 1200, 1200);
        long saveTicks = config.settings().autosaveSeconds * 20L;
        scheduler.runAsyncRepeating(states::saveAll, saveTicks, saveTicks);

        // players already online (e.g. after /reload): create their state
        for (Player p : getServer().getOnlinePlayers()) states.getOrCreate(p);

        started = true;
        logStartup();
    }

    @Override
    public void onDisable() {
        if (!started) return;
        if (scheduler != null) scheduler.cancelAll();
        if (states != null) states.saveAll();
        if (storage != null) storage.close();
        if (states != null) states.clear();
    }

    private SchedulerAdapter createScheduler() {
        if (platform.folia) return new FoliaSchedulerAdapter(this);
        return new BukkitSchedulerAdapter(this);
    }

    private void saveResourceIfMissing(String path) {
        if (!new File(getDataFolder(), path).exists()) {
            try {
                saveResource(path, false);
            } catch (IllegalArgumentException ignored) {
                // resource not bundled
            }
        }
    }

    /** Reloads all YAML files and re-applies check settings. Storage and platform are not re-initialised. */
    public List<String> reload() {
        config.load();
        checks.applyConfig(config);
        punishments.reload();
        List<String> w = config.drainWarnings();
        for (String s : w) getLogger().warning(s);
        return w;
    }

    private void logStartup() {
        getLogger().info("PaiAC " + getDescription().getVersion() + " on " + platform.describe());
        if (!platform.inSupportedRange()) {
            getLogger().warning("Minecraft " + platform.minecraftVersion + " is outside the targeted range 1.21.1 - 1.21.11. "
                    + "PaiAC loads, but behaviour on this version is untested.");
        }
        long enabled = checks.all().stream().filter(c -> c.enabled()).count();
        getLogger().info(enabled + "/" + checks.all().size() + " checks enabled | mode "
                + (punishments.enforcing() ? "ENFORCE" : "DETECTION_ONLY (no punishments are executed)")
                + " | storage " + (storage.available() ? "SQLite" : "memory only") + " | packets: " + packets.name());
        if (config.settings().reportUnsupported) {
            for (String f : packets.degradedFeatures()) getLogger().info("Reduced without packets: " + f);
        }
        for (String line : xray.report()) getLogger().info("[anti-xray] " + line);
        for (String w : config.drainWarnings()) getLogger().warning(w);
    }

    public boolean debugEnabled() { return debugOverride || (config != null && config.settings().debug); }

    public void setDebugOverride(boolean v) { debugOverride = v; }

    public PlatformInfo platform() { return platform; }
    public SchedulerAdapter scheduler() { return scheduler; }
    public ConfigManager config() { return config; }
    public Settings settings() { return config.settings(); }
    public TpsMonitor tps() { return tps; }
    public StorageService storage() { return storage; }
    public PlayerStateManager states() { return states; }
    public MovementTracker movement() { return movement; }
    public CheckManager checks() { return checks; }
    public ViolationManager violations() { return violations; }
    public EvidenceManager evidence() { return evidence; }
    public AlertManager alerts() { return alerts; }
    public PunishmentManager punishments() { return punishments; }
    public BanManager bans() { return bans; }
    public PacketAdapter packets() { return packets; }
    public XrayDiagnostics xray() { return xray; }
}
