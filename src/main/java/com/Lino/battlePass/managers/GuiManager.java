package com.Lino.battlePass.managers;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.gui.*;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GuiManager {

    private final BattlePass plugin;
    private final PlayerDataManager playerDataManager;
    private final MissionManager missionManager;
    private final RewardManager rewardManager;
    private final MessageManager messageManager;
    private final ConfigManager configManager;

    private final Map<Integer, Integer> currentPages = new ConcurrentHashMap<>();

    public GuiManager(BattlePass plugin, PlayerDataManager playerDataManager, MissionManager missionManager,
                      RewardManager rewardManager, MessageManager messageManager, ConfigManager configManager) {
        this.plugin = plugin;
        this.playerDataManager = playerDataManager;
        this.missionManager = missionManager;
        this.rewardManager = rewardManager;
        this.messageManager = messageManager;
        this.configManager = configManager;
    }

    public void openBattlePassGUI(Player player, int page) {
        if (page < 1) page = 1;

        int maxLevel = rewardManager.getMaxLevel();
        int maxPages = (int) Math.ceil(maxLevel / 9.0);
        if (maxPages < 1) maxPages = 1;

        if (page > maxPages) page = maxPages;

        BattlePassGui gui = new BattlePassGui(plugin, player, page);
        gui.open();
    }

    public void openMissionsGUI(Player player) {
        MissionsGui gui = new MissionsGui(plugin, player);
        gui.open();
    }

    public void openLeaderboardGUI(Player player) {
        LeaderboardGui gui = new LeaderboardGui(plugin, player);
        gui.open();
    }

    public void openShopGUI(Player player) {
        ShopGui gui = new ShopGui(plugin, player);
        gui.open();
    }

    public void clearCache() {
        currentPages.clear();
    }

    public void cleanExpiredCachePublic() {
        // This method is no longer needed with the new structure
        // but we keep it for compatibility
    }

    public Map<Integer, Integer> getCurrentPages() {
        return currentPages;
    }
}