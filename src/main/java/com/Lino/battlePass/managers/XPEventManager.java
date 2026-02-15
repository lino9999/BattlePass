package com.Lino.battlePass.managers;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.utils.GradientColorParser;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class XPEventManager {

    private final BattlePass plugin;
    private int multiplier = 1;
    private long endTime = 0;
    private BossBar bossBar;
    private BukkitRunnable countdownTask;
    private long totalDuration = 0;

    public XPEventManager(BattlePass plugin) {
        this.plugin = plugin;
    }

    public boolean startEvent(int multiplier, long durationMillis) {
        if (isEventActive()) {
            stopEvent();
        }

        this.multiplier = multiplier;
        this.totalDuration = durationMillis;
        this.endTime = System.currentTimeMillis() + durationMillis;

        bossBar = Bukkit.createBossBar(
                formatBossBarTitle(),
                BarColor.YELLOW,
                BarStyle.SEGMENTED_10
        );
        bossBar.setProgress(1.0);

        for (Player player : Bukkit.getOnlinePlayers()) {
            bossBar.addPlayer(player);
        }

        countdownTask = new BukkitRunnable() {
            @Override
            public void run() {
                long remaining = endTime - System.currentTimeMillis();

                if (remaining <= 0) {
                    stopEvent();
                    String endMsg = GradientColorParser.parse(
                            plugin.getMessageManager().getPrefix() +
                                    plugin.getMessageManager().getMessage("messages.event.ended"));
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.sendMessage(endMsg);
                    }
                    this.cancel();
                    return;
                }

                double progress = (double) remaining / totalDuration;
                bossBar.setProgress(Math.max(0.0, Math.min(1.0, progress)));
                bossBar.setTitle(formatBossBarTitle());

                if (remaining <= 60000) {
                    bossBar.setColor(BarColor.RED);
                } else if (remaining <= 300000) {
                    bossBar.setColor(BarColor.PURPLE);
                }
            }
        };
        countdownTask.runTaskTimer(plugin, 0L, 20L);

        return true;
    }

    public void stopEvent() {
        multiplier = 1;
        endTime = 0;
        totalDuration = 0;

        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }

        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }
    }

    public void addPlayerToBossBar(Player player) {
        if (bossBar != null && isEventActive()) {
            bossBar.addPlayer(player);
        }
    }

    public void removePlayerFromBossBar(Player player) {
        if (bossBar != null) {
            bossBar.removePlayer(player);
        }
    }

    public boolean isEventActive() {
        return multiplier > 1 && System.currentTimeMillis() < endTime;
    }

    public int getMultiplier() {
        if (!isEventActive()) {
            return 1;
        }
        return multiplier;
    }

    public String getTimeRemaining() {
        if (!isEventActive()) return "None";

        long remaining = endTime - System.currentTimeMillis();
        if (remaining <= 0) return "None";

        long hours = remaining / 3600000;
        long minutes = (remaining % 3600000) / 60000;
        long seconds = (remaining % 60000) / 1000;

        if (hours > 0) {
            return hours + "h " + minutes + "m " + seconds + "s";
        } else if (minutes > 0) {
            return minutes + "m " + seconds + "s";
        } else {
            return seconds + "s";
        }
    }

    private String formatBossBarTitle() {
        long remaining = endTime - System.currentTimeMillis();
        if (remaining <= 0) return "";

        long hours = remaining / 3600000;
        long minutes = (remaining % 3600000) / 60000;
        long seconds = (remaining % 60000) / 1000;

        StringBuilder time = new StringBuilder();
        if (hours > 0) time.append(hours).append("h ");
        if (minutes > 0) time.append(minutes).append("m ");
        time.append(seconds).append("s");

        return GradientColorParser.parse(
                "<gradient:#FFD700:#FF6B6B>" + multiplier + "x XP Event</gradient>" +
                        " &8| " +
                        "&7Time Left: " +
                        "<gradient:#4ECDC4:#45B7D1>" + time.toString().trim() + "</gradient>"
        );
    }

    public static long parseDuration(String input) {
        input = input.toLowerCase().trim();
        long total = 0;

        StringBuilder number = new StringBuilder();
        for (char c : input.toCharArray()) {
            if (Character.isDigit(c)) {
                number.append(c);
            } else if (c == 'h' && number.length() > 0) {
                total += Long.parseLong(number.toString()) * 3600000;
                number.setLength(0);
            } else if (c == 'm' && number.length() > 0) {
                total += Long.parseLong(number.toString()) * 60000;
                number.setLength(0);
            } else if (c == 's' && number.length() > 0) {
                total += Long.parseLong(number.toString()) * 1000;
                number.setLength(0);
            }
        }

        return total;
    }

    public static int parseMultiplier(String input) {
        input = input.toLowerCase().trim().replace("x", "");
        try {
            int val = Integer.parseInt(input);
            if (val < 2 || val > 100) return -1;
            return val;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}