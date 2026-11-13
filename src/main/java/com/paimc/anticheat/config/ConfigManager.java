package com.paimc.anticheat.config;

import com.paimc.anticheat.PaiAC;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads every YAML file. A broken file never aborts startup: the bundled defaults are used in memory
 * and the user's file is left untouched. Older files are backed up, then missing keys are merged in.
 */
public final class ConfigManager {
    public static final String[] ROOT_FILES = {"config.yml", "messages.yml", "punishments.yml"};
    public static final String[] CHECK_FILES = {"combat", "crystal", "anchor", "inventory", "movement", "xray"};

    private final PaiAC plugin;
    private final List<String> warnings = new ArrayList<>();
    private final Map<String, YamlConfiguration> files = new HashMap<>();
    private Settings settings;

    public ConfigManager(PaiAC plugin) {
        this.plugin = plugin;
    }

    public void load() {
        warnings.clear();
        files.clear();
        for (String f : ROOT_FILES) files.put(f, loadFile(f));
        for (String f : CHECK_FILES) files.put("checks/" + f + ".yml", loadFile("checks/" + f + ".yml"));
        settings = Settings.from(files.get("config.yml"));
    }

    public Settings settings() { return settings; }

    public YamlConfiguration punishments() { return files.get("punishments.yml"); }

    public YamlConfiguration checkFile(String name) { return files.get("checks/" + name + ".yml"); }

    public String msg(String key, String def) {
        YamlConfiguration m = files.get("messages.yml");
        return m == null ? def : m.getString(key, def);
    }

    public CheckSettings checkSettings(String file, String path, String id) {
        YamlConfiguration cfg = checkFile(file);
        ConfigurationSection s = cfg == null ? null : cfg.getConfigurationSection(path);
        return CheckSettings.from(id, s, warnings);
    }

    public List<String> drainWarnings() {
        List<String> copy = new ArrayList<>(warnings);
        warnings.clear();
        return copy;
    }

    public void warn(String w) { warnings.add(w); }

    private YamlConfiguration loadFile(String path) {
        YamlConfiguration bundled = loadBundled(path);
        File f = new File(plugin.getDataFolder(), path);
        if (!f.exists()) {
            try {
                plugin.saveResource(path, false);
            } catch (IllegalArgumentException ex) {
                warnings.add(path + ": bundled resource missing from jar (" + ex.getMessage() + ")");
                return bundled;
            }
        }
        YamlConfiguration cfg = new YamlConfiguration();
        try {
            cfg.load(f);
        } catch (IOException | InvalidConfigurationException ex) {
            plugin.getLogger().severe(path + " could not be parsed, using bundled defaults (file left untouched): " + ex.getMessage());
            warnings.add(path + ": INVALID YAML, defaults in use. Fix the file and run /paiac reload.");
            return bundled;
        }

        int fileVersion = cfg.getInt("config-version", 0);
        int bundledVersion = bundled.getInt("config-version", 1);
        List<String> missing = new ArrayList<>();
        for (String key : bundled.getKeys(true)) {
            if (bundled.isConfigurationSection(key)) continue;
            if (!cfg.contains(key, true)) missing.add(key);
        }

        if (fileVersion < bundledVersion) {
            if (backup(f)) {
                for (String key : missing) cfg.set(key, bundled.get(key));
                cfg.set("config-version", bundledVersion);
                try {
                    cfg.save(f);
                    plugin.getLogger().info(path + " migrated v" + fileVersion + " -> v" + bundledVersion
                            + " (" + missing.size() + " keys added, backup created).");
                } catch (IOException ex) {
                    warnings.add(path + ": migration could not be saved: " + ex.getMessage());
                }
            } else {
                warnings.add(path + ": backup failed, migration skipped; defaults used for missing keys.");
            }
        } else if (fileVersion > bundledVersion) {
            warnings.add(path + ": config-version " + fileVersion + " is newer than this plugin supports (" + bundledVersion + ").");
        } else if (!missing.isEmpty()) {
            warnings.add(path + ": " + missing.size() + " key(s) missing, defaults used (e.g. " + missing.get(0) + ").");
        }
        cfg.setDefaults(bundled);
        return cfg;
    }

    private YamlConfiguration loadBundled(String path) {
        YamlConfiguration y = new YamlConfiguration();
        try (var in = plugin.getResource(path)) {
            if (in == null) return y;
            try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                y.load(r);
            }
        } catch (IOException | InvalidConfigurationException ex) {
            plugin.getLogger().severe("Bundled " + path + " is unreadable: " + ex.getMessage());
        }
        return y;
    }

    private boolean backup(File f) {
        try {
            String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
            File bak = new File(f.getParentFile(), f.getName() + ".bak-" + stamp);
            Files.copy(f.toPath(), bak.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not back up " + f.getName() + ": " + ex.getMessage());
            return false;
        }
    }
}
