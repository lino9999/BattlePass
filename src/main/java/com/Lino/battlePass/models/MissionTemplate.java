package com.Lino.battlePass.models;

public class MissionTemplate {
    public final String nameFormat;
    public final String type;
    public final String target;
    public final int minRequired;
    public final int maxRequired;
    public final int minXP;
    public final int maxXP;

    public MissionTemplate(String nameFormat, String type, String target, int minRequired,
                           int maxRequired, int minXP, int maxXP) {
        this.nameFormat = nameFormat;
        this.type = type;
        this.target = target;
        this.minRequired = minRequired;
        this.maxRequired = maxRequired;
        this.minXP = minXP;
        this.maxXP = maxXP;
    }
}