package com.Lino.battlePass.events;

import com.Lino.battlePass.BattlePass;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.RegisteredListener;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class BattlePassXPGainEventTest {
    @Test
    void bukkitIgnoreCancelledListenersSkipCancelledXpGains() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        RegisteredListener listener = new RegisteredListener(new Listener() {},
                (ignored, event) -> calls.incrementAndGet(), EventPriority.NORMAL, mock(BattlePass.class), true);
        var event = new BattlePassXPGainEvent(mock(Player.class), BattlePassXPGainEvent.XPSource.MISSION, 100);
        event.setCancelled(true);

        listener.callEvent(event);
        assertEquals(0, calls.get());

        event.setCancelled(false);
        listener.callEvent(event);
        assertEquals(1, calls.get());
    }
}
