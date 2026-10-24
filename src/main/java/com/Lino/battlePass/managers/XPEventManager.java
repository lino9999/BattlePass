package com.Lino.battlePass.managers;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.utils.GradientColorParser;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Locale;
import java.util.function.LongSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Server-thread event changes; immutable timing snapshots are safely published to placeholder readers. */
public class XPEventManager {
    private final BattlePass plugin;
    private final LongSupplier clock;
    private volatile RunningEvent active;
    private volatile boolean shuttingDown;
    private boolean starting;
    private long revision;
    private static final Pattern DURATION_TOKEN = Pattern.compile("(\\d+)([hms])");

    public XPEventManager(BattlePass plugin) { this(plugin, System::currentTimeMillis); }

    XPEventManager(BattlePass plugin, LongSupplier clock) { this.plugin = plugin; this.clock = clock; }

    public boolean startEvent(int multiplier, long durationMillis) {
        long now = clock.getAsLong();
        if (starting || !canRun() || multiplier < 2 || multiplier > 100 || durationMillis <= 0
                || durationMillis > Long.MAX_VALUE - now) return false;
        starting = true;
        long preparedAt = revision;
        RunningEvent prepared = new RunningEvent(multiplier, durationMillis, now + durationMillis);
        try {
            prepared.bar = Bukkit.createBossBar(prepared.title(), BarColor.YELLOW, BarStyle.SEGMENTED_10);
            prepared.bar.setVisible(false);
            prepared.bar.setProgress(1.0);
            for (Player player : Bukkit.getOnlinePlayers()) prepared.bar.addPlayer(player);
            prepared.task = Bukkit.getScheduler().runTaskTimer(plugin, prepared, 0L, 20L);
            if (!canPublish(prepared, preparedAt)) { prepared.close(); return false; }
            prepared.bar.setVisible(true);
            if (!canPublish(prepared, preparedAt)) { prepared.close(); return false; }
            RunningEvent previous = active;
            active = prepared;
            revision++;
            if (previous != null) previous.close();
            return active == prepared && canRun();
        } catch (RuntimeException failure) {
            if (active == prepared) { active = null; revision++; }
            prepared.close();
            plugin.getLogger().warning("Could not start XP event: " + failure.getMessage());
            return false;
        } finally {
            starting = false;
        }
    }

    private boolean canPublish(RunningEvent prepared, long preparedAt) {
        return canRun() && revision == preparedAt && clock.getAsLong() < prepared.endTime;
    }

    private boolean canRun() { return !shuttingDown && !plugin.isStopping() && plugin.isEnabled(); }

    public void shutdown() { shuttingDown = true; stopEvent(); }

    public void stopEvent() {
        revision++;
        RunningEvent previous = active;
        active = null;
        if (previous != null) previous.close();
    }

    private void stopOwned(RunningEvent event) {
        if (active == event) stopEvent();
    }

    public void addPlayerToBossBar(Player player) {
        RunningEvent current = active;
        if (current != null && canRun() && current.isActive() && player.isOnline()) current.bar.addPlayer(player);
    }

    public void removePlayerFromBossBar(Player player) {
        RunningEvent current = active;
        if (current != null) current.bar.removePlayer(player);
    }

    public boolean isEventActive() {
        RunningEvent current = active;
        return current != null && current.isActive();
    }

    public int getMultiplier() {
        RunningEvent current = active;
        return current != null && current.isActive() ? current.multiplier : 1;
    }

    public String getTimeRemaining() {
        RunningEvent current = active;
        if (current == null || !current.isActive()) return "None";
        long remaining = current.endTime - clock.getAsLong();
        if (remaining <= 0) return "None";
        long hours = remaining / 3600000;
        long minutes = (remaining % 3600000) / 60000;
        long seconds = (remaining % 60000) / 1000;
        if (hours > 0) return hours + "h " + minutes + "m " + seconds + "s";
        if (minutes > 0) return minutes + "m " + seconds + "s";
        return seconds + "s";
    }

    private final class RunningEvent implements Runnable {
        final int multiplier;
        final long totalDuration;
        final long endTime;
        private BossBar bar;
        private BukkitTask task;
        private volatile boolean closed;
        private BarColor color = BarColor.YELLOW;

        RunningEvent(int multiplier, long totalDuration, long endTime) {
            this.multiplier = multiplier; this.totalDuration = totalDuration; this.endTime = endTime;
        }

        // Final play-time credits are flushed after plugin stopping begins, before shutdown closes the event.
        boolean isActive() { return !closed && !shuttingDown && clock.getAsLong() < endTime; }

        void close() {
            if (closed) return;
            closed = true;
            if (task != null) release(task::cancel);
            if (bar != null) { release(bar::removeAll); release(() -> bar.setVisible(false)); }
        }

        private void release(Runnable cleanup) {
            try { cleanup.run(); }
            catch (RuntimeException failure) { plugin.getLogger().warning("Could not release XP event resource: " + failure.getMessage()); }
        }

        @Override public void run() {
            if (closed || active != this) return;
            if (!canRun()) { stopOwned(this); return; }
            long remaining = endTime - clock.getAsLong();
            if (remaining <= 0) {
                stopOwned(this);
                if (!canRun() || active != null) return;
                String message = GradientColorParser.parse(plugin.getMessageManager().getPrefix()
                        + plugin.getMessageManager().getMessage("messages.event.ended"));
                for (Player player : Bukkit.getOnlinePlayers()) player.sendMessage(message);
                return;
            }
            try {
                bar.setProgress(Math.max(0.0, Math.min(1.0, (double) remaining / totalDuration)));
                bar.setTitle(title());
                BarColor nextColor = remaining <= 60000 ? BarColor.RED : remaining <= 300000 ? BarColor.PURPLE : BarColor.YELLOW;
                if (color != nextColor) { bar.setColor(nextColor); color = nextColor; }
            } catch (RuntimeException failure) {
                stopOwned(this);
                plugin.getLogger().warning("Could not update XP event: " + failure.getMessage());
            }
        }

        private String title() {
            long remaining = endTime - clock.getAsLong();
            if (remaining <= 0) return "";
            long hours = remaining / 3600000;
            long minutes = (remaining % 3600000) / 60000;
            long seconds = (remaining % 60000) / 1000;
            StringBuilder time = new StringBuilder();
            if (hours > 0) time.append(hours).append("h ");
            if (minutes > 0) time.append(minutes).append("m ");
            time.append(seconds).append("s");
            return GradientColorParser.parse(
                    "<gradient:#FFD700:#FF6B6B>" + multiplier + "x XP Event</gradient>"
                    + " &8| " + "&7Time Left: " + "<gradient:#4ECDC4:#45B7D1>"
                    + time.toString().trim() + "</gradient>");
        }
    }

    public static long parseDuration(String input) {
        if (input == null) return 0;
        input = input.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        long total = 0;
        int end = 0;
        Matcher matcher = DURATION_TOKEN.matcher(input);
        try {
            while (matcher.find()) {
                if (matcher.start() != end) return 0;
                long unit = switch (matcher.group(2)) {
                    case "h" -> 3600000L;
                    case "m" -> 60000L;
                    default -> 1000L;
                };
                long amount = Long.parseLong(matcher.group(1));
                total = Math.addExact(total, Math.multiplyExact(amount, unit));
                end = matcher.end();
            }
        } catch (NumberFormatException | ArithmeticException invalid) {
            return 0;
        }
        return end == input.length() ? total : 0;
    }

    public static int parseMultiplier(String input) {
        if (input == null) return -1;
        input = input.toLowerCase(Locale.ROOT).trim();
        if (input.endsWith("x")) input = input.substring(0, input.length() - 1);
        try {
            int val = Integer.parseInt(input);
            if (val < 2 || val > 100) return -1;
            return val;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
