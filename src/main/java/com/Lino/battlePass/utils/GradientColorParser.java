package com.Lino.battlePass.utils;

import net.md_5.bungee.api.ChatColor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GradientColorParser {
    private static final Pattern GRADIENT_PATTERN = Pattern.compile("<gradient:(#[A-Fa-f0-9]{6}):(#[A-Fa-f0-9]{6})>(.*?)</gradient>", Pattern.DOTALL);
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final boolean SUPPORTS_HEX = checkHexSupport();

    private static boolean checkHexSupport() {
        try {
            ChatColor.class.getDeclaredMethod("of", String.class);
            return true;
        } catch (NoSuchMethodException unavailable) {
            return false;
        }
    }

    public static String parse(String message) {
        if (message == null) return null;
        return ChatColor.translateAlternateColorCodes('&', parseHexColors(parseGradients(message)));
    }

    private static String parseGradients(String message) {
        if (!SUPPORTS_HEX) return GRADIENT_PATTERN.matcher(message).replaceAll("$3");
        Matcher matcher = GRADIENT_PATTERN.matcher(message);
        StringBuffer result = new StringBuffer();
        GradientText.Formatting formatting = new GradientText.Formatting();
        int previous = 0;
        while (matcher.find()) {
            GradientText.updateFormatting(message.substring(previous, matcher.start()), formatting);
            String text = GradientText.render(matcher.group(3), matcher.group(1), matcher.group(2), formatting);
            matcher.appendReplacement(result, Matcher.quoteReplacement(text));
            previous = matcher.end();
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String parseHexColors(String message) {
        if (!SUPPORTS_HEX) return HEX_PATTERN.matcher(message).replaceAll("");
        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) matcher.appendReplacement(result, Matcher.quoteReplacement(ChatColor.of("#" + matcher.group(1)).toString()));
        matcher.appendTail(result);
        return result.toString();
    }

    public static String stripColors(String message) {
        if (message == null) return null;
        return GradientText.stripControls(GRADIENT_PATTERN.matcher(message).replaceAll("$3"));
    }
}
