package com.Lino.battlePass.managers;

import com.Lino.battlePass.models.EditableReward;
import com.Lino.battlePass.utils.AtomicYamlFile;
import com.Lino.battlePass.utils.ItemSerializer;
import com.Lino.battlePass.utils.RewardConfiguration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.inventory.ItemStack;
import java.io.File;
import java.io.IOException;
import java.util.ConcurrentModificationException;
import java.util.List;

/** Selected reward file and level revision stay bound to the editor that loaded them. */
final class RewardEditorStorage {
    RewardEditorManager.EditorSnapshot load(File file, int season, int level) throws IOException, InvalidConfigurationException {
        File source = file.exists() ? file.toPath().toRealPath().toFile() : file.getCanonicalFile();
        var config = AtomicYamlFile.load(source);
        return new RewardEditorManager.EditorSnapshot(source, season, AtomicYamlFile.sectionSignature(config, "level-" + level),
                RewardConfiguration.readEditableRewardsStrict(config, level));
    }

    RewardEditorManager.EditorSnapshot save(RewardEditorManager.EditorSnapshot source, int level, List<EditableReward> rewards)
            throws IOException, InvalidConfigurationException {
        var config = AtomicYamlFile.load(source.file());
        String path = "level-" + level;
        if (!source.signature().equals(AtomicYamlFile.sectionSignature(config, path))) {
            throw new ConcurrentModificationException("This reward level changed after the editor was opened; reopen it before saving");
        }
        List<String> comments = config.getComments(path);
        List<String> inlineComments = config.getInlineComments(path);
        config.set(path, null);
        if (rewards.size() == 1) writeReward(config.createSection(path), rewards.getFirst());
        else if (!rewards.isEmpty()) {
            ConfigurationSection items = config.createSection(path + ".items");
            for (int i = 0; i < rewards.size(); i++) writeReward(items.createSection("reward" + (i + 1)), rewards.get(i));
        }
        if (!rewards.isEmpty()) {
            config.setComments(path, comments);
            config.setInlineComments(path, inlineComments);
        }
        AtomicYamlFile.save(source.file(), config);
        return new RewardEditorManager.EditorSnapshot(source.file(), source.season(), AtomicYamlFile.sectionSignature(config, path), List.copyOf(rewards));
    }

    private void writeReward(ConfigurationSection section, EditableReward reward) {
        if (reward.isCommand()) {
            if (reward.getCommand() == null || reward.getCommand().isBlank()) throw new IllegalArgumentException("A reward command cannot be empty");
            section.set("command", reward.getCommand());
            section.set("display", reward.getDisplayName());
        } else {
            ItemStack item = reward.getItemStack();
            if (item == null || item.getAmount() < 1 || item.getType().isAir()) throw new IllegalArgumentException("A reward item must not be empty");
            ItemSerializer.saveItemToConfig(section, item);
            if (!section.contains("serialized-item") || !section.contains("material")) throw new IllegalStateException("Could not serialize the complete reward item");
            if (reward.getDisplayName() != null) section.set("display", reward.getDisplayName());
        }
    }
}
