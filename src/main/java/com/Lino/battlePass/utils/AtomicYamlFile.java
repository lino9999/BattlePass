package com.Lino.battlePass.utils;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Strict reads and staged replacement: invalid YAML or failed writes never become empty successful saves. */
public final class AtomicYamlFile {
    private AtomicYamlFile() {}

    public static YamlConfiguration load(File file) throws IOException, InvalidConfigurationException {
        YamlConfiguration config = new YamlConfiguration();
        config.options().parseComments(true);
        if (file.exists()) config.load(file);
        return config;
    }

    public static void save(File file, FileConfiguration config) throws IOException {
        AtomicFile.write(file.toPath(), config.saveToString().getBytes(StandardCharsets.UTF_8));
    }

    public static String sectionSignature(FileConfiguration config, String path) {
        YamlConfiguration snapshot = new YamlConfiguration();
        snapshot.set("value", config.get(path));
        return snapshot.saveToString();
    }
}
