package com.Lino.battlePass.managers;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.models.PlayerData;
import com.Lino.battlePass.models.ShopItem;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.*;

public class ShopManager {

    private final BattlePass plugin;
    private FileConfiguration shopConfig;
    private final Map<Integer, ShopItem> shopItems = new HashMap<>();

    public ShopManager(BattlePass plugin) {
        this.plugin = plugin;
        loadShop();
    }

    public void loadShop() {
        File shopFile = new File(plugin.getDataFolder(), "shop.yml");
        if (!shopFile.exists()) {
            plugin.saveResource("shop.yml", false);
        }

        shopConfig = YamlConfiguration.loadConfiguration(shopFile);
        shopItems.clear();

        ConfigurationSection items = shopConfig.getConfigurationSection("shop-items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                ConfigurationSection item = items.getConfigurationSection(key);
                if (item == null) continue;

                int slot = item.getInt("slot");
                Material material = Material.valueOf(item.getString("material", "STONE"));
                String displayName = item.getString("display-name", "Shop Item");
                List<String> lore = item.getStringList("lore");
                int price = item.getInt("price", 0);
                List<String> commands = item.getStringList("commands");

                List<ItemStack> itemsList = new ArrayList<>();
                if (item.contains("items")) {
                    List<Map<?, ?>> itemMaps = item.getMapList("items");
                    for (Map<?, ?> map : itemMaps) {
                        Material mat = Material.valueOf((String) map.get("material"));
                        int amount = (Integer) map.get("amount");
                        itemsList.add(new ItemStack(mat, amount));
                    }
                }

                shopItems.put(slot, new ShopItem(slot, material, displayName, lore, price, commands, itemsList));
            }
        }
    }

    public void purchaseItem(Player player, int slot) {
        ShopItem item = shopItems.get(slot);
        if (item == null) return;

        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        MessageManager messageManager = plugin.getMessageManager();

        if (data.battleCoins < item.price) {
            player.sendMessage(messageManager.getPrefix() +
                    messageManager.getMessage("messages.shop.insufficient-coins",
                            "%required%", String.valueOf(item.price),
                            "%current%", String.valueOf(data.battleCoins)));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        data.battleCoins -= item.price;
        plugin.getPlayerDataManager().markForSave(player.getUniqueId());

        for (String command : item.commands) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    command.replace("<player>", player.getName()));
        }

        for (ItemStack itemStack : item.items) {
            player.getInventory().addItem(itemStack);
        }

        player.sendMessage(messageManager.getPrefix() +
                messageManager.getMessage("messages.shop.purchase-success",
                        "%item%", ChatColor.stripColor(item.displayName),
                        "%price%", String.valueOf(item.price)));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

        plugin.getGuiManager().openShopGUI(player);
    }

    public Map<Integer, ShopItem> getShopItems() {
        return shopItems;
    }

    public void reload() {
        loadShop();
    }
}