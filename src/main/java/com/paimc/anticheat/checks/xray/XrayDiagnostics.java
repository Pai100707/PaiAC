package com.paimc.anticheat.checks.xray;

import com.paimc.anticheat.PaiAC;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * X-ray is NOT detected by a Java check. It is handled by the server's native anti-xray (Paper
 * {@code anticheat.anti-xray}, Spigot {@code anti-xray}), which stops ore data reaching the client at all.
 * This class only READS those files and reports the effective state; it never modifies server configuration.
 */
public final class XrayDiagnostics {
    private final PaiAC plugin;

    public XrayDiagnostics(PaiAC plugin) {
        this.plugin = plugin;
    }

    public List<String> report() {
        List<String> out = new ArrayList<>();
        if (!plugin.config().checkFile("xray").getBoolean("xray.diagnostics.enabled", true)) {
            out.add("Anti-Xray diagnostics disabled in checks/xray.yml");
            return out;
        }
        File root = new File(".");
        File paperDefaults = firstExisting(new File(root, "config/paper-world-defaults.yml"),
                new File(plugin.getServer().getWorldContainer(), "config/paper-world-defaults.yml"));
        if (paperDefaults != null) {
            YamlConfiguration d = load(paperDefaults);
            out.add("Paper defaults: " + describe(d, "anticheat.anti-xray"));
            for (World w : plugin.getServer().getWorlds()) {
                File wf = new File(w.getWorldFolder(), "paper-world.yml");
                if (!wf.exists()) continue;
                YamlConfiguration wc = load(wf);
                if (wc.contains("anticheat.anti-xray")) {
                    out.add("World '" + w.getName() + "' override: " + describe(wc, "anticheat.anti-xray"));
                }
            }
        } else {
            File spigot = firstExisting(new File(root, "spigot.yml"));
            if (spigot != null) {
                out.add("Spigot (world-settings.default): " + describe(load(spigot), "world-settings.default.anti-xray"));
            } else {
                out.add("No paper-world-defaults.yml / spigot.yml found: cannot verify anti-xray.");
            }
        }
        if (plugin.config().checkFile("xray").getBoolean("xray.diagnostics.warn-if-disabled", true)
                && out.stream().anyMatch(l -> l.contains("enabled=false"))) {
            out.add("WARNING: anti-xray is disabled somewhere. See xray/paper-world-defaults.example.yml in the PaiAC plugin folder.");
        }
        return out;
    }

    private static String describe(YamlConfiguration c, String base) {
        boolean enabled = c.getBoolean(base + ".enabled", false);
        return "enabled=" + enabled + " engine-mode=" + c.get(base + ".engine-mode", "?")
                + " max-block-height=" + c.get(base + ".max-block-height", "?")
                + " hidden-blocks=" + c.getStringList(base + ".hidden-blocks").size()
                + " replacement-blocks=" + c.getStringList(base + ".replacement-blocks").size();
    }

    private static YamlConfiguration load(File f) {
        try {
            return YamlConfiguration.loadConfiguration(f);
        } catch (Throwable t) {
            return new YamlConfiguration();
        }
    }

    private static File firstExisting(File... files) {
        for (File f : files) if (f.isFile()) return f;
        return null;
    }
}
