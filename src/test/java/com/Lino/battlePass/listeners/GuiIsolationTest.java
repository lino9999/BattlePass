package com.Lino.battlePass.listeners;

import com.Lino.battlePass.BattlePass;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class GuiIsolationTest {
    @Test
    void allClickListenersIgnoreOtherPluginsInventories() {
        BattlePass plugin = mock(BattlePass.class);
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        InventoryView view = foreignView();
        when(event.getView()).thenReturn(view);

        new GuiClickListener(plugin).onInventoryClick(event);
        new RewardsEditorListener(plugin).onInventoryClick(event);
        new MissionEditorListener(plugin).onInventoryClick(event);

        verify(event, never()).setCancelled(anyBoolean());
        verifyNoInteractions(plugin);
    }

    @Test
    void dragListenerIgnoresOtherPluginsInventories() {
        BattlePass plugin = mock(BattlePass.class);
        InventoryDragEvent event = mock(InventoryDragEvent.class);
        InventoryView view = foreignView();
        when(event.getView()).thenReturn(view);

        new RewardsEditorListener(plugin).onInventoryDrag(event);

        verify(event, never()).setCancelled(anyBoolean());
        verifyNoInteractions(plugin);
    }

    @Test
    void closingAnUnrelatedEditorDoesNotClearBattlePassEditorState() {
        BattlePass plugin = mock(BattlePass.class);
        InventoryCloseEvent event = mock(InventoryCloseEvent.class);
        InventoryView view = foreignView();
        when(event.getView()).thenReturn(view);
        when(event.getPlayer()).thenReturn(mock(Player.class));

        new RewardsEditorListener(plugin).onInventoryClose(event);

        verifyNoInteractions(plugin);
    }

    private InventoryView foreignView() {
        InventoryView view = mock(InventoryView.class);
        Inventory inventory = mock(Inventory.class);
        when(inventory.getHolder()).thenReturn(mock(InventoryHolder.class));
        when(view.getTopInventory()).thenReturn(inventory);
        // Deliberately match BattlePass's editor title: ownership must decide, not the title.
        when(view.getTitle()).thenReturn("Edit Level 1 Rewards");
        return view;
    }
}
