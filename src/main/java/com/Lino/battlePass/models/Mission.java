package com.Lino.battlePass.models;

import org.bukkit.Material;

import java.util.List;
import java.util.ArrayList;

public class Mission {
    public final String name;
    public final String type;
    public final String target;
    public final List<String> additionalTargets;
    public final int required;
    public final int xpReward;

    public Mission(String name, String type, String target, List<String> additionalTargets, int required, int xpReward) {
        this.name = name;
        this.type = type;
        this.target = target;
        this.additionalTargets = additionalTargets != null ? additionalTargets : new ArrayList<>();
        this.required = required;
        this.xpReward = xpReward;
    }

    public boolean isValidTarget(String actionTarget) {
        if (target == null || "ANY".equals(target) || matchesTarget(target, actionTarget)) {
            return true;
        }
        return additionalTargets.stream()
                .filter(java.util.Objects::nonNull)
                .anyMatch(additional -> matchesTarget(additional, actionTarget));
    }

    private boolean matchesTarget(String configuredTarget, String actionTarget) {
        if (configuredTarget.equalsIgnoreCase(actionTarget)) {
            return true;
        }
        boolean materialTarget = switch (type == null ? "" : type) {
            case "EAT_ITEM", "BREAK_BLOCK", "MINE_BLOCK", "PLACE_BLOCK", "CRAFT_ITEM",
                    "SMELT_ITEM", "ENCHANT_ITEM", "FISH_ITEM" -> true;
            default -> false;
        };
        if (!materialTarget || actionTarget == null) {
            return false;
        }

        // Resolve material names at comparison time so persisted progress keys stay unchanged.
        Material configuredMaterial = Material.matchMaterial(configuredTarget.trim());
        return configuredMaterial != null
                && configuredMaterial == Material.matchMaterial(actionTarget.trim());
    }
}
