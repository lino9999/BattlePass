package com.Lino.battlePass.managers;

import com.Lino.battlePass.BattlePass;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class ConfigValidator {

    private static final List<String> KNOWN_SOUND_KEYS = Arrays.asList(
            "ENTITY_VILLAGER_NO",
            "UI_TOAST_CHALLENGE_COMPLETE",
            "ENTITY_EXPERIENCE_ORB_PICKUP",
            "ENTITY_PLAYER_LEVELUP",
            "ENTITY_ENDER_DRAGON_DEATH",
            "ENTITY_ITEM_PICKUP",
            "BLOCK_BEACON_AMBIENT",
            "BLOCK_NOTE_BLOCK_CHIME",
            "BLOCK_END_PORTAL_FRAME_FILL",
            "BLOCK_NOTE_BLOCK_BELL",
            "ENTITY_SQUID_AMBIENT",
            "BLOCK_ENCHANTMENT_TABLE_USE",
            "BLOCK_RESPAWN_ANCHOR_CHARGE"
    );

    private final BattlePass plugin;

    public ConfigValidator(BattlePass plugin) {
        this.plugin = plugin;
    }

    public ValidationReport validateAll() {
        ValidationReport report = new ValidationReport();

        validateGuiMaterials(plugin.getConfigManager().getConfig(), report);
        validateRewardConfig("BattlePassFREE.yml", plugin.getConfigManager().getBattlePassFreeConfig(), report);
        validateRewardConfig("BattlePassPREMIUM.yml", plugin.getConfigManager().getBattlePassPremiumConfig(), report);
        validateShopConfig(report);
        validateKnownSounds(report);

        plugin.getLogger().info("[ConfigValidator] " + report.summary());
        return report;
    }

    private void validateGuiMaterials(FileConfiguration config, ValidationReport report) {
        validateMaterialValue(config, "gui.reward-locked.free", report);
        validateMaterialValue(config, "gui.reward-locked.premium", report);
        validateMaterialValue(config, "gui.premium-no-pass", report);
        validateMaterialValue(config, "gui.reward-available", report);
        validateMaterialValue(config, "gui.separator", report);

        String freeClaimed = config.getString("gui.reward-claimed.free");
        if (freeClaimed != null && !"NONE".equalsIgnoreCase(freeClaimed)) {
            validateMaterialValue(config, "gui.reward-claimed.free", report);
        }

        String premiumClaimed = config.getString("gui.reward-claimed.premium");
        if (premiumClaimed != null && !"NONE".equalsIgnoreCase(premiumClaimed)) {
            validateMaterialValue(config, "gui.reward-claimed.premium", report);
        }
    }

    private void validateRewardConfig(String fileName, FileConfiguration config, ValidationReport report) {
        if (config == null) {
            report.warning(plugin, "[ConfigValidator] " + fileName + " is not loaded.");
            return;
        }

        for (String key : config.getKeys(false)) {
            if (!key.startsWith("level-")) {
                continue;
            }

            ConfigurationSection levelSection = config.getConfigurationSection(key);
            if (levelSection == null) {
                continue;
            }

            validateRewardSection(fileName, key, levelSection, report);
        }
    }

    private void validateRewardSection(String fileName, String basePath, ConfigurationSection section, ValidationReport report) {
        if (section.contains("material")) {
            String material = section.getString("material");
            if (!isValidMaterial(material)) {
                    report.error(plugin, "[ConfigValidator] " + fileName + " -> " + basePath + ".material: invalid material '" + material + "'");
            }
        }

        if (section.contains("command")) {
            String command = section.getString("command", "").trim();
            if (command.isEmpty()) {
                report.warning(plugin, "[ConfigValidator] " + fileName + " -> " + basePath + ".command is empty.");
            }
        }

        ConfigurationSection items = section.getConfigurationSection("items");
        if (items == null) {
            return;
        }

        for (String itemKey : items.getKeys(false)) {
            ConfigurationSection item = items.getConfigurationSection(itemKey);
            if (item == null) {
                continue;
            }

            String path = basePath + ".items." + itemKey;

            if (item.contains("material")) {
                String material = item.getString("material");
                if (!isValidMaterial(material)) {
                    report.error(plugin, "[ConfigValidator] " + fileName + " -> " + path + ".material: invalid material '" + material + "'");
                }
            }

            if (item.contains("command")) {
                String command = item.getString("command", "").trim();
                if (command.isEmpty()) {
                    report.warning(plugin, "[ConfigValidator] " + fileName + " -> " + path + ".command is empty.");
                }
            }
        }
    }

    private void validateShopConfig(ValidationReport report) {
        File shopFile = new File(plugin.getDataFolder(), "shop.yml");
        if (!shopFile.exists()) {
            report.warning(plugin, "[ConfigValidator] shop.yml is missing.");
            return;
        }

        FileConfiguration shopConfig = YamlConfiguration.loadConfiguration(shopFile);
        ConfigurationSection items = shopConfig.getConfigurationSection("shop-items");
        if (items == null) {
            report.warning(plugin, "[ConfigValidator] shop.yml has no 'shop-items' section.");
            return;
        }

        for (String itemKey : items.getKeys(false)) {
            ConfigurationSection item = items.getConfigurationSection(itemKey);
            if (item == null) {
                continue;
            }

            String material = item.getString("material");
            if (!isValidMaterial(material)) {
                report.error(plugin, "[ConfigValidator] shop.yml -> shop-items." + itemKey + ".material: invalid material '" + material + "'");
            }

            List<String> commands = item.getStringList("commands");
            if (commands.isEmpty()) {
                report.warning(plugin, "[ConfigValidator] shop.yml -> shop-items." + itemKey + ".commands is empty.");
            } else {
                for (int i = 0; i < commands.size(); i++) {
                    if (commands.get(i) == null || commands.get(i).trim().isEmpty()) {
                        report.warning(plugin, "[ConfigValidator] shop.yml -> shop-items." + itemKey + ".commands[" + i + "] is empty.");
                    }
                }
            }

            if (item.contains("items")) {
                List<?> rewards = item.getList("items");
                if (rewards != null) {
                    for (int i = 0; i < rewards.size(); i++) {
                        Object raw = rewards.get(i);
                        if (!(raw instanceof Map)) {
                            continue;
                        }

                        Map<?, ?> rewardItem = (Map<?, ?>) raw;
                        Object rewardMaterialValue = rewardItem.get("material");
                        String rewardMaterial = rewardMaterialValue == null ? null : rewardMaterialValue.toString();
                        if (!isValidMaterial(rewardMaterial)) {
                            report.error(plugin, "[ConfigValidator] shop.yml -> shop-items." + itemKey + ".items[" + i + "].material: invalid material '" + rewardMaterial + "'");
                        }
                    }
                }
            }
        }
    }

    private void validateKnownSounds(ValidationReport report) {
        for (String soundKey : KNOWN_SOUND_KEYS) {
            try {
                Sound.valueOf(soundKey);
            } catch (IllegalArgumentException ex) {
                report.error(plugin, "[ConfigValidator] Sound constant is unavailable on this API version: " + soundKey);
            }
        }
    }

    private void validateMaterialValue(FileConfiguration config, String path, ValidationReport report) {
        String material = config.getString(path);
        if (!isValidMaterial(material)) {
            report.error(plugin, "[ConfigValidator] config.yml -> " + path + ": invalid material '" + material + "'");
        }
    }

    private boolean isValidMaterial(String name) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }
        try {
            Material.valueOf(name.trim().toUpperCase());
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    public static class ValidationReport {
        private int errors;
        private int warnings;

        private void error(BattlePass plugin, String message) {
            errors++;
            plugin.getLogger().severe(message);
        }

        private void warning(BattlePass plugin, String message) {
            warnings++;
            plugin.getLogger().warning(message);
        }

        public String summary() {
            return "validation finished: " + errors + " error(s), " + warnings + " warning(s).";
        }
    }
}
