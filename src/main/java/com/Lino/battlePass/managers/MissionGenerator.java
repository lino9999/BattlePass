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
        ConfigurationSection pools = configManager.getMissionsConfig().getConfigurationSection("mission-pools");
        if (pools == null) {
            return new ArrayList<>();
        }

        List<Mission> newMissions = new ArrayList<>();
        List<WeightedMissionTemplate> weightedTemplates = new ArrayList<>();
        Map<String, MissionTemplate> uniqueTemplates = new HashMap<>();

        for (String key : pools.getKeys(false)) {
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

            MissionTemplate template = new MissionTemplate(displayName, type, target,
                    minRequired, maxRequired, minXP, maxXP);

            uniqueTemplates.put(key, template);
            weightedTemplates.add(new WeightedMissionTemplate(template, weight, key));
        }

        int missionsToGenerate = configManager.getDailyMissionsCount();
        Set<String> usedMissionKeys = new HashSet<>();

        for (int i = 0; i < missionsToGenerate && !weightedTemplates.isEmpty(); i++) {
            WeightedMissionTemplate selected = selectWeightedRandom(weightedTemplates);

            if (selected != null) {
                Mission mission = createMissionFromTemplate(selected.template);
                newMissions.add(mission);
                usedMissionKeys.add(selected.key);

                weightedTemplates.removeIf(w -> w.key.equals(selected.key));
            }
        }

        return newMissions;
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

        return new Mission(name, template.type, template.target, required, xpReward);
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