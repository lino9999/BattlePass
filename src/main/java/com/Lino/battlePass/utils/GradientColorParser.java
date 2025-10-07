package com.Lino.battlePass.utils;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;

import java.awt.Color;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GradientColorParser {

    private static final Pattern GRADIENT_PATTERN = Pattern.compile("<gradient:(#[A-Fa-f0-9]{6}):(#[A-Fa-f0-9]{6})>(.*?)</gradient>");
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    private static final boolean SUPPORTS_HEX = checkHexSupport();

    private static boolean checkHexSupport() {
        try {
            ChatColor.class.getDeclaredMethod("of", String.class);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    public static String parse(String message) {
        if (message == null) return null;

        message = parseGradients(message);
        message = parseHexColors(message);

        return ChatColor.translateAlternateColorCodes('&', message);
    }

    private static String parseGradients(String message) {
        if (!SUPPORTS_HEX) {
            return message.replaceAll("<gradient:#[A-Fa-f0-9]{6}:#[A-Fa-f0-9]{6}>(.*?)</gradient>", "$1");
        }

        Matcher matcher = GRADIENT_PATTERN.matcher(message);
        StringBuffer buffer = new StringBuffer();

        while (matcher.find()) {
            String startHex = matcher.group(1);
            String endHex = matcher.group(2);
            String text = matcher.group(3);

            String gradientText = applyGradient(text, startHex, endHex);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(gradientText));
        }

        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private static String parseHexColors(String message) {
        if (!SUPPORTS_HEX) {
            return message.replaceAll("&#[A-Fa-f0-9]{6}", "");
        }

        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuffer buffer = new StringBuffer();

        while (matcher.find()) {
            String hex = matcher.group(1);
            String replacement = ChatColor.of("#" + hex).toString();
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }

        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private static String applyGradient(String text, String startHex, String endHex) {
        if (text == null || text.isEmpty()) return text;

        Color startColor = hexToColor(startHex);
        Color endColor = hexToColor(endHex);
        StringBuilder result = new StringBuilder();

        String strippedText = stripColors(text);
        int length = strippedText.length();
        int colorIndex = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' || c == '&') {
                if (i + 1 < text.length()) {
                    result.append(c).append(text.charAt(i + 1));
                    i++;
                } else {
                    result.append(c);
                }
            } else {
                if (Character.isWhitespace(c)) {
                    result.append(c);
                    continue;
                }

                float ratio = length <= 1 ? 0.5f : (float) colorIndex / (float) (length - 1);
                Color currentColor = interpolateColor(startColor, endColor, ratio);
                String hexColor = String.format("#%02x%02x%02x",
                        currentColor.getRed(),
                        currentColor.getGreen(),
                        currentColor.getBlue());

                result.append(ChatColor.of(hexColor)).append(c);
                colorIndex++;
            }
        }
        return result.toString();
    }

    private static Color hexToColor(String hex) {
        return Color.decode(hex);
    }

    private static Color interpolateColor(Color start, Color end, float ratio) {
        int red = (int) (start.getRed() + ratio * (end.getRed() - start.getRed()));
        int green = (int) (start.getGreen() + ratio * (end.getGreen() - start.getGreen()));
        int blue = (int) (start.getBlue() + ratio * (end.getBlue() - start.getBlue()));

        return new Color(red, green, blue);
    }

    public static String stripColors(String message) {
        if (message == null) return null;

        message = message.replaceAll("<gradient:#[A-Fa-f0-9]{6}:#[A-Fa-f0-9]{6}>(.*?)</gradient>", "$1");
        message = message.replaceAll("&#[A-Fa-f0-9]{6}", "");

        return ChatColor.stripColor(message);
    }
}