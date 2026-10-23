package com.Lino.battlePass.utils;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

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
        byte[] bytes = config.saveToString().getBytes(StandardCharsets.UTF_8);
        Path target = file.toPath().toAbsolutePath().normalize();
        if (Files.exists(target)) {
            if (!Files.isRegularFile(target)) throw new IOException("Configuration target is not a file: " + target);
            target = target.toRealPath();
        }
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), target.getFileName() + ".tmp-", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unavailable) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public static String sectionSignature(FileConfiguration config, String path) {
        YamlConfiguration snapshot = new YamlConfiguration();
        snapshot.set("value", config.get(path));
        return snapshot.saveToString();
    }
}
