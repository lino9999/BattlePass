package com.Lino.battlePass.models;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EatMissionTargetTest {
    @ParameterizedTest
    @CsvSource({
            "GOLDEN_APPLE, GOLDEN_APPLE",
            "ENCHANTED_GOLDEN_APPLE, ENCHANTED_GOLDEN_APPLE",
            "Golden Apple, GOLDEN_APPLE",
            "Enchanted Golden Apple, ENCHANTED_GOLDEN_APPLE",
            "minecraft:golden_apple, GOLDEN_APPLE",
            "minecraft:enchanted_golden_apple, ENCHANTED_GOLDEN_APPLE",
            "' Golden Apple ', GOLDEN_APPLE",
            "enchanted_golden_apple, ENCHANTED_GOLDEN_APPLE"
    })
    void resolvesFoodMaterialNames(String configured, String consumed) {
        Mission mission = new Mission("Eat apples", "EAT_ITEM", configured, List.of(), 3, 100);
        assertTrue(mission.isValidTarget(consumed));
        assertEquals(configured, mission.target, "Matching must preserve the stored progress key");
    }

    @Test
    void resolvesAdditionalFoodTargetsWithoutCombiningDifferentApples() {
        Mission goldenOnly = new Mission("Golden", "EAT_ITEM", "Golden Apple", List.of(), 3, 100);
        assertFalse(goldenOnly.isValidTarget("ENCHANTED_GOLDEN_APPLE"));
        Mission enchantedOnly = new Mission("Enchanted", "EAT_ITEM", "Enchanted Golden Apple", List.of(), 3, 100);
        assertFalse(enchantedOnly.isValidTarget("GOLDEN_APPLE"));
        Mission both = new Mission("Both", "EAT_ITEM", "Golden Apple",
                List.of("minecraft:enchanted_golden_apple"), 3, 100);
        assertTrue(both.isValidTarget("GOLDEN_APPLE"));
        assertTrue(both.isValidTarget("ENCHANTED_GOLDEN_APPLE"));
        assertFalse(both.isValidTarget("APPLE"));
    }

    @Test
    void preservesWildcardAndCustomMobMatching() {
        assertTrue(new Mission("Any", "EAT_ITEM", "ANY", List.of(), 3, 100)
                .isValidTarget("ENCHANTED_GOLDEN_APPLE"));
        assertFalse(new Mission("Invalid", "EAT_ITEM", "not a material", List.of(), 3, 100)
                .isValidTarget("GOLDEN_APPLE"));
        Mission custom = new Mission("Boss", "KILL_MYTHIC_MOB", "Golden Apple", List.of(), 3, 100);
        assertTrue(custom.isValidTarget("golden apple"));
        assertFalse(custom.isValidTarget("GOLDEN_APPLE"));
    }
}
