package com.Lino.battlePass.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class BattlePassXPGainEvent extends Event implements Cancellable {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final Player player;
    private final XPSource source;
    private int amount;
    private boolean cancelled;

    public BattlePassXPGainEvent(Player player, XPSource source, int amount) {
        this.player = player;
        this.source = source;
        this.amount = amount;
    }

    public Player getPlayer() {
        return player;
    }

    public XPSource getSource() {
        return source;
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }

    public enum XPSource {
        MISSION,
        DAILY_REWARD,
        LEVEL_BOOST
    }
}
