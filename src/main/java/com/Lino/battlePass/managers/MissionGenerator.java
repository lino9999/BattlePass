package com.Lino.battlePass.managers;

import com.Lino.battlePass.models.Mission;
import com.Lino.battlePass.models.MissionTemplate;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class MissionGenerator {

    private final ConfigManager configManager;

    public MissionGenerator(ConfigManager configManager) {
        this.configManager = configManager;
    }

    public List<Mission> generateDailyMissions(String missionDate) {
        List<Mission> newMissions = new ArrayList<>();
        List<WeightedMissionTemplate> weightedTemplates = new ArrayList<>();

        // Load global mission pool
        loadPoolFromConfig(configManager.getMissionsConfig(), weightedTemplates);

        // Merge season-specific mission pool (keys prefixed with "season_" to avoid collisions)
        FileConfiguration seasonConfig = configManager.getSeasonMissionsConfig();
        if (seasonConfig != null) {
            loadPoolFromConfig(seasonConfig, weightedTemplates);
        }

        if (weightedTemplates.isEmpty()) {
            return newMissions;
        }

        int missionsToGenerate = configManager.getDailyMissionsCount();

        for (int i = 0; i < missionsToGenerate && !weightedTemplates.isEmpty(); i++) {
            WeightedMissionTemplate selected = selectWeightedRandom(weightedTemplates);

            if (selected != null) {
                Mission mission = createMissionFromTemplate(selected.template);
                newMissions.add(mission);
                weightedTemplates.removeIf(w -> w.key.equals(selected.key));
            }
        }

        return newMissions;
    }

    private void loadPoolFromConfig(FileConfiguration config, List<WeightedMissionTemplate> weightedTemplates) {
        ConfigurationSection pools = config.getConfigurationSection("mission-pools");
        if (pools == null) return;

        Set<String> existingKeys = new HashSet<>();
        for (WeightedMissionTemplate w : weightedTemplates) existingKeys.add(w.key);

        for (String key : pools.getKeys(false)) {
            if (existingKeys.contains(key)) continue; // season overrides global if same key

            ConfigurationSection missionSection = pools.getConfigurationSection(key);
            if (missionSection == null) continue;

            String type = missionSection.getString("type");
            String target = missionSection.getString("target");
            String displayName = missionSection.getString("display-name");
            int minRequired = missionSection.getInt("min-required");
            int maxRequired = missionSection.getInt("max-required");
            int minXP = missionSection.getInt("min-xp");
            int maxXP = missionSection.getInt("max-xp");
            int weight = missionSection.getInt("weight", 10);
            String world = missionSection.getString("world", null);
            String region = missionSection.getString("region", null);

            MissionTemplate template = new MissionTemplate(displayName, type, target,
                    minRequired, maxRequired, minXP, maxXP, world, region);

            weightedTemplates.add(new WeightedMissionTemplate(template, weight, key));
        }
    }

    private WeightedMissionTemplate selectWeightedRandom(List<WeightedMissionTemplate> weightedTemplates) {
        if (weightedTemplates.isEmpty()) return null;

        int totalWeight = weightedTemplates.stream().mapToInt(w -> w.weight).sum();

        if (totalWeight == 0) {
            return weightedTemplates.get(ThreadLocalRandom.current().nextInt(weightedTemplates.size()));
        }

        int randomValue = ThreadLocalRandom.current().nextInt(totalWeight);
        int currentWeight = 0;

        for (WeightedMissionTemplate weighted : weightedTemplates) {
            currentWeight += weighted.weight;
            if (randomValue < currentWeight) {
                return weighted;
            }
        }

        return weightedTemplates.get(weightedTemplates.size() - 1);
    }

    private Mission createMissionFromTemplate(MissionTemplate template) {
        int required = ThreadLocalRandom.current().nextInt(
                template.minRequired, template.maxRequired + 1);
        int xpReward = ThreadLocalRandom.current().nextInt(
                template.minXP, template.maxXP + 1);

        String name = template.nameFormat
                .replace("<amount>", String.valueOf(required))
                .replace("<target>", formatTarget(template.target));

        return new Mission(name, template.type, template.target, required, xpReward, template.world, template.region);
    }

    private String formatTarget(String target) {
        if (target.equals("ANY")) {
            return "";
        }
        return target.toLowerCase().replace("_", " ");
    }

    private static class WeightedMissionTemplate {
        final MissionTemplate template;
        final int weight;
        final String key;

        WeightedMissionTemplate(MissionTemplate template, int weight, String key) {
            this.template = template;
            this.weight = weight;
            this.key = key;
        }
    }
}