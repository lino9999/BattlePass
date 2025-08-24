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
        Map<String, List<MissionTemplate>> templatesByType = new HashMap<>();

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

            String missionTypeKey = type + "_" + target;

            templatesByType.computeIfAbsent(missionTypeKey, k -> new ArrayList<>());

            for (int i = 0; i < weight; i++) {
                templatesByType.get(missionTypeKey).add(new MissionTemplate(displayName, type, target,
                        minRequired, maxRequired, minXP, maxXP));
            }
        }

        List<String> allKeys = new ArrayList<>(templatesByType.keySet());
        Collections.shuffle(allKeys);

        Set<String> usedMissionTypes = new HashSet<>();
        int missionsToGenerate = configManager.getDailyMissionsCount();
        int generated = 0;

        for (String key : allKeys) {
            if (generated >= missionsToGenerate) break;

            if (!usedMissionTypes.contains(key)) {
                List<MissionTemplate> templates = templatesByType.get(key);
                if (!templates.isEmpty()) {
                    Mission mission = createMissionFromTemplate(
                            templates.get(ThreadLocalRandom.current().nextInt(templates.size()))
                    );
                    newMissions.add(mission);
                    usedMissionTypes.add(key);
                    generated++;
                }
            }
        }

        if (generated < missionsToGenerate) {
            List<MissionTemplate> allTemplates = new ArrayList<>();
            for (List<MissionTemplate> list : templatesByType.values()) {
                allTemplates.addAll(list);
            }
            Collections.shuffle(allTemplates);

            for (int i = generated; i < missionsToGenerate && i < allTemplates.size(); i++) {
                newMissions.add(createMissionFromTemplate(allTemplates.get(i)));
            }
        }

        return newMissions;
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
}