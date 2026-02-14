package com.Lino.battlePass.gui;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.models.PlayerData;
import com.Lino.battlePass.models.ShopItem;
import com.Lino.battlePass.utils.GradientColorParser;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class ShopGui extends BaseGui {

    private final Player player;
    private final PlayerData playerData;

    public ShopGui(BattlePass plugin, Player player) {
        super(plugin, plugin.getMessageManager().getGuiMessage("gui.shop"), 54);
        this.player = player;
        this.playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
    }

    public void open() {
        Inventory gui = createInventory();

        setupBalanceItem(gui);
        setupShopItems(gui);
        gui.setItem(49, createBackButton());

        player.openInventory(gui);
    }

    private void setupBalanceItem(Inventory gui) {
        ItemStack balance = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = balance.getItemMeta();
        meta.setDisplayName(plugin.getMessageManager().getGuiMessage("items.shop-balance.name"));
        meta.setLore(plugin.getMessageManager().getGuiMessages("items.shop-balance.lore",
                "%coins%", String.valueOf(playerData.battleCoins)));

        balance.setItemMeta(meta);
        gui.setItem(4, balance);
    }

    private void setupShopItems(Inventory gui) {
        for (ShopItem item : plugin.getShopManager().getShopItems().values()) {
            ItemStack shopItem = new ItemStack(item.material);
            ItemMeta meta = shopItem.getItemMeta();
            meta.setDisplayName(GradientColorParser.parse(item.displayName));

            List<String> lore = new ArrayList<>();
            for (String line : item.lore) {
                lore.add(GradientColorParser.parse(line));
            }

            meta.setLore(lore);
            shopItem.setItemMeta(meta);
            gui.setItem(item.slot, shopItem);
        }
    }
}
