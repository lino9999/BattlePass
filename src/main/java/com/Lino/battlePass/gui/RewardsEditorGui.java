package com.Lino.battlePass.gui;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.utils.GradientColorParser;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class RewardsEditorGui extends BaseGui {

    private final Player player;

    public RewardsEditorGui(BattlePass plugin, Player player) {
        super(plugin, GradientColorParser.parse("<gradient:#FFD700:#FF6B6B>⚙ Rewards Editor</gradient>"), 27);
        this.player = player;
    }

    public void open() {
        if (!player.hasPermission("battlepass.admin")) {
            player.sendMessage(plugin.getMessageManager().getPrefix() +
                    GradientColorParser.parse("<gradient:#FF0000:#FF6B6B>✗ You don't have permission to access this!</gradient>"));
            return;
        }

        Inventory gui = createInventory();

        ItemStack freeRewards = new ItemStack(Material.CHEST);
        ItemMeta freeMeta = freeRewards.getItemMeta();
        freeMeta.setDisplayName(GradientColorParser.parse("<gradient:#4ECDC4:#45B7D1>⚡ Free Rewards Editor</gradient>"));

        List<String> freeLore = new ArrayList<>();
        freeLore.add("");
        freeLore.add(GradientColorParser.parse("&7Edit rewards for the free battle pass"));
        freeLore.add(GradientColorParser.parse("&7that all players can claim"));
        freeLore.add("");
        freeLore.add(GradientColorParser.parse("<gradient:#4ECDC4:#45B7D1>▼ Features ▼</gradient>"));
        freeLore.add(GradientColorParser.parse("&7• View all 54 levels"));
        freeLore.add(GradientColorParser.parse("&7• Add/remove items"));
        freeLore.add(GradientColorParser.parse("&7• Configure commands"));
        freeLore.add("");
        freeLore.add(GradientColorParser.parse("<gradient:#00FF88:#45B7D1>▶ CLICK TO EDIT</gradient>"));

        freeMeta.setLore(freeLore);
        freeRewards.setItemMeta(freeMeta);
        gui.setItem(11, freeRewards);

        ItemStack premiumRewards = new ItemStack(Material.ENDER_CHEST);
        ItemMeta premiumMeta = premiumRewards.getItemMeta();
        premiumMeta.setDisplayName(GradientColorParser.parse("<gradient:#FFD700:#FF6B6B>★ Premium Rewards Editor</gradient>"));

        List<String> premiumLore = new ArrayList<>();
        premiumLore.add("");
        premiumLore.add(GradientColorParser.parse("&7Edit rewards for the premium battle pass"));
        premiumLore.add(GradientColorParser.parse("&7exclusive to premium pass holders"));
        premiumLore.add("");
        premiumLore.add(GradientColorParser.parse("<gradient:#FFD700:#FF6B6B>▼ Features ▼</gradient>"));
        premiumLore.add(GradientColorParser.parse("&7• View all 54 levels"));
        premiumLore.add(GradientColorParser.parse("&7• Add/remove items"));
        premiumLore.add(GradientColorParser.parse("&7• Configure commands"));
        premiumLore.add("");
        premiumLore.add(GradientColorParser.parse("<gradient:#FFD700:#FF6B6B>▶ CLICK TO EDIT</gradient>"));

        premiumMeta.setLore(premiumLore);
        premiumRewards.setItemMeta(premiumMeta);
        gui.setItem(15, premiumRewards);

        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName(GradientColorParser.parse("<gradient:#00FF88:#45B7D1>ℹ Information</gradient>"));

        List<String> infoLore = new ArrayList<>();
        infoLore.add("");
        infoLore.add(GradientColorParser.parse("<gradient:#4ECDC4:#45B7D1>How to Edit Rewards:</gradient>"));
        infoLore.add(GradientColorParser.parse("&71. Choose Free or Premium rewards"));
        infoLore.add(GradientColorParser.parse("&72. Click on a level chest to edit"));
        infoLore.add(GradientColorParser.parse("&73. Add items from your inventory"));
        infoLore.add(GradientColorParser.parse("&74. Remove items by clicking them"));
        infoLore.add(GradientColorParser.parse("&75. Click Save to apply changes"));
        infoLore.add("");
        infoLore.add(GradientColorParser.parse("<gradient:#FFD700:#FF6B6B>⚠ Warning:</gradient>"));
        infoLore.add(GradientColorParser.parse("&7Changes will reload the plugin"));
        infoLore.add(GradientColorParser.parse("&7and update configuration files"));

        infoMeta.setLore(infoLore);
        info.setItemMeta(infoMeta);
        gui.setItem(4, info);

        gui.setItem(22, createBackButton());

        player.openInventory(gui);
    }
}