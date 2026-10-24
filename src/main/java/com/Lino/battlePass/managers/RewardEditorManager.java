package com.Lino.battlePass.managers;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.gui.LevelRewardEditGui;
import com.Lino.battlePass.models.EditableReward;
import com.Lino.battlePass.utils.GradientColorParser;
import org.bukkit.entity.Player;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RewardEditorManager {
    public record EditorSnapshot(File file, int season, String signature, List<EditableReward> rewards) {
        public EditorSnapshot { rewards = List.copyOf(rewards); }
    }

    private final BattlePass plugin;
    private final RewardEditorStorage storage = new RewardEditorStorage();
    private final Map<UUID, LevelRewardEditGui> activeEditors = new ConcurrentHashMap<>();
    private final Map<UUID, CommandInputState> commandInputStates = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> seasonEditingContext = new ConcurrentHashMap<>();

    public RewardEditorManager(BattlePass plugin) { this.plugin = plugin; }

    public EditorSnapshot loadEditorSnapshot(Player player, int level, boolean premium) {
        if (level < 1) throw new IllegalArgumentException("Reward levels must be positive");
        int season = getSeasonEditingContext(player.getUniqueId());
        SeasonRotationManager rotation = plugin.getSeasonRotationManager();
        if (season < 1 && rotation != null && rotation.isRotationEnabled()) season = rotation.getCurrentSeason();
        if (season > 0) rotation.prepareSeasonArchive(season);
        File file = season > 0 ? rotation.getSeasonRewardFile(season, premium)
                : new File(plugin.getDataFolder(), premium ? "BattlePassPREMIUM.yml" : "BattlePassFREE.yml");
        try { return storage.load(file, Math.max(0, season), level); }
        catch (Exception failure) { throw new IllegalStateException("Could not load reward editor: " + failure.getMessage(), failure); }
    }

    public void openLevelEditor(Player player, int level, boolean premium) {
        if (!canEdit(player)) return;
        try {
            LevelRewardEditGui editor = new LevelRewardEditGui(plugin, player, level, premium);
            commandInputStates.remove(player.getUniqueId());
            activeEditors.put(player.getUniqueId(), editor);
            editor.open();
        } catch (RuntimeException failure) { report(player, "open reward editor", failure); }
    }

    public LevelRewardEditGui getActiveEditor(UUID uuid) { return activeEditors.get(uuid); }
    public void removeActiveEditor(UUID uuid) { activeEditors.remove(uuid); commandInputStates.remove(uuid); }
    public void setSeasonEditingContext(UUID uuid, int season) { removeActiveEditor(uuid); seasonEditingContext.put(uuid, season); }
    public void clearSeasonEditingContext(UUID uuid) { removeActiveEditor(uuid); seasonEditingContext.remove(uuid); }
    public int getSeasonEditingContext(UUID uuid) { return seasonEditingContext.getOrDefault(uuid, -1); }
    public boolean isEditingSeason(UUID uuid) { return seasonEditingContext.containsKey(uuid); }
    public void cleanupPlayer(UUID uuid) { clearSeasonEditingContext(uuid); }

    public void startCommandInput(Player player, int level, boolean premium) {
        if (!canEdit(player)) return;
        try {
            LevelRewardEditGui editor = getActiveEditor(player.getUniqueId());
            if (editor == null || editor.getLevel() != level || editor.isPremium() != premium) {
                editor = new LevelRewardEditGui(plugin, player, level, premium);
                activeEditors.put(player.getUniqueId(), editor);
            }
            commandInputStates.put(player.getUniqueId(), new CommandInputState(editor));
            editor.invalidatePendingClose();
            player.closeInventory();
            player.sendMessage(GradientColorParser.parse(plugin.getMessageManager().getPrefix()
                    + "<gradient:#FFD700:#FF6B6B>Enter command reward format:</gradient>"));
            player.sendMessage(GradientColorParser.parse("&7Example: &e$1000 | eco give <player> 1000"));
            player.sendMessage(GradientColorParser.parse("&7Type &c'cancel' &7to cancel"));
        } catch (RuntimeException failure) { report(player, "start reward command input", failure); }
    }

    public boolean hasCommandInput(UUID uuid) { return commandInputStates.containsKey(uuid); }
    public Object getCommandInputToken(UUID uuid) { return commandInputStates.get(uuid); }

    public boolean handleCommandInput(Player player, String message) {
        return handleCommandInput(player, message, getCommandInputToken(player.getUniqueId()));
    }

    public boolean handleCommandInput(Player player, String message, Object expectedToken) {
        UUID uuid = player.getUniqueId();
        CommandInputState state = commandInputStates.get(uuid);
        if (state == null || state != expectedToken || getActiveEditor(uuid) != state.editor
                || !commandInputStates.remove(uuid, state)) return false;
        if (!player.isOnline()) return true;
        if (!player.hasPermission("battlepass.admin")) { canEdit(player); return true; }
        if (message.equalsIgnoreCase("cancel")) {
            player.sendMessage(GradientColorParser.parse(plugin.getMessageManager().getPrefix()
                    + "<gradient:#FF6B6B:#FF0000>Command input cancelled</gradient>"));
            state.editor.open();
            return true;
        }
        String[] parts = message.split("\\|", 2);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            player.sendMessage(GradientColorParser.parse(plugin.getMessageManager().getPrefix()
                    + "<gradient:#FF6B6B:#FF0000>Invalid format! Use: display | command</gradient>"));
            state.editor.open();
            return true;
        }
        state.editor.addReward(new EditableReward(parts[1].trim(), parts[0].trim()));
        player.sendMessage(GradientColorParser.parse(plugin.getMessageManager().getPrefix()
                + "<gradient:#00FF88:#45B7D1>✓ Command reward added!</gradient>"));
        var returnTo = player.getOpenInventory().getTopInventory();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && player.hasPermission("battlepass.admin") && getActiveEditor(uuid) == state.editor
                    && !hasCommandInput(uuid) && player.getOpenInventory().getTopInventory() == returnTo) state.editor.open();
        }, 20L);
        return true;
    }

    public boolean saveEditor(Player player, LevelRewardEditGui editor) {
        if (!canEdit(player) || getActiveEditor(player.getUniqueId()) != editor) return false;
        EditorSnapshot saved;
        try {
            saved = storage.save(editor.getSnapshot(), editor.getLevel(), List.copyOf(editor.getCurrentRewards()));
            editor.acceptSnapshot(saved);
        } catch (Exception failure) { report(player, "save rewards", failure); return false; }
        return applySaved(player, saved, editor.getLevel(), editor.getCurrentRewards().isEmpty());
    }

    /** Programmatic save against the currently selected source; interactive saves use their captured snapshot. */
    public void saveRewards(Player player, int level, boolean premium, List<EditableReward> rewards) {
        if (!canEdit(player)) return;
        try {
            EditorSnapshot saved = storage.save(loadEditorSnapshot(player, level, premium), level, List.copyOf(rewards));
            applySaved(player, saved, level, rewards.isEmpty());
        } catch (Exception failure) { report(player, "save rewards", failure); }
    }

    private boolean applySaved(Player player, EditorSnapshot source, int level, boolean empty) {
        try {
            if (source.season() > 0) syncSeasonIfActive(source.season());
            else plugin.reload();
            String suffix = source.season() > 0 ? " (Season " + source.season() + ")" : "";
            player.sendMessage(GradientColorParser.parse(plugin.getMessageManager().getPrefix()
                    + "<gradient:#00FF88:#45B7D1>✓ Level " + level + (empty ? " rewards cleared!" : " rewards saved!") + suffix + "</gradient>"));
            return true;
        } catch (RuntimeException failure) {
            plugin.getLogger().severe("Rewards were saved, but applying them failed: " + failure.getMessage());
            player.sendMessage(plugin.getMessageManager().getPrefix() + "§cRewards were saved, but applying them failed. Fix the error and reload.");
            return false;
        }
    }

    private void syncSeasonIfActive(int season) {
        SeasonRotationManager rotation = plugin.getSeasonRotationManager();
        if (rotation != null && rotation.isRotationEnabled() && rotation.getCurrentSeason() == season) plugin.reload();
    }

    public void saveAllRewards(Player player) {
        if (!canEdit(player)) return;
        try {
            if (isEditingSeason(player.getUniqueId())) syncSeasonIfActive(getSeasonEditingContext(player.getUniqueId()));
            else plugin.reload();
            player.sendMessage(GradientColorParser.parse(plugin.getMessageManager().getPrefix()
                    + "<gradient:#00FF88:#45B7D1>✓ All saved rewards applied!</gradient>"));
            for (Player online : plugin.getServer().getOnlinePlayers()) {
                if (!online.equals(player)) online.sendMessage(GradientColorParser.parse(plugin.getMessageManager().getPrefix()
                        + "<gradient:#FFD700:#FF6B6B>Battle Pass rewards have been updated by an admin!</gradient>"));
            }
        } catch (RuntimeException failure) { report(player, "apply rewards", failure); }
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

    private static final class CommandInputState {
        final LevelRewardEditGui editor;
        CommandInputState(LevelRewardEditGui editor) { this.editor = editor; }
    }
}
