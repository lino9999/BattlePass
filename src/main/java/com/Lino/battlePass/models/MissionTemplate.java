package com.Lino.battlePass.models;

public class MissionTemplate {
    public final String nameFormat;
    public final String type;
    public final String target;
    public final int minRequired;
    public final int maxRequired;
    public final int minXP;
    public final int maxXP;
    public final String world;
    public final String region;

    public MissionTemplate(String nameFormat, String type, String target, int minRequired,
                           int maxRequired, int minXP, int maxXP) {
        this(nameFormat, type, target, minRequired, maxRequired, minXP, maxXP, null, null);
    }

    public MissionTemplate(String nameFormat, String type, String target, int minRequired,
                           int maxRequired, int minXP, int maxXP, String world, String region) {
        this.nameFormat = nameFormat;
        this.type = type;
        this.target = target;
        this.minRequired = minRequired;
        this.maxRequired = maxRequired;
        this.minXP = minXP;
        this.maxXP = maxXP;
        this.world = world;
        this.region = region;
    }
}