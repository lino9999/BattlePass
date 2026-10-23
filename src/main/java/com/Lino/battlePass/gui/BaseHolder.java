package com.Lino.battlePass.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import java.util.Map;
import java.util.Objects;

public class BaseHolder implements InventoryHolder {
    public enum MenuType {
        UNKNOWN, BATTLE_PASS, LEADERBOARD, MISSIONS, SHOP, REWARDS_EDITOR, REWARDS_CATEGORY,
        REWARD_LEVEL_EDITOR, MISSION_EDITOR, MISSION_TYPE, MISSION_DETAILS
    }

    public record MenuContext(MenuType type, int page, boolean premium, String missionKey, LevelRewardEditGui editor,
                              Map<Integer, String> entries) {
        public MenuContext {
            Objects.requireNonNull(type);
            page = Math.max(1, page);
            entries = Map.copyOf(entries);
        }

        public MenuContext(MenuType type, int page, boolean premium, String missionKey, LevelRewardEditGui editor) {
            this(type, page, premium, missionKey, editor, Map.of());
        }

        public static MenuContext menu(MenuType type) { return page(type, 1); }
        public static MenuContext page(MenuType type, int page) { return new MenuContext(type, page, false, null, null); }
        public static MenuContext rewards(int page, boolean premium) { return new MenuContext(MenuType.REWARDS_CATEGORY, page, premium, null, null); }
        public static MenuContext mission(String key) { return new MenuContext(MenuType.MISSION_DETAILS, 1, false, key, null); }
        public static MenuContext editor(LevelRewardEditGui editor) { return new MenuContext(MenuType.REWARD_LEVEL_EDITOR, editor.getRewardPage(), editor.isPremium(), null, editor); }
        public static MenuContext missions(int page, Map<Integer, String> entries) { return new MenuContext(MenuType.MISSION_EDITOR, page, false, null, null, entries); }
    }

    private final MenuContext context;
    private Inventory inventory;

    public BaseHolder() { this(MenuContext.menu(MenuType.UNKNOWN)); }

    public BaseHolder(MenuContext context) {
        this.context = Objects.requireNonNull(context);
    }

    public MenuContext getContext() { return context; }

    public static MenuContext contextOf(Inventory inventory) {
        return inventory != null && inventory.getHolder() instanceof BaseHolder holder ? holder.getContext() : null;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public static boolean isBattlePassInventory(Inventory inventory) {
        return contextOf(inventory) != null;
    }
}
