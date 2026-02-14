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
        super(plugin, GradientColorParser.parse("<gradient:#FFD700:#FF6B6B>⚙ Редактор наград</gradient>"), 27);
        this.player = player;
    }

    public void open() {
        if (!player.hasPermission("battlepass.admin")) {
            player.sendMessage(plugin.getMessageManager().getPrefix() +
                    GradientColorParser.parse("<gradient:#FF0000:#FF6B6B>✗ У вас нет прав для доступа к этому!</gradient>"));
            return;
        }

        Inventory gui = createInventory();

        ItemStack freeRewards = new ItemStack(Material.CHEST);
        ItemMeta freeMeta = freeRewards.getItemMeta();
        freeMeta.setDisplayName(GradientColorParser.parse("<gradient:#4ECDC4:#45B7D1>⚡ Редактор бесплатных наград</gradient>"));

        List<String> freeLore = new ArrayList<>();
        freeLore.add("");
        freeLore.add(GradientColorParser.parse("&7Редактировать награды бесплатной ветки"));
        freeLore.add(GradientColorParser.parse("&7которые доступны всем игрокам"));
        freeLore.add("");
        freeLore.add(GradientColorParser.parse("<gradient:#4ECDC4:#45B7D1>▼ Возможности ▼</gradient>"));
        freeLore.add(GradientColorParser.parse("&7• Просмотр всех 54 уровней"));
        freeLore.add(GradientColorParser.parse("&7• Добавление/удаление предметов"));
        freeLore.add(GradientColorParser.parse("&7• Настройка команд"));
        freeLore.add("");
        freeLore.add(GradientColorParser.parse("<gradient:#00FF88:#45B7D1>▶ НАЖМИТЕ ДЛЯ РЕДАКТИРОВАНИЯ</gradient>"));

        freeMeta.setLore(freeLore);
        freeRewards.setItemMeta(freeMeta);
        gui.setItem(11, freeRewards);

        ItemStack premiumRewards = new ItemStack(Material.ENDER_CHEST);
        ItemMeta premiumMeta = premiumRewards.getItemMeta();
        premiumMeta.setDisplayName(GradientColorParser.parse("<gradient:#FFD700:#FF6B6B>★ Редактор премиум-наград</gradient>"));

        List<String> premiumLore = new ArrayList<>();
        premiumLore.add("");
        premiumLore.add(GradientColorParser.parse("&7Редактировать награды премиум-ветки"));
        premiumLore.add(GradientColorParser.parse("&7доступные только владельцам Premium Pass"));
        premiumLore.add("");
        premiumLore.add(GradientColorParser.parse("<gradient:#FFD700:#FF6B6B>▼ Возможности ▼</gradient>"));
        premiumLore.add(GradientColorParser.parse("&7• Просмотр всех 54 уровней"));
        premiumLore.add(GradientColorParser.parse("&7• Добавление/удаление предметов"));
        premiumLore.add(GradientColorParser.parse("&7• Настройка команд"));
        premiumLore.add("");
        premiumLore.add(GradientColorParser.parse("<gradient:#FFD700:#FF6B6B>▶ НАЖМИТЕ ДЛЯ РЕДАКТИРОВАНИЯ</gradient>"));

        premiumMeta.setLore(premiumLore);
        premiumRewards.setItemMeta(premiumMeta);
        gui.setItem(15, premiumRewards);

        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName(GradientColorParser.parse("<gradient:#00FF88:#45B7D1>ℹ Информация</gradient>"));

        List<String> infoLore = new ArrayList<>();
        infoLore.add("");
        infoLore.add(GradientColorParser.parse("<gradient:#4ECDC4:#45B7D1>Как редактировать награды:</gradient>"));
        infoLore.add(GradientColorParser.parse("&71. Выберите бесплатные или премиум-награды"));
        infoLore.add(GradientColorParser.parse("&72. Нажмите на сундук уровня для редактирования"));
        infoLore.add(GradientColorParser.parse("&73. Добавьте предметы из инвентаря"));
        infoLore.add(GradientColorParser.parse("&74. Удалите предметы кликом"));
        infoLore.add(GradientColorParser.parse("&75. Нажмите Сохранить для применения"));
        infoLore.add("");
        infoLore.add(GradientColorParser.parse("<gradient:#FFD700:#FF6B6B>⚠ Внимание:</gradient>"));
        infoLore.add(GradientColorParser.parse("&7Изменения перезагрузят плагин"));
        infoLore.add(GradientColorParser.parse("&7и обновят конфигурационные файлы"));

        infoMeta.setLore(infoLore);
        info.setItemMeta(infoMeta);
        gui.setItem(4, info);

        gui.setItem(22, createBackButton());

        player.openInventory(gui);
    }
}
