package com.Lino.battlePass.utils;

import org.bukkit.Material;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class MaterialNameResolver {

    private static final Map<String, String> RUSSIAN_NAMES = new HashMap<>();
    private static final String LANGUAGE_RU = "RU";
    private static final String LANGUAGE_EN = "EN";

    static {
        register("AMETHYST_BLOCK", "Аметистовый блок");
        register("AMETHYST_SHARD", "Аметистовый осколок");
        register("ANCIENT_DEBRIS", "Древние обломки");
        register("ANVIL", "Наковальня");
        register("BEACON", "Маяк");
        register("BLAZE_POWDER", "Огненный порошок");
        register("BLAZE_ROD", "Стержень ифрита");
        register("BREWING_STAND", "Варочная стойка");
        register("CHEST", "Сундук");
        register("CHORUS_FRUIT", "Плод коруса");
        register("COAL", "Уголь");
        register("COMMAND_BLOCK", "Командный блок");
        register("CONDUIT", "Морской проводник");
        register("COMPASS", "Компас");
        register("COPPER_BLOCK", "Медный блок");
        register("CREEPER_HEAD", "Голова крипера");
        register("CRYING_OBSIDIAN", "Плачущий обсидиан");
        register("DIAMOND", "Алмаз");
        register("DIAMOND_BLOCK", "Алмазный блок");
        register("DIAMOND_BOOTS", "Алмазные ботинки");
        register("DIAMOND_CHESTPLATE", "Алмазный нагрудник");
        register("DIAMOND_HELMET", "Алмазный шлем");
        register("DIAMOND_HORSE_ARMOR", "Алмазная конская броня");
        register("DIAMOND_LEGGINGS", "Алмазные поножи");
        register("DIRT", "Земля");
        register("DRAGON_BREATH", "Дыхание дракона");
        register("DRAGON_EGG", "Яйцо дракона");
        register("DRAGON_HEAD", "Голова дракона");
        register("ECHO_SHARD", "Осколок эха");
        register("ELYTRA", "Элитры");
        register("EMERALD", "Изумруд");
        register("EMERALD_BLOCK", "Изумрудный блок");
        register("ENCHANTED_BOOK", "Зачарованная книга");
        register("ENCHANTED_GOLDEN_APPLE", "Зачарованное золотое яблоко");
        register("END_CRYSTAL", "Кристалл Края");
        register("ENDER_CHEST", "Эндер-сундук");
        register("ENDER_EYE", "Око Края");
        register("ENDER_PEARL", "Эндер-жемчуг");
        register("EXPERIENCE_BOTTLE", "Пузырек опыта");
        register("FERMENTED_SPIDER_EYE", "Маринованный паучий глаз");
        register("GHAST_TEAR", "Слеза гаста");
        register("GLISTERING_MELON_SLICE", "Сверкающий ломтик арбуза");
        register("GLOWSTONE", "Светокамень");
        register("GOLD_BLOCK", "Золотой блок");
        register("GOLD_INGOT", "Золотой слиток");
        register("GOLD_NUGGET", "Золотой самородок");
        register("GOLDEN_APPLE", "Золотое яблоко");
        register("GOLDEN_CARROT", "Золотая морковь");
        register("GRAY_STAINED_GLASS", "Серое стекло");
        register("GRAY_STAINED_GLASS_PANE", "Серая стеклянная панель");
        register("GREEN_STAINED_GLASS", "Зеленое стекло");
        register("GUNPOWDER", "Порох");
        register("HEART_OF_THE_SEA", "Сердце моря");
        register("HONEY_BLOCK", "Медовый блок");
        register("HONEYCOMB", "Пчелиные соты");
        register("IRON_BARS", "Железные прутья");
        register("IRON_BLOCK", "Железный блок");
        register("IRON_HORSE_ARMOR", "Железная конская броня");
        register("IRON_INGOT", "Железный слиток");
        register("LAPIS_LAZULI", "Лазурит");
        register("LIME_STAINED_GLASS", "Лаймовое стекло");
        register("LODESTONE", "Магнетит");
        register("MAGMA_CREAM", "Магмовый крем");
        register("MUSIC_DISC_OTHERSIDE", "Пластинка Otherside");
        register("MUSIC_DISC_PIGSTEP", "Пластинка Pigstep");
        register("NAME_TAG", "Бирка");
        register("NAUTILUS_SHELL", "Раковина наутилуса");
        register("NETHER_STAR", "Звезда Незера");
        register("NETHERITE_BLOCK", "Незеритовый блок");
        register("NETHERITE_BOOTS", "Незеритовые ботинки");
        register("NETHERITE_CHESTPLATE", "Незеритовый нагрудник");
        register("NETHERITE_HELMET", "Незеритовый шлем");
        register("NETHERITE_INGOT", "Незеритовый слиток");
        register("NETHERITE_LEGGINGS", "Незеритовые поножи");
        register("NETHERITE_SCRAP", "Незеритовый лом");
        register("NETHERITE_SWORD", "Незеритовый меч");
        register("NETHERITE_UPGRADE_SMITHING_TEMPLATE", "Кузнечный шаблон незерита");
        register("PHANTOM_MEMBRANE", "Мембрана фантома");
        register("PRISMARINE_CRYSTALS", "Кристаллы призмарина");
        register("PRISMARINE_SHARD", "Осколок призмарина");
        register("QUARTZ", "Кварц");
        register("RABBIT_FOOT", "Кроличья лапка");
        register("RECOVERY_COMPASS", "Компас восстановления");
        register("REDSTONE", "Редстоун");
        register("RESPAWN_ANCHOR", "Якорь возрождения");
        register("SADDLE", "Седло");
        register("SCUTE", "Щиток черепахи");
        register("SHULKER_BOX", "Шалкеровый ящик");
        register("SHULKER_SHELL", "Раковина шалкера");
        register("SKELETON_SKULL", "Череп скелета");
        register("SLIME_BALL", "Слизь");
        register("SPIDER_EYE", "Паучий глаз");
        register("SPYGLASS", "Подзорная труба");
        register("STONE", "Камень");
        register("STRING", "Нить");
        register("TOTEM_OF_UNDYING", "Тотем бессмертия");
        register("TRIDENT", "Трезубец");
        register("TURTLE_SCUTE", "Черепок черепахи");
        register("WITHER_SKELETON_SKULL", "Череп визер-скелета");
        register("ZOMBIE_HEAD", "Голова зомби");
    }

    private MaterialNameResolver() {
    }

    public static String resolve(Material material) {
        return resolve(material, LANGUAGE_RU);
    }

    public static String resolve(Material material, String language) {
        if (material == null) {
            return getDefaultName(language);
        }
        return resolve(material.name(), language);
    }

    public static String resolve(String materialName) {
        return resolve(materialName, LANGUAGE_RU);
    }

    public static String resolve(String materialName, String language) {
        if (materialName == null || materialName.isEmpty()) {
            return getDefaultName(language);
        }

        String normalized = materialName.toUpperCase(Locale.ROOT);
        if (isEnglish(language)) {
            return toReadableName(normalized);
        }

        String translatedName = RUSSIAN_NAMES.get(normalized);
        if (translatedName != null) {
            return translatedName;
        }

        return toReadableName(normalized);
    }

    private static String toReadableName(String value) {
        String[] words = value.toLowerCase(Locale.ROOT).split("_");
        StringBuilder fallbackName = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (fallbackName.length() > 0) {
                fallbackName.append(' ');
            }
            fallbackName.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return fallbackName.length() == 0 ? value : fallbackName.toString();
    }

    private static void register(String materialName, String russianName) {
        RUSSIAN_NAMES.put(materialName, russianName);
    }

    private static boolean isEnglish(String language) {
        if (language == null) {
            return false;
        }

        String normalized = language.trim().toUpperCase(Locale.ROOT);
        return LANGUAGE_EN.equals(normalized) || "ENG".equals(normalized) || "ENGLISH".equals(normalized);
    }

    private static String getDefaultName(String language) {
        if (isEnglish(language)) {
            return "Item";
        }
        return "Предмет";
    }
}
