package com.Lino.battlePass.models;

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
        if ("ANY".equals(target) || target.equalsIgnoreCase(actionTarget)) {
            return true;
        }
        return additionalTargets.stream().anyMatch(additional -> additional.equalsIgnoreCase(actionTarget));
    }
}