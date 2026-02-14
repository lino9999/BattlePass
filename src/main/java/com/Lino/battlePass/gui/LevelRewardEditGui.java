package com.Lino.battlePass.gui;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.models.EditableReward;
import com.Lino.battlePass.utils.GradientColorParser;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class LevelRewardEditGui {

    private final BattlePass plugin;
    private final Player player;
    private final int level;
    private final boolean isPremium;
    private final List<EditableReward> currentRewards;

    public LevelRewardEditGui(BattlePass plugin, Player player, int level, boolean isPremium) {
        this.plugin = plugin;
        this.player = player;
        this.level = level;
        this.isPremium = isPremium;
        this.currentRewards = new ArrayList<>();
        loadCurrentRewards();
    }

    private void loadCurrentRewards() {
        try {
            List<EditableReward> rewards = plugin.getRewardManager().getEditableRewards(level, isPremium);
            if (rewards != null) {
                currentRewards.addAll(rewards);
            }
        } catch (Exception e) {
            // Keep editor open even if reward loading fails.
        }
    }

    public void open() {
        if (!player.hasPermission("battlepass.admin")) {
            player.sendMessage(plugin.getMessageManager().getPrefix() +
                    GradientColorParser.parse("<gradient:#FF0000:#FF6B6B>✗ У вас нет прав для доступа к этому!</gradient>"));
            return;
        }

        String gradient = isPremium ?
                "<gradient:#FFD700:#FF6B6B>" :
                "<gradient:#4ECDC4:#45B7D1>";

        String title = GradientColorParser.parse(gradient + "Редактирование уровня " + level + " " +
                (isPremium ? "премиум" : "бесплатных") + " наград</gradient>");

        Inventory gui = Bukkit.createInventory(null, 54, title);

        int slot = 0;
        for (EditableReward reward : currentRewards) {
            if (slot >= 36) break;

            if (reward.isCommand()) {
                ItemStack commandItem = new ItemStack(Material.COMMAND_BLOCK);
                ItemMeta meta = commandItem.getItemMeta();
                meta.setDisplayName(GradientColorParser.parse("<gradient:#FFD700:#FF6B6B>Награда-команда</gradient>"));

                List<String> lore = new ArrayList<>();
                lore.add("");
                lore.add(GradientColorParser.parse("&7Отображение: &f" + reward.getDisplayName()));
                lore.add(GradientColorParser.parse("&7Команда: &f" + reward.getCommand()));
                lore.add("");
                lore.add(GradientColorParser.parse("<gradient:#FF6B6B:#FF0000>▶ НАЖМИТЕ, ЧТОБЫ УДАЛИТЬ</gradient>"));

                meta.setLore(lore);
                commandItem.setItemMeta(meta);
                gui.setItem(slot, commandItem);
            } else {
                ItemStack item = reward.getItemStack();
                ItemMeta meta = item.getItemMeta();
                List<String> originalLore = meta.hasLore() ? meta.getLore() : new ArrayList<>();

                List<String> newLore = new ArrayList<>(originalLore);
                newLore.add("");
                newLore.add(GradientColorParser.parse("<gradient:#FF6B6B:#FF0000>▶ НАЖМИТЕ, ЧТОБЫ УДАЛИТЬ</gradient>"));

                meta.setLore(newLore);
                item.setItemMeta(meta);
                gui.setItem(slot, item);
            }
            slot++;
        }

        ItemStack addItem = new ItemStack(Material.LIME_DYE);
        ItemMeta addMeta = addItem.getItemMeta();
        addMeta.setDisplayName(GradientColorParser.parse("<gradient:#00FF88:#45B7D1>+ Добавить предмет-награду</gradient>"));

        List<String> addLore = new ArrayList<>();
        addLore.add("");
        addLore.add(GradientColorParser.parse("&7Перетащите предметы из инвентаря"));
        addLore.add(GradientColorParser.parse("&7в свободные слоты сверху, чтобы добавить"));
        addLore.add(GradientColorParser.parse("&7их как награды для этого уровня"));
        addLore.add("");
        addLore.add(GradientColorParser.parse("&7Поддерживаются все типы предметов, включая:"));
        addLore.add(GradientColorParser.parse("&8• &7Кастомные предметы с NBT"));
        addLore.add(GradientColorParser.parse("&8• &7Предметы с описанием"));
        addLore.add(GradientColorParser.parse("&8• &7Зачарованные предметы"));

        addMeta.setLore(addLore);
        addItem.setItemMeta(addMeta);
        gui.setItem(45, addItem);

        ItemStack addCommand = new ItemStack(Material.COMMAND_BLOCK);
        ItemMeta cmdMeta = addCommand.getItemMeta();
        cmdMeta.setDisplayName(GradientColorParser.parse("<gradient:#FFD700:#FF6B6B>+ Добавить награду-команду</gradient>"));

        List<String> cmdLore = new ArrayList<>();
        cmdLore.add("");
        cmdLore.add(GradientColorParser.parse("&7Добавьте награду-команду"));
        cmdLore.add(GradientColorParser.parse("&7После нажатия введите в чат:"));
        cmdLore.add(GradientColorParser.parse("&e/cmdreward <display> | <command>"));
        cmdLore.add("");
        cmdLore.add(GradientColorParser.parse("&7Пример:"));
        cmdLore.add(GradientColorParser.parse("&e/cmdreward $1000 | eco give <player> 1000"));
        cmdLore.add("");
        cmdLore.add(GradientColorParser.parse("<gradient:#FFD700:#FF6B6B>▶ НАЖМИТЕ, ЧТОБЫ ДОБАВИТЬ КОМАНДУ</gradient>"));

        cmdMeta.setLore(cmdLore);
        addCommand.setItemMeta(cmdMeta);
        gui.setItem(46, addCommand);

        ItemStack saveButton = new ItemStack(Material.EMERALD);
        ItemMeta saveMeta = saveButton.getItemMeta();
        saveMeta.setDisplayName(GradientColorParser.parse("<gradient:#00FF88:#45B7D1>✓ Сохранить изменения</gradient>"));

        List<String> saveLore = new ArrayList<>();
        saveLore.add("");
        saveLore.add(GradientColorParser.parse("&7Сохранить изменения для этого уровня"));
        saveLore.add(GradientColorParser.parse("&7и вернуться к выбору уровней"));
        saveLore.add("");
        saveLore.add(GradientColorParser.parse("<gradient:#00FF88:#45B7D1>▶ НАЖМИТЕ, ЧТОБЫ СОХРАНИТЬ</gradient>"));

        saveMeta.setLore(saveLore);
        saveButton.setItemMeta(saveMeta);
        gui.setItem(49, saveButton);

        ItemStack cancelButton = new ItemStack(Material.BARRIER);
        ItemMeta cancelMeta = cancelButton.getItemMeta();
        cancelMeta.setDisplayName(GradientColorParser.parse("<gradient:#FF6B6B:#FF0000>✗ Отмена</gradient>"));

        List<String> cancelLore = new ArrayList<>();
        cancelLore.add("");
        cancelLore.add(GradientColorParser.parse("&7Отменить изменения и вернуться"));
        cancelLore.add(GradientColorParser.parse("&7к выбору уровней"));

        cancelMeta.setLore(cancelLore);
        cancelButton.setItemMeta(cancelMeta);
        gui.setItem(48, cancelButton);

        ItemStack clearButton = new ItemStack(Material.TNT);
        ItemMeta clearMeta = clearButton.getItemMeta();
        clearMeta.setDisplayName(GradientColorParser.parse("<gradient:#FF0000:#FF6B6B>⚠ Очистить все награды</gradient>"));

        List<String> clearLore = new ArrayList<>();
        clearLore.add("");
        clearLore.add(GradientColorParser.parse("&7Удалить все награды"));
        clearLore.add(GradientColorParser.parse("&7с этого уровня"));
        clearLore.add("");
        clearLore.add(GradientColorParser.parse("<gradient:#FF0000:#FF6B6B>⚠ Это нельзя отменить!</gradient>"));

        clearMeta.setLore(clearLore);
        clearButton.setItemMeta(clearMeta);
        gui.setItem(50, clearButton);

        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName(GradientColorParser.parse("<gradient:#FFE66D:#FF6B6B>Информация об уровне " + level + "</gradient>"));

        List<String> infoLore = new ArrayList<>();
        infoLore.add("");
        infoLore.add(GradientColorParser.parse("&7Тип: " + (isPremium ? "&6Премиум" : "&bБесплатный")));
        infoLore.add(GradientColorParser.parse("&7Текущих наград: &f" + currentRewards.size()));
        infoLore.add("");
        infoLore.add(GradientColorParser.parse("&8Перетаскивайте предметы для добавления"));
        infoLore.add(GradientColorParser.parse("&8Нажимайте на предметы для удаления"));

        infoMeta.setLore(infoLore);
        info.setItemMeta(infoMeta);
        gui.setItem(53, info);

        player.openInventory(gui);
    }

    public List<EditableReward> getCurrentRewards() {
        return currentRewards;
    }

    public void addReward(EditableReward reward) {
        currentRewards.add(reward);
    }

    public void removeReward(int index) {
        if (index >= 0 && index < currentRewards.size()) {
            currentRewards.remove(index);
        }
    }

    public void clearRewards() {
        currentRewards.clear();
    }

    public int getLevel() {
        return level;
    }

    public boolean isPremium() {
        return isPremium;
    }
}
