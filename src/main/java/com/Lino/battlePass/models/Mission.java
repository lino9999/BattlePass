package com.Lino.battlePass.models;

public class Mission {
    public final String name;
    public final String type;
    public final String target;
    public final int required;
    public final int xpReward;

    public Mission(String name, String type, String target, int required, int xpReward) {
        this.name = name;
        this.type = type;
        this.target = target;
        this.required = required;
        this.xpReward = xpReward;
    }
}