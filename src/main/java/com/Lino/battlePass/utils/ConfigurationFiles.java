package com.Lino.battlePass.utils;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.List;

/** Strict reads for managed YAML; missing optional files retain AtomicYamlFile's empty/default contract. */
public final class ConfigurationFiles {
    private static final List<String> MANAGED = List.of("config.yml", "missions.yml", "messages.yml", "BattlePassFREE.yml", "BattlePassPREMIUM.yml", "shop.yml");
    private ConfigurationFiles() {}

    public static YamlConfiguration read(File file) {
        try { return AtomicYamlFile.load(file); }
        catch (IOException | InvalidConfigurationException failure) {
            throw new IllegalStateException("Could not read " + file.getName() + ": " + failure.getMessage(), failure);
        }
    }

    public static void validateManagedFiles(File folder) {
        for (String name : MANAGED) read(new File(folder, name));
    }
}
