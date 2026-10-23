package com.Lino.battlePass.managers;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.gui.MissionDetailsGui;
import com.Lino.battlePass.gui.MissionEditorGui;
import com.Lino.battlePass.gui.MissionTypeSelectionGui;
import com.Lino.battlePass.utils.GradientColorParser;
import com.Lino.battlePass.utils.AtomicYamlFile;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class MissionEditorManager {

    private final BattlePass plugin;
    private final Map<UUID, EditorState> editingStates = new ConcurrentHashMap<>();

    public MissionEditorManager(BattlePass plugin) {
        this.plugin = plugin;
    }

    public void openMissionEditor(Player player, int page) {
        if (!canEdit(player)) return;
        editingStates.remove(player.getUniqueId());
        new MissionEditorGui(plugin, player, page).open();
    }

    public void openMissionTypeSelector(Player player) {
        if (canEdit(player)) new MissionTypeSelectionGui(plugin, player).open();
    }

    public void openMissionDetails(Player player, String key) {
        if (!canEdit(player)) return;
        editingStates.remove(player.getUniqueId());
        new MissionDetailsGui(plugin, player, key).open();
    }

    public void cleanupPlayer(UUID uuid) { editingStates.remove(uuid); }

    public void createMissionFromType(Player player, String type) {
        if (!canEdit(player) || type == null || type.isBlank()) return;
        File file = new File(plugin.getDataFolder(), "missions.yml");
        FileConfiguration config = readConfig(player, file);
        if (config == null) return;
        String id = generateUniqueId(type);
        for (int attempts = 0; config.contains("mission-pools." + id) && attempts < 16; attempts++) id = generateUniqueId(type);
        if (config.contains("mission-pools." + id)) {
            String base = type.toLowerCase(Locale.ROOT).replace("_", "-") + "-extra-";
            long suffix = 0;
            while (config.contains("mission-pools." + base + suffix)) suffix++;
            id = base + suffix;
        }
        String path = "mission-pools." + id;
        config.set(path + ".type", type);
        config.set(path + ".display-name", formatDisplayName(type));
        config.set(path + ".min-required", 10);
        config.set(path + ".max-required", 20);
        config.set(path + ".min-xp", 100);
        config.set(path + ".max-xp", 200);
        config.set(path + ".weight", 10);
        config.set(path + ".target", getDefaultTarget(type));
        if (saveConfig(player, file, config) != SaveResult.APPLIED) return;
        player.sendMessage(plugin.getMessageManager().getPrefix() + "§aMission created: " + id);
        openMissionDetails(player, id);
    }

    public void updateMissionType(Player player, String newType) {
        if (!canEdit(player)) return;
        EditorState state = editingStates.get(player.getUniqueId());
        if (state == null || state.type != EditType.TYPE || !editingStates.remove(player.getUniqueId(), state)) return;
        File file = new File(plugin.getDataFolder(), "missions.yml");
        FileConfiguration config = readConfig(player, file);
        if (config == null) { editingStates.putIfAbsent(player.getUniqueId(), state); return; }
        if (!matches(config, state, player)) return;
        String path = "mission-pools." + state.missionKey;
        config.set(path + ".type", newType);
        config.set(path + ".target", isTargetRequired(newType) ? getDefaultTarget(newType) : "ANY");
        SaveResult saved = saveConfig(player, file, config);
        if (saved == SaveResult.FAILED) editingStates.putIfAbsent(player.getUniqueId(), state);
        if (saved != SaveResult.APPLIED) return;
        player.sendMessage(plugin.getMessageManager().getPrefix() + "§aMission type updated to " + newType);
        openMissionDetails(player, state.missionKey);
    }

    public void deleteMission(Player player, String key) {
        if (!canEdit(player)) return;
        File file = new File(plugin.getDataFolder(), "missions.yml");
        FileConfiguration config = readConfig(player, file);
        if (config == null) return;
        config.set("mission-pools." + key, null);
        if (saveConfig(player, file, config) != SaveResult.APPLIED) return;
        player.sendMessage(plugin.getMessageManager().getPrefix() + "§aMission deleted successfully!");
        openMissionEditor(player, 1);
    }

    public void startEditingValue(Player player, String key, EditType type) {
        if (!canEdit(player)) return;
        FileConfiguration config = readConfig(player, new File(plugin.getDataFolder(), "missions.yml"));
        if (config == null || !config.isConfigurationSection("mission-pools." + key)) return;
        editingStates.put(player.getUniqueId(), new EditorState(key, type, AtomicYamlFile.sectionSignature(config, "mission-pools." + key)));
        if (type == EditType.TYPE) { openMissionTypeSelector(player); return; }
        player.closeInventory();
        player.sendMessage(GradientColorParser.parse(plugin.getMessageManager().getPrefix()
                + "<gradient:#FFD700:#FF6B6B>Editing " + type.name() + "</gradient>"));
        player.sendMessage(GradientColorParser.parse("&7Enter the new value in chat."));
        player.sendMessage(GradientColorParser.parse("&7Type &c'cancel' &7to cancel."));
    }

    public void handleChatInput(Player player, String message) {
        handleChatInput(player, message, editingStates.get(player.getUniqueId()));
    }

    public void handleChatInput(Player player, String message, EditorState expected) {
        UUID uuid = player.getUniqueId();
        if (expected == null || editingStates.get(uuid) != expected || !editingStates.remove(uuid, expected)) return;
        if (!player.isOnline() || !canEdit(player)) return;
        if (message.equalsIgnoreCase("cancel")) { openMissionDetails(player, expected.missionKey); return; }
        File file = new File(plugin.getDataFolder(), "missions.yml");
        FileConfiguration config = readConfig(player, file);
        if (config == null) { editingStates.putIfAbsent(uuid, expected); return; }
        if (!matches(config, expected, player)) return;
        String path = "mission-pools." + expected.missionKey + ".";
        try {
            switch (expected.type) {
                case DISPLAY_NAME -> config.set(path + "display-name", message);
                case TARGET -> config.set(path + "target", message.toUpperCase(Locale.ROOT));
                case MIN_REQ, MAX_REQ, MIN_XP, MAX_XP, WEIGHT -> {
                    int value = Integer.parseInt(message.trim());
                    if (value < (expected.type == EditType.WEIGHT ? 0 : 1)) throw new IllegalArgumentException("Value is below its permitted minimum");
                    String field = switch (expected.type) {
                        case MIN_REQ -> "min-required"; case MAX_REQ -> "max-required";
                        case MIN_XP -> "min-xp"; case MAX_XP -> "max-xp"; default -> "weight";
                    };
                    config.set(path + field, value);
                }
                default -> { editingStates.putIfAbsent(uuid, expected); return; }
            }
            SaveResult result = saveConfig(player, file, config);
            if (result == SaveResult.FAILED) editingStates.putIfAbsent(uuid, expected);
            if (result != SaveResult.APPLIED) return;
            player.sendMessage(plugin.getMessageManager().getPrefix() + "§aValue updated!");
            var returnTo = player.getOpenInventory().getTopInventory();
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (player.isOnline() && player.hasPermission("battlepass.admin") && editingStates.get(uuid) == null
                        && player.getOpenInventory().getTopInventory() == returnTo) openMissionDetails(player, expected.missionKey);
            });
        } catch (IllegalArgumentException failure) {
            player.sendMessage(plugin.getMessageManager().getPrefix() + "§cInvalid value: " + failure.getMessage());
            editingStates.putIfAbsent(uuid, expected);
        }
    }

    private boolean matches(FileConfiguration config, EditorState state, Player player) {
        String path = "mission-pools." + state.missionKey;
        if (!config.isConfigurationSection(path) || (state.signature != null
                && !state.signature.equals(AtomicYamlFile.sectionSignature(config, path)))) {
            player.sendMessage(plugin.getMessageManager().getPrefix() + "§cThis mission changed or was removed. Reopen it before editing.");
            return false;
        }
        return true;
    }

    private FileConfiguration readConfig(Player player, File file) {
        try { return AtomicYamlFile.load(file); }
        catch (Exception failure) { report(player, "read missions", failure); return null; }
    }

    private enum SaveResult { FAILED, SAVED, APPLIED }

    private SaveResult saveConfig(Player player, File file, FileConfiguration config) {
        try { AtomicYamlFile.save(file, config); }
        catch (Exception failure) { report(player, "save missions", failure); return SaveResult.FAILED; }
        try { plugin.getConfigManager().reload(); return SaveResult.APPLIED; }
        catch (RuntimeException failure) {
            report(player, "apply saved missions (the file was saved)", failure);
            return SaveResult.SAVED;
        }
    }

    private boolean canEdit(Player player) {
        if (player.hasPermission("battlepass.admin")) return true;
        player.sendMessage(plugin.getMessageManager().getPrefix() + plugin.getMessageManager().getMessage("messages.no-permission"));
        return false;
    }

    private void report(Player player, String operation, Exception failure) {
        plugin.getLogger().severe("Could not " + operation + ": " + failure.getMessage());
        player.sendMessage(plugin.getMessageManager().getPrefix() + "§cCould not " + operation + ": " + failure.getMessage());
    }

    // --- Helpers ---

    private String generateUniqueId(String type) {
        String base = type.toLowerCase(Locale.ROOT).replace("_", "-");
        int random = ThreadLocalRandom.current().nextInt(1000, 9999);
        return base + "-" + random;
    }

    private String formatDisplayName(String type) {
        String readable = type.toLowerCase(Locale.ROOT).replace("_", " ");
        return Character.toUpperCase(readable.charAt(0)) + readable.substring(1) + " <amount>";
    }

    private String getDefaultTarget(String type) {
        switch (type) {
            case "KILL_MOB": return "ZOMBIE";
            case "MINE_BLOCK": case "BREAK_BLOCK": case "PLACE_BLOCK": return "STONE";
            case "CRAFT_ITEM": case "EAT_ITEM": return "BREAD";
            case "FISH_ITEM": return "COD";
            case "BREED_ANIMAL": case "TAME_ANIMAL": case "SHEAR_SHEEP": return "SHEEP";
            default: return "ANY";
        }
    }

    public boolean isTargetRequired(String type) {
        return switch (type) {
            case "PLAY_TIME", "GAIN_XP" -> false;
            default -> true;
        };
    }

    public boolean isEditing(UUID uuid) {
        return editingStates.containsKey(uuid);
    }

    public EditorState getEditingState(UUID uuid) {
        return editingStates.get(uuid);
    }

    public enum EditType {
        DISPLAY_NAME, TYPE, TARGET, MIN_REQ, MAX_REQ, MIN_XP, MAX_XP, WEIGHT
    }

    public static class EditorState {
        public final String missionKey;
        public final EditType type;

        final String signature;
        public EditorState(String missionKey, EditType type) { this(missionKey, type, null); }
        EditorState(String missionKey, EditType type, String signature) {
            this.missionKey = missionKey;
            this.type = type;
            this.signature = signature;
        }
    }
}
