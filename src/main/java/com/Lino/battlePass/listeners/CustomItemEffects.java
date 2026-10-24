package com.Lino.battlePass.listeners;

import com.Lino.battlePass.BattlePass;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;

/** Server-thread delayed effects, bound to a player session and invalidated before cancellation. */
final class CustomItemEffects {
    private final BattlePass plugin;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private boolean closed;

    CustomItemEffects(BattlePass plugin) { this.plugin = plugin; }

    void checkNextTick(Player player) {
        if (!available(player)) return;
        Session session = session(player);
        if (session.pendingCheck != null) return;
        ScheduledEffect effect = new ScheduledEffect(session, () -> plugin.getSoundManager().checkAndUpdateSound(player));
        session.pendingCheck = effect;
        effect.schedule(1L);
    }

    void playLater(Player player, long delay, Runnable action) {
        if (available(player)) new ScheduledEffect(session(player), action).schedule(delay);
    }

    void cleanup(UUID uuid) {
        Session previous = sessions.remove(uuid);
        if (previous == null) return;
        List<ScheduledEffect> effects = List.copyOf(previous.effects);
        previous.effects.clear();
        previous.pendingCheck = null;
        for (ScheduledEffect effect : effects) if (effect.task != null) effect.task.cancel();
    }

    void shutdown() {
        closed = true;
        for (UUID uuid : List.copyOf(sessions.keySet())) cleanup(uuid);
    }

    private boolean available(Player player) {
        return !closed && !plugin.isStopping() && plugin.isEnabled() && player.isOnline();
    }

    private Session session(Player player) {
        UUID uuid = player.getUniqueId();
        Session current = sessions.get(uuid);
        if (current == null || current.player != player) {
            cleanup(uuid);
            current = new Session(player);
            sessions.put(uuid, current);
        }
        return current;
    }

    private static final class Session {
        final Player player;
        final Set<ScheduledEffect> effects = new HashSet<>();
        ScheduledEffect pendingCheck;
        Session(Player player) { this.player = player; }
    }

    private final class ScheduledEffect implements Runnable {
        private final Session session;
        private final Runnable action;
        private BukkitTask task;
        ScheduledEffect(Session session, Runnable action) { this.session = session; this.action = action; }

        void schedule(long delay) {
            session.effects.add(this);
            try {
                task = Bukkit.getScheduler().runTaskLater(plugin, this, delay);
                if (!session.effects.contains(this)) task.cancel();
            } catch (RuntimeException failure) {
                session.effects.remove(this);
                if (session.pendingCheck == this) session.pendingCheck = null;
                throw failure;
            }
        }

        @Override public void run() {
            if (!session.effects.remove(this)) return;
            if (session.pendingCheck == this) session.pendingCheck = null;
            if (sessions.get(session.player.getUniqueId()) != session) return;
            if (available(session.player)) action.run();
            else cleanup(session.player.getUniqueId());
        }
    }
}
