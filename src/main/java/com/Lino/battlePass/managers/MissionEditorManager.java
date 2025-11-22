package com.Lino.battlePass.managers;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.gui.MissionDetailsGui;
import com.Lino.battlePass.gui.MissionEditorGui;
import com.Lino.battlePass.gui.MissionTypeSelectionGui;
import com.Lino.battlePass.utils.GradientColorParser;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
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
        editingStates.remove(player.getUniqueId()); // Pulisce stati precedenti
        new MissionEditorGui(plugin, player, page).open();
    }

    public void openMissionTypeSelector(Player player) {
        new MissionTypeSelectionGui(plugin, player).open();
    }

    public void openMissionDetails(Player player, String missionKey) {
        editingStates.remove(player.getUniqueId()); // Pulisce stati precedenti
        new MissionDetailsGui(plugin, player, missionKey).open();
    }

    public void createMissionFromType(Player player, String type) {
        String uniqueId = generateUniqueId(type);

        File file = new File(plugin.getDataFolder(), "missions.yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        String path = "mission-pools." + uniqueId;

        config.set(path + ".type", type);
        config.set(path + ".display-name", formatDisplayName(type));
        config.set(path + ".min-required", 10);
        config.set(path + ".max-required", 20);
        config.set(path + ".min-xp", 100);
        config.set(path + ".max-xp", 200);
        config.set(path + ".weight", 10);
        config.set(path + ".target", getDefaultTarget(type));

        saveConfig(file, config);

        player.sendMessage(plugin.getMessageManager().getPrefix() + "§aMission created: " + uniqueId);
        openMissionDetails(player, uniqueId);
    }

    public void updateMissionType(Player player, String newType) {
        EditorState state = editingStates.remove(player.getUniqueId());
        if (state == null || state.type != EditType.TYPE) return;

        File file = new File(plugin.getDataFolder(), "missions.yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        String path = "mission-pools." + state.missionKey;

        config.set(path + ".type", newType);

        // Se il nuovo tipo non richiede target, lo impostiamo su ANY automaticamente
        if (!isTargetRequired(newType)) {
            config.set(path + ".target", "ANY");
        } else {
            // Imposta un target di default valido per evitare configurazioni rotte
            config.set(path + ".target", getDefaultTarget(newType));
        }

        saveConfig(file, config);
        player.sendMessage(plugin.getMessageManager().getPrefix() + "§aMission type updated to " + newType);
        openMissionDetails(player, state.missionKey);
    }

    public void deleteMission(Player player, String missionKey) {
        File file = new File(plugin.getDataFolder(), "missions.yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);

        config.set("mission-pools." + missionKey, null);
        saveConfig(file, config);

        player.sendMessage(plugin.getMessageManager().getPrefix() + "§aMission deleted successfully!");
        openMissionEditor(player, 1);
    }

    public void startEditingValue(Player player, String missionKey, EditType type) {
        editingStates.put(player.getUniqueId(), new EditorState(missionKey, type));

        if (type == EditType.TYPE) {
            openMissionTypeSelector(player);
            return;
        }

        player.closeInventory();
        player.sendMessage(GradientColorParser.parse(plugin.getMessageManager().getPrefix() +
                "<gradient:#FFD700:#FF6B6B>Editing " + type.name() + "</gradient>"));
        player.sendMessage(GradientColorParser.parse("&7Enter the new value in chat."));
        player.sendMessage(GradientColorParser.parse("&7Type &c'cancel' &7to cancel."));
    }

    public void handleChatInput(Player player, String message) {
        EditorState state = editingStates.remove(player.getUniqueId());
        if (state == null) return;

        if (message.equalsIgnoreCase("cancel")) {
            openMissionDetails(player, state.missionKey);
            return;
        }

        File file = new File(plugin.getDataFolder(), "missions.yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        String path = "mission-pools." + state.missionKey + ".";

        try {
            switch (state.type) {
                case DISPLAY_NAME:
                    config.set(path + "display-name", message);
                    break;
                case TARGET:
                    config.set(path + "target", message.toUpperCase());
                    break;
                case MIN_REQ:
                    config.set(path + "min-required", Integer.parseInt(message));
                    break;
                case MAX_REQ:
                    config.set(path + "max-required", Integer.parseInt(message));
                    break;
                case MIN_XP:
                    config.set(path + "min-xp", Integer.parseInt(message));
                    break;
                case MAX_XP:
                    config.set(path + "max-xp", Integer.parseInt(message));
                    break;
                case WEIGHT:
                    config.set(path + "weight", Integer.parseInt(message));
                    break;
                default:
                    break;
            }
            saveConfig(file, config);
            player.sendMessage(plugin.getMessageManager().getPrefix() + "§aValue updated!");

            new BukkitRunnable() {
                @Override
                public void run() {
                    openMissionDetails(player, state.missionKey);
                }
            }.runTask(plugin);

        } catch (NumberFormatException e) {
            player.sendMessage(plugin.getMessageManager().getPrefix() + "§cInvalid number format!");
            editingStates.put(player.getUniqueId(), state); // Restore state
        } catch (Exception e) {
            player.sendMessage(plugin.getMessageManager().getPrefix() + "§cError updating value!");
            e.printStackTrace();
        }
    }

    // --- Helpers ---

    private void saveConfig(File file, FileConfiguration config) {
        try {
            config.save(file);
            plugin.getConfigManager().reload();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String generateUniqueId(String type) {
        String base = type.toLowerCase().replace("_", "-");
        int random = ThreadLocalRandom.current().nextInt(1000, 9999);
        return base + "-" + random;
    }

    private String formatDisplayName(String type) {
        String readable = type.toLowerCase().replace("_", " ");
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
        switch (type) {
            case "WALK_DISTANCE":
            case "PLAY_TIME":
            case "GAIN_XP":
            case "DAMAGE_DEALT":
            case "DAMAGE_TAKEN":
            case "DEATH":
            case "TRADE_VILLAGER":
            case "ENCHANT_ITEM":
                return false;
            default:
                return true;
        }
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

        public EditorState(String missionKey, EditType type) {
            this.missionKey = missionKey;
            this.type = type;
        }
    }
}