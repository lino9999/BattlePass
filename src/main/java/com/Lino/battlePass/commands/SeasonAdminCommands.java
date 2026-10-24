package com.Lino.battlePass.commands;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.managers.MissionManager;
import com.Lino.battlePass.models.ResetResult;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.concurrent.CompletableFuture;

/** Reports reset completion only after the coordinator applies the committed state. */
final class SeasonAdminCommands {
    private final BattlePass plugin;
    private boolean seasonWarningPending;
    private volatile long reloadRevision;

    SeasonAdminCommands(BattlePass plugin) { this.plugin = plugin; }

    void reloadPlugin(CommandSender sender) {
        if (!canStart(sender)) return;
        long revision = ++reloadRevision;
        MissionManager manager = plugin.getMissionManager();
        try { plugin.reload(); }
        catch (RuntimeException failure) { report(sender, "reload configuration", "messages.reload-failed", failure); return; }
        send(sender, "messages.config-reloaded");
        try {
            plugin.getDatabaseManager().loadCoinsDistributionTime().whenComplete((deadline, failure) ->
                onMain(manager, () -> {
                    if (reloadRevision != revision) return;
                    if (failure != null) report(sender, "load the coin deadline", "messages.reload-coins-failed", failure);
                    else {
                        try { plugin.startCoinsDistributionTask(deadline); }
                        catch (RuntimeException applyFailure) { report(sender, "restore the coin timer", "messages.reload-coins-apply-failed", applyFailure); }
                    }
                }));
        } catch (RuntimeException failure) { report(sender, "load the coin deadline", "messages.reload-coins-failed", failure); }
    }

    boolean handleResetSeason(CommandSender sender) {
        if (!canStart(sender)) return true;
        if (seasonWarningPending) { send(sender, "messages.reset-busy"); return true; }
        MissionManager manager = plugin.getMissionManager();
        seasonWarningPending = true;
        send(sender, "messages.season.reset-warning");
        try {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                seasonWarningPending = false;
                if (!isCurrent(manager) || !canStart(sender)) return;
                try { observeReset(sender, "season", manager, manager.forceResetSeasonAsync()); }
                catch (RuntimeException failure) { report(sender, "request the season reset", "messages.reset-failed", failure); }
            }, 40L);
        } catch (RuntimeException failure) {
            seasonWarningPending = false;
            report(sender, "schedule the season reset", "messages.reset-failed", failure);
        }
        return true;
    }

    boolean handleResetMissions(CommandSender sender) {
        if (!canStart(sender)) return true;
        MissionManager manager = plugin.getMissionManager();
        try { observeReset(sender, "mission", manager, manager.forceResetMissionsAsync()); }
        catch (RuntimeException failure) { report(sender, "request the mission reset", "messages.reset-failed", failure); }
        return true;
    }

    private void observeReset(CommandSender sender, String kind, MissionManager manager, CompletableFuture<ResetResult> reset) {
        String admin = sender.getName();
        reset.whenComplete((result, failure) -> onMain(manager, () -> {
            if (failure != null || result == null) { report(sender, "complete the reset", "messages.reset-failed", failure); return; }
            if (result != ResetResult.COMPLETED) {
                send(sender, switch (result) {
                    case BUSY -> "messages.reset-busy";
                    case COOLDOWN -> "messages.reset-cooldown";
                    case NOT_READY -> "messages.data-loading";
                    default -> "messages.reset-failed";
                });
                return;
            }
            send(sender, "messages." + kind + ".reset-complete");
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!player.equals(sender)) player.sendMessage(plugin.getMessageManager().getPrefix()
                        + plugin.getMessageManager().getMessage("messages." + kind + ".reset-admin", "%admin%", admin));
            }
        }));
    }

    private boolean canStart(CommandSender sender) {
        if (sender instanceof Player player && !player.isOnline()) return false;
        if (plugin.isStopping()) return false;
        if (sender.hasPermission("battlepass.admin")) return true;
        send(sender, "messages.no-permission");
        return false;
    }

    private boolean isCurrent(MissionManager manager) {
        return !plugin.isStopping() && plugin.getMissionManager() == manager;
    }

    private void onMain(MissionManager manager, Runnable action) {
        if (!isCurrent(manager)) return;
        if (Bukkit.isPrimaryThread()) action.run();
        else {
            try {
                plugin.getServer().getScheduler().runTask(plugin, () -> { if (isCurrent(manager)) action.run(); });
            } catch (RuntimeException failure) {
                if (!plugin.isStopping()) plugin.getLogger().warning("Could not schedule reset/reload feedback: " + failure.getMessage());
            }
        }
    }

    private void send(CommandSender sender, String message) {
        if (!(sender instanceof Player player) || player.isOnline())
            sender.sendMessage(plugin.getMessageManager().getPrefix() + plugin.getMessageManager().getMessage(message));
    }

    private void report(CommandSender sender, String operation, String message, Throwable failure) {
        plugin.getLogger().warning("Could not " + operation + (failure == null ? "" : ": " + failure.getMessage()));
        send(sender, message);
    }
}
