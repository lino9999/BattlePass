package com.Lino.battlePass.managers;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.utils.GradientColorParser;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Owns actionbar task identities independently of mission progress and XP. */
final class MissionActionbars {
    private final BattlePass plugin;
    private final Map<UUID, Map<String, ActionbarTask>> tasks = new ConcurrentHashMap<>();

    MissionActionbars(BattlePass plugin) { this.plugin = plugin; }

    void progress(Player player, String mission, int current, int required) {
        if (!canShow() || !player.isOnline()) return;
        String message = plugin.getMessageManager().getMessage("messages.mission.actionbar-progress",
                "%mission%", mission, "%current%", String.valueOf(current), "%required%", String.valueOf(required));
        show(player, mission, message, (long) Math.max(1, plugin.getConfigManager().getActionbarProgressDuration()) * 4, 5L);
    }

    void completed(Player player, String mission) {
        if (!canShow() || !player.isOnline()) return;
        show(player, mission, plugin.getMessageManager().getMessage("messages.mission.actionbar-completed", "%mission%", mission),
                Math.max(1, plugin.getConfigManager().getActionbarCompletedDuration()), 20L);
    }

    private boolean canShow() { return plugin.isEnabled() && !plugin.isStopping(); }

    private void show(Player player, String mission, String message, long repeats, long period) {
        UUID uuid = player.getUniqueId();
        String key = mission.toLowerCase(Locale.ROOT).replace(" ", "_");
        Map<String, ActionbarTask> previous = tasks.get(uuid);
        if (previous != null && previous.get(key) != null) previous.get(key).stop(false);
        Map<String, ActionbarTask> playerTasks = tasks.computeIfAbsent(uuid, ignored -> new ConcurrentHashMap<>());
        ActionbarTask task = new ActionbarTask(player, key, message, repeats, playerTasks);
        try {
            task.runTaskTimer(plugin, 0L, period);
            playerTasks.put(key, task);
        } catch (RuntimeException failure) {
            if (playerTasks.isEmpty()) tasks.remove(uuid, playerTasks);
            throw failure;
        }
    }

    private final class ActionbarTask extends BukkitRunnable {
        private final Player player;
        private final String key;
        private final String message;
        private final long repeats;
        private final Map<String, ActionbarTask> playerTasks;
        private long count;
        private boolean stopped;

        ActionbarTask(Player player, String key, String message, long repeats, Map<String, ActionbarTask> playerTasks) {
            this.player = player; this.key = key; this.message = message; this.repeats = repeats; this.playerTasks = playerTasks;
        }

        @Override public void run() {
            if (stopped) return;
            if (playerTasks.get(key) != this || !player.isOnline() || !canShow()) { stop(false); return; }
            if (count >= repeats) { stop(true); return; }
            count++; // Sending can re-enter progress or replace this task.
            send(player, message);
        }

        void stop(boolean clear) {
            if (stopped) return;
            stopped = true;
            boolean owned = playerTasks.remove(key, this);
            if (playerTasks.isEmpty()) tasks.remove(player.getUniqueId(), playerTasks);
            cancel();
            if (owned && clear && player.isOnline()) send(player, "");
        }
    }

    void clearPlayer(UUID uuid) {
        Map<String, ActionbarTask> playerTasks = tasks.get(uuid);
        if (playerTasks != null) List.copyOf(playerTasks.values()).forEach(task -> task.stop(false));
    }

    void clearAll() {
        tasks.values().stream().flatMap(playerTasks -> playerTasks.values().stream()).toList().forEach(task -> task.stop(false));
    }

    private void send(Player player, String message) {
        try { player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message)); }
        catch (Exception failure) {
            String prefix = plugin.getMessageManager().getMessagesConfig().getString("messages.mission.actionbar-prefix", "&7[&6Progress&7]");
            player.sendMessage(GradientColorParser.parse(prefix) + " " + message);
        }
    }
}
