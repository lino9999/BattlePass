package com.Lino.battlePass.managers;

import com.Lino.battlePass.models.Mission;
import com.Lino.battlePass.models.MissionTemplate;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class MissionGenerator {

    private final ConfigManager configManager;

    public MissionGenerator(ConfigManager configManager) {
        this.configManager = configManager;
    }

    public List<Mission> generateDailyMissions() {
        ConfigurationSection pools = configManager.getMissionsConfig().getConfigurationSection("mission-pools");
        if (pools == null) {
            return new ArrayList<>();
        }

        List<Mission> newMissions = new ArrayList<>();
        List<WeightedMissionTemplate> weightedTemplates = new ArrayList<>();

        for (String key : pools.getKeys(false)) {
            ConfigurationSection missionSection = pools.getConfigurationSection(key);
            if (missionSection == null) continue;

            String type = missionSection.getString("type");
            if (type == null || type.isEmpty()) continue;

            String target = missionSection.getString("target", "ANY");
            List<String> additionalTargets = missionSection.getStringList("additional-targets");
            String displayName = missionSection.getString("display-name", type.replace("_", " ") + " <amount>");
            int minRequired = missionSection.getInt("min-required", 1);
            int maxRequired = missionSection.getInt("max-required", minRequired);
            int minXP = missionSection.getInt("min-xp", 100);
            int maxXP = missionSection.getInt("max-xp", minXP);
            int weight = Math.max(0, missionSection.getInt("weight", 10));

            MissionTemplate template = new MissionTemplate(displayName, type, target, additionalTargets,
                    minRequired, maxRequired, minXP, maxXP);
            weightedTemplates.add(new WeightedMissionTemplate(template, weight, key));
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

    private WeightedMissionTemplate selectWeightedRandom(List<WeightedMissionTemplate> weightedTemplates) {
        if (weightedTemplates.isEmpty()) return null;

        int totalWeight = weightedTemplates.stream().mapToInt(w -> w.weight).sum();

        if (totalWeight <= 0) {
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
        int required = randomInRange(Math.max(1, template.minRequired), Math.max(1, template.maxRequired));
        int xpReward = randomInRange(Math.max(1, template.minXP), Math.max(1, template.maxXP));

        String name = template.nameFormat
                .replace("<amount>", String.valueOf(required))
                .replace("<target>", formatTarget(template.target));

        return new Mission(name, template.type, template.target, template.additionalTargets, required, xpReward);
    }

    private int randomInRange(int min, int max) {
        int low = Math.min(min, max);
        int high = Math.max(min, max);
        return ThreadLocalRandom.current().nextInt(low, high + 1);
    }

    private String formatTarget(String target) {
        if (target == null || target.equals("ANY")) {
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