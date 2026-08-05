package com.Lino.battlePass.managers;

import com.Lino.battlePass.BattlePass;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class SeasonRotationManager {

    private final BattlePass plugin;
    private File rotationFile;
    private YamlConfiguration rotationConfig;
    private int currentSeason;
    private int totalSeasons;
    private boolean rotationEnabled;

    public SeasonRotationManager(BattlePass plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        rotationEnabled = plugin.getConfig().getBoolean("season.rotation.enabled", false);
        totalSeasons = plugin.getConfig().getInt("season.rotation.total-seasons", 3);

        rotationFile = new File(plugin.getDataFolder(), "season_rotation.yml");
        if (!rotationFile.exists()) {
            try {
                rotationFile.createNewFile();
                rotationConfig = YamlConfiguration.loadConfiguration(rotationFile);
                rotationConfig.set("current-season", 1);
                rotationConfig.save(rotationFile);
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to create season_rotation.yml: " + e.getMessage());
            }
        }
        rotationConfig = YamlConfiguration.loadConfiguration(rotationFile);
        currentSeason = rotationConfig.getInt("current-season", 1);

        if (rotationEnabled) {
            reapplyCurrentSeason();
        }
    }

    public void reapplyCurrentSeason() {
        if (!rotationEnabled) return;

        File seasonFolder = new File(plugin.getDataFolder(), "seasons/season-" + currentSeason);
        if (!seasonFolder.exists()) {
            plugin.getLogger().warning("Season folder not found: " + seasonFolder.getPath());
            return;
        }

        applySeasonRewards(currentSeason);
    }

    public void createDefaultSeasonFolders() {
        if (!rotationEnabled) return;

        File seasonsDir = new File(plugin.getDataFolder(), "seasons");
        if (!seasonsDir.exists()) {
            seasonsDir.mkdirs();
        }

        for (int i = 1; i <= totalSeasons; i++) {
            File seasonFolder = new File(seasonsDir, "season-" + i);
            if (!seasonFolder.exists()) {
                seasonFolder.mkdirs();

                File sourceFree = new File(plugin.getDataFolder(), "BattlePassFREE.yml");
                File sourcePremium = new File(plugin.getDataFolder(), "BattlePassPREMIUM.yml");
                File destFree = new File(seasonFolder, "BattlePassFREE.yml");
                File destPremium = new File(seasonFolder, "BattlePassPREMIUM.yml");

                try {
                    if (sourceFree.exists()) Files.copy(sourceFree.toPath(), destFree.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    if (sourcePremium.exists()) Files.copy(sourcePremium.toPath(), destPremium.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    plugin.getLogger().info("Created default season folder: season-" + i);
                } catch (IOException e) {
                    plugin.getLogger().severe("Failed to create season-" + i + " folder: " + e.getMessage());
                }
            }
        }
    }

    public void rotateToNextSeason() {
        if (!rotationEnabled) return;

        currentSeason++;
        if (currentSeason > totalSeasons) {
            currentSeason = 1;
        }

        applySeasonRewards(currentSeason);
        saveRotationData();
        plugin.getLogger().info("Season rotated to: " + currentSeason);
    }

    public void applySeasonRewards(int seasonNumber) {
        File seasonFolder = new File(plugin.getDataFolder(), "seasons/season-" + seasonNumber);
        if (!seasonFolder.exists()) {
            plugin.getLogger().warning("Season folder not found: season-" + seasonNumber);
            return;
        }

        File seasonFree = new File(seasonFolder, "BattlePassFREE.yml");
        File seasonPremium = new File(seasonFolder, "BattlePassPREMIUM.yml");
        File mainFree = new File(plugin.getDataFolder(), "BattlePassFREE.yml");
        File mainPremium = new File(plugin.getDataFolder(), "BattlePassPREMIUM.yml");

        try {
            if (seasonFree.exists()) Files.copy(seasonFree.toPath(), mainFree.toPath(), StandardCopyOption.REPLACE_EXISTING);
            if (seasonPremium.exists()) Files.copy(seasonPremium.toPath(), mainPremium.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to apply season " + seasonNumber + " rewards: " + e.getMessage());
        }
    }

    public File getSeasonRewardFile(int seasonNumber, boolean isPremium) {
        String fileName = isPremium ? "BattlePassPREMIUM.yml" : "BattlePassFREE.yml";
        return new File(plugin.getDataFolder(), "seasons/season-" + seasonNumber + "/" + fileName);
    }

    public boolean isValidSeason(int seasonNumber) {
        return seasonNumber >= 1 && seasonNumber <= totalSeasons;
    }

    private void saveRotationData() {
        rotationConfig.set("current-season", currentSeason);
        try {
            rotationConfig.save(rotationFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save season rotation data: " + e.getMessage());
        }
    }

    public int getCurrentSeason() {
        return currentSeason;
    }

    public int getTotalSeasons() {
        return totalSeasons;
    }

    public boolean isRotationEnabled() {
        return rotationEnabled;
    }
}