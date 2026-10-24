package com.Lino.battlePass.managers;

import com.Lino.battlePass.BattlePass;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Cached GUI materials/model data, exposed through ConfigManager's existing API. */
final class GuiConfiguration {
    private final BattlePass plugin;
    private FileConfiguration config;

    private Material guiFreeLockedMaterial = Material.GRAY_STAINED_GLASS;
    private Material guiPremiumLockedMaterial = Material.GRAY_STAINED_GLASS;
    private Material guiPremiumNoPassMaterial = Material.IRON_BARS;
    private Material guiRewardAvailableMaterial = Material.CHEST;
    private Material guiSeparatorMaterial = Material.GRAY_STAINED_GLASS_PANE;
    private Material guiFreeClaimedMaterial = Material.GREEN_STAINED_GLASS;
    private Material guiPremiumClaimedMaterial = Material.LIME_STAINED_GLASS;
    private boolean hideFreeClaimedRewards = false;
    private boolean hidePremiumClaimedRewards = false;
    private final Map<String, Integer> guiCustomModelData = new HashMap<>();
    private int guiNavigationCustomModelData = 0;

    GuiConfiguration(BattlePass plugin) {
        this.plugin = plugin;
    }

    void reload(FileConfiguration config) {
        this.config = config;
        guiCustomModelData.clear();

        guiFreeLockedMaterial = parseGuiMaterial("gui.reward-locked.free", Material.GRAY_STAINED_GLASS);
        guiPremiumLockedMaterial = parseGuiMaterial("gui.reward-locked.premium", Material.GRAY_STAINED_GLASS);
        guiPremiumNoPassMaterial = parseGuiMaterial("gui.premium-no-pass", Material.IRON_BARS);
        guiRewardAvailableMaterial = parseGuiMaterial("gui.reward-available", Material.CHEST);
        guiSeparatorMaterial = parseGuiMaterial("gui.separator", Material.GRAY_STAINED_GLASS_PANE);

        if (isGuiEntryNone("gui.reward-claimed.free")) {
            hideFreeClaimedRewards = true;
            guiFreeClaimedMaterial = null;
        } else {
            hideFreeClaimedRewards = false;
            guiFreeClaimedMaterial = parseGuiMaterial("gui.reward-claimed.free", Material.GREEN_STAINED_GLASS);
        }

        if (isGuiEntryNone("gui.reward-claimed.premium")) {
            hidePremiumClaimedRewards = true;
            guiPremiumClaimedMaterial = null;
        } else {
            hidePremiumClaimedRewards = false;
            guiPremiumClaimedMaterial = parseGuiMaterial("gui.reward-claimed.premium", Material.LIME_STAINED_GLASS);
        }

        guiNavigationCustomModelData = Math.max(0, config.getInt("gui.navigation.custom-model-data", 0));
    }

    private Material parseMaterial(String materialName, Material defaultMaterial) {
        if (materialName == null || materialName.isEmpty()) {
            return defaultMaterial;
        }

        try {
            return Material.valueOf(materialName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid material '" + materialName + "' in config. Using default: " + defaultMaterial.name());
            return defaultMaterial;
        }
    }

    private boolean isGuiEntryNone(String path) {
        Object raw = config.get(path);
        String materialName;
        if (raw instanceof ConfigurationSection section) {
            materialName = section.getString("material");
        } else {
            materialName = config.getString(path);
        }
        return materialName != null && materialName.equalsIgnoreCase("NONE");
    }

    private Material parseGuiMaterial(String path, Material defaultMaterial) {
        Object raw = config.get(path);
        String materialName = null;
        int customModelData = 0;

        if (raw instanceof ConfigurationSection section) {
            materialName = section.getString("material");
            customModelData = section.getInt("custom-model-data", 0);
        } else if (raw instanceof String) {
            materialName = (String) raw;
        }

        if (customModelData > 0) {
            guiCustomModelData.put(path, customModelData);
        } else {
            guiCustomModelData.remove(path);
        }

        return parseMaterial(materialName, defaultMaterial);
    }

    Integer getGuiCustomModelData(String path) {
        return guiCustomModelData.get(path);
    }

    int getGuiNavigationCustomModelData() {
        return guiNavigationCustomModelData;
    }

    Material getGuiFreeLockedMaterial() {
        return guiFreeLockedMaterial;
    }

    Material getGuiPremiumLockedMaterial() {
        return guiPremiumLockedMaterial;
    }

    Material getGuiPremiumNoPassMaterial() {
        return guiPremiumNoPassMaterial;
    }

    Material getGuiRewardAvailableMaterial() {
        return guiRewardAvailableMaterial;
    }

    Material getGuiSeparatorMaterial() {
        return guiSeparatorMaterial;
    }

    Material getGuiFreeClaimedMaterial() {
        return guiFreeClaimedMaterial;
    }

    Material getGuiPremiumClaimedMaterial() {
        return guiPremiumClaimedMaterial;
    }

    boolean shouldHideFreeClaimedRewards() {
        return hideFreeClaimedRewards;
    }

    boolean shouldHidePremiumClaimedRewards() {
        return hidePremiumClaimedRewards;
    }
}
