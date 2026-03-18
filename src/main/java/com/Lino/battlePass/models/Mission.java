package com.Lino.battlePass.models;

public class Mission {
    public final String name;
    public final String type;
    public final String target;
    public final int required;
    public final int xpReward;
    public final String world;   // null = any world
    public final String region;  // null = any region

    public Mission(String name, String type, String target, int required, int xpReward) {
        this(name, type, target, required, xpReward, null, null);
    }

    public Mission(String name, String type, String target, int required, int xpReward, String world, String region) {
        this.name = name;
        this.type = type;
        this.target = target;
        this.required = required;
        this.xpReward = xpReward;
        this.world = world;
        this.region = region;
    }
}