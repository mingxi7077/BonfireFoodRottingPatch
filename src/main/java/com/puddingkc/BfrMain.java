package com.puddingkc;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class BfrMain extends JavaPlugin {

    public final Map<Material, Integer> food = new ConcurrentHashMap<>();
    public FileConfiguration configuration;

    public String loreFresh;
    public String loreSpoiled;
    public String loreRotten;
    public boolean writeStateLore;
    public int scanIntervalTicks;
    public boolean mergeStacksEnabled;
    public int creationTimeBucketSeconds;
    public boolean trackCreativeMode;
    public boolean bundleProtectionEnabled;
    public final Set<Material> protectedBundleMaterials = ConcurrentHashMap.newKeySet();

    @Override
    public void onEnable() {
        loadConfig();

        PluginCommand command = getCommand("bfr");
        if (command != null) {
            command.setExecutor(new BfrCommand(this));
        } else {
            getLogger().warning("Command /bfr not found in plugin.yml");
        }

        BfrUtils utils = new BfrUtils(this);
        getServer().getPluginManager().registerEvents(new BfrListeners(this, utils), this);

        if (scanIntervalTicks > 0) {
            getServer().getScheduler().runTaskTimer(this, () -> {
                for (Player player : getServer().getOnlinePlayers()) {
                    if (!shouldTrackPlayer(player)) {
                        continue;
                    }
                    utils.checkInventory(player.getInventory());
                }
            }, scanIntervalTicks, scanIntervalTicks);
        }

        getLogger().info("BonfireFoodRotting enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("BonfireFoodRotting disabled!");
    }

    public void loadConfig() {
        saveDefaultConfig();
        reloadConfig();
        configuration = getConfig();

        loreFresh = configuration.getString("lore.fresh", "&7Food State: &aFresh");
        loreSpoiled = configuration.getString("lore.spoiled", "&7Food State: &eSpoiled");
        loreRotten = configuration.getString("lore.rotten", "&7Food State: &cRotten");
        writeStateLore = configuration.getBoolean("lore.write-state-line", true);
        scanIntervalTicks = Math.max(1, configuration.getInt("scan-interval-ticks", 40));
        mergeStacksEnabled = configuration.getBoolean("stacking.merge-enabled", true);
        creationTimeBucketSeconds = Math.max(0, configuration.getInt("stacking.creation-time-bucket-seconds", 300));
        trackCreativeMode = configuration.getBoolean("tracking.track-creative-mode", false);
        bundleProtectionEnabled = configuration.getBoolean("bundle-protection.enabled", true);

        protectedBundleMaterials.clear();
        List<String> configuredBundles = configuration.getStringList("bundle-protection.protected-materials");
        if (configuredBundles == null || configuredBundles.isEmpty()) {
            configuredBundles = List.of("WHITE_BUNDLE");
        }
        for (String bundleName : configuredBundles) {
            if (bundleName == null || bundleName.isBlank()) {
                continue;
            }

            String normalized = bundleName.trim().toUpperCase(Locale.ROOT);
            try {
                Material material = Material.valueOf(normalized);
                if (material == Material.BUNDLE || normalized.endsWith("_BUNDLE")) {
                    protectedBundleMaterials.add(material);
                } else {
                    getLogger().warning("bundle-protection ignores non-bundle material: " + normalized);
                }
            } catch (IllegalArgumentException ex) {
                getLogger().warning("Invalid bundle-protection material: " + normalized);
            }
        }
        if (protectedBundleMaterials.isEmpty()) {
            protectedBundleMaterials.add(Material.WHITE_BUNDLE);
        }

        food.clear();
        Logger logger = getLogger();

        if (configuration.contains("FoodList")) {
            List<String> list = configuration.getStringList("FoodList");
            for (String row : list) {
                if (row == null || row.isBlank()) {
                    continue;
                }
                String[] parts = row.split(":");
                if (parts.length != 2) {
                    logger.warning("Invalid FoodList row: " + row);
                    continue;
                }

                String typeName = parts[0].trim().toUpperCase(Locale.ROOT);
                try {
                    int timeMinutes = Integer.parseInt(parts[1].trim());
                    Material material = Material.valueOf(typeName);
                    food.put(material, timeMinutes);
                } catch (IllegalArgumentException ex) {
                    logger.warning("Invalid food item in config: " + typeName);
                }
            }
        }
    }

    public boolean isProtectedBundle(Material material) {
        return bundleProtectionEnabled && material != null && protectedBundleMaterials.contains(material);
    }

    public boolean shouldTrackPlayer(Player player) {
        if (player == null || player.getGameMode() == GameMode.SPECTATOR) {
            return false;
        }
        return trackCreativeMode || player.getGameMode() != GameMode.CREATIVE;
    }
}
