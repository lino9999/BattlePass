package com.Lino.battlePass.utils;

import java.math.BigInteger;

public final class VersionUtils {
    private VersionUtils() {
    }

    /** Compare the dotted numeric release versions used by BattlePass and Spigot. */
    public static boolean isNewer(String candidate, String installed) {
        String[] candidateParts = parse(candidate);
        String[] installedParts = parse(installed);
        if (candidateParts == null || installedParts == null) return false;

        for (int i = 0; i < Math.max(candidateParts.length, installedParts.length); i++) {
            BigInteger candidatePart = i < candidateParts.length ? new BigInteger(candidateParts[i]) : BigInteger.ZERO;
            BigInteger installedPart = i < installedParts.length ? new BigInteger(installedParts[i]) : BigInteger.ZERO;
            int comparison = candidatePart.compareTo(installedPart);
            if (comparison != 0) return comparison > 0;
        }
        return false;
    }

    private static String[] parse(String version) {
        if (version == null) return null;
        String normalized = version.trim().replaceFirst("^[vV]", "");
        return normalized.matches("[0-9]+(?:\\.[0-9]+)*") ? normalized.split("\\.") : null;
    }
}
