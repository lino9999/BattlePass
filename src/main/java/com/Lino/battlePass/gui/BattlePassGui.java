package com.Lino.battlePass.gui;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.models.PlayerData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public class BattlePassGui extends BaseGui {

    private final Player player;
    private final PlayerData playerData;
    private final int page;
    private final int maxLevel;

    public BattlePassGui(BattlePass plugin, Player player, int page) {
        super(plugin, plugin.getMessageManager().getMessage("gui.battlepass", "%page%", String.valueOf(page)), 54);
        this.player = player;
        this.playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        this.page = page;
        this.maxLevel = plugin.getRewardManager().getMaxLevel();
    }

    public void open() {
        Inventory gui = createInventory();

        BattlePassMenuControls controls = new BattlePassMenuControls(plugin, player, playerData, page, maxLevel);
        BattlePassRewardRenderer rewards = new BattlePassRewardRenderer(plugin, playerData, page, maxLevel);

        controls.setupProgressItem(gui);
        rewards.setupRewards(gui);
        rewards.setupSeparator(gui);
        controls.setupNavigationButtons(gui);
        controls.setupActionButtons(gui);

        player.openInventory(gui);
        plugin.getGuiManager().getCurrentPages().put(player.getEntityId(), page);
    }
}
