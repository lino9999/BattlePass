package com.Lino.battlePass.placeholders;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.models.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/** No SQL waits: bounded leaderboard snapshots shared by all leaderboard placeholder readers. */
final class LeaderboardPlaceholders {
    private static final long REFRESH_NANOS = TimeUnit.SECONDS.toNanos(5);
    private static final Set<String> TOP_FIELDS = Set.of("name", "level", "xp", "coins", "totallevels");
    private final BattlePass plugin;
    private final LongSupplier clock;
    private volatile List<Entry> cached;
    private volatile boolean closed;
    private Request pending;
    private boolean attempted;
    private long lastAttempt;

    LeaderboardPlaceholders(BattlePass plugin, LongSupplier clock) { this.plugin = plugin; this.clock = clock; }

    String rank(UUID uuid) {
        List<Entry> entries = snapshot();
        if (closed) return "";
        if (entries == null) return "Loading...";
        for (int i = 0; i < entries.size(); i++) if (entries.get(i).row.uuid.equals(uuid)) return String.valueOf(i + 1);
        return "Unranked";
    }

    String top(String identifier) {
        String[] parts = identifier.split("_", -1);
        if (parts.length > 2 || parts.length == 2 && !TOP_FIELDS.contains(parts[1])) return "";
        int position;
        try { position = Integer.parseInt(parts[0]); }
        catch (NumberFormatException invalid) { return ""; }
        if (position < 1 || position > 10) return "";
        List<Entry> entries = snapshot();
        if (closed) return "";
        if (entries == null) return "Loading...";
        if (position > entries.size()) return "Empty";
        Entry entry = entries.get(position - 1);
        return switch (parts.length == 1 ? "name" : parts[1]) {
            case "level" -> String.valueOf(entry.row.level);
            case "xp" -> String.valueOf(entry.row.xp);
            case "coins" -> String.valueOf(entry.row.coins);
            case "totallevels" -> String.valueOf(entry.row.totalLevels);
            default -> entry.name;
        };
    }

    boolean isClosed() { return closed; }

    void close() {
        Request previous;
        synchronized (this) { closed = true; previous = pending; pending = null; }
        if (previous != null && previous.task != null) {
            try { previous.task.cancel(); }
            catch (RuntimeException failure) { plugin.getLogger().warning("Could not cancel leaderboard placeholder publication: " + failure.getMessage()); }
        }
    }

    private boolean available() { return !closed && !plugin.isStopping() && plugin.isEnabled(); }

    private List<Entry> snapshot() {
        Request request;
        synchronized (this) {
            long now = clock.getAsLong();
            if (!available() || pending != null || attempted && now - lastAttempt < REFRESH_NANOS) return cached;
            attempted = true;
            lastAttempt = now;
            request = new Request();
            pending = request;
        }
        try { plugin.getDatabaseManager().getTop10Players().whenComplete((players, failure) -> complete(request, players, failure)); }
        catch (RuntimeException failure) { fail(request, failure); }
        return cached;
    }

    private synchronized boolean owns(Request request) { return pending == request && available(); }

    private void complete(Request request, List<PlayerData> players, Throwable failure) {
        if (!owns(request)) return;
        if (failure != null) { fail(request, failure); return; }
        try {
            List<Row> rows = players.stream().limit(10).map(data -> new Row(data.uuid, data.level, data.xp, data.battleCoins, data.totalLevels)).toList();
            if (rows.isEmpty() || plugin.getServer().isPrimaryThread()) publish(request, rows);
            else {
                BukkitTask task = plugin.getServer().getScheduler().runTask(plugin, () -> publish(request, rows));
                boolean obsolete;
                synchronized (this) { obsolete = pending != request || !available(); if (!obsolete) request.task = task; }
                if (obsolete) task.cancel();
            }
        } catch (RuntimeException invalid) { fail(request, invalid); }
    }

    private void publish(Request request, List<Row> rows) {
        if (!owns(request)) return;
        try {
            List<Entry> entries = new ArrayList<>();
            for (Row row : rows) {
                if (!owns(request)) return;
                String name = Bukkit.getOfflinePlayer(row.uuid).getName();
                entries.add(new Entry(row, name == null ? "Unknown" : name));
            }
            synchronized (this) {
                if (!owns(request)) return;
                cached = List.copyOf(entries);
                lastAttempt = clock.getAsLong();
                pending = null;
            }
        } catch (RuntimeException failure) { fail(request, failure); }
    }

    private void fail(Request request, Throwable failure) {
        synchronized (this) {
            if (pending != request) return;
            pending = null;
            lastAttempt = clock.getAsLong();
        }
        if (available()) plugin.getLogger().warning("Could not refresh leaderboard placeholders: " + failure.getMessage());
    }

    private static final class Request { volatile BukkitTask task; }
    private record Row(UUID uuid, int level, int xp, int coins, int totalLevels) {}
    private record Entry(Row row, String name) {}
}
