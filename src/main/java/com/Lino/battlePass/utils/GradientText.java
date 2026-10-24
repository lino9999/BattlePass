package com.Lino.battlePass.utils;

import net.md_5.bungee.api.ChatColor;
import java.awt.Color;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Visible-grapheme rendering with legacy formatting carried across inserted RGB color codes. */
final class GradientText {
    private static final String CONTROL = "(?:[&§]x(?:[&§][0-9a-f]){6}|&#[0-9a-f]{6}|[&§][0-9a-fk-orx])";
    private static final Pattern CONTROLS = Pattern.compile(CONTROL, Pattern.CASE_INSENSITIVE);
    private static final Pattern TOKENS = Pattern.compile("(" + CONTROL + ")|(\\X)", Pattern.CASE_INSENSITIVE);
    private static final String STYLES = "klmno";

    private GradientText() {}

    static String stripControls(String text) { return CONTROLS.matcher(text).replaceAll(""); }

    static void updateFormatting(String text, Formatting formatting) {
        Matcher controls = CONTROLS.matcher(text);
        while (controls.find()) formatting.accept(controls.group());
    }

    static String render(String text, String startHex, String endHex, Formatting formatting) {
        Matcher tokens = TOKENS.matcher(text);
        int length = 0;
        while (tokens.find()) if (tokens.group(1) == null && !whitespace(tokens.group(2))) length++;
        Color start = Color.decode(startHex);
        Color end = Color.decode(endHex);
        StringBuilder result = new StringBuilder(text.length());
        tokens.reset();
        int index = 0;
        while (tokens.find()) {
            String control = tokens.group(1);
            if (control != null) {
                formatting.accept(control);
                result.append(control);
                continue;
            }
            String glyph = tokens.group(2);
            if (!whitespace(glyph)) {
                float ratio = length <= 1 ? 0.5f : (float) index / (length - 1);
                Color color = new Color(
                        (int) (start.getRed() + ratio * (end.getRed() - start.getRed())),
                        (int) (start.getGreen() + ratio * (end.getGreen() - start.getGreen())),
                        (int) (start.getBlue() + ratio * (end.getBlue() - start.getBlue())));
                result.append(ChatColor.of(color));
                formatting.appendTo(result);
                index++;
            }
            result.append(glyph);
        }
        return result.toString();
    }

    private static boolean whitespace(String glyph) {
        return glyph.codePoints().allMatch(value -> Character.isWhitespace(value) || Character.isSpaceChar(value));
    }

    static final class Formatting {
        private int styles;
        private void accept(String control) {
            int style = STYLES.indexOf(Character.toLowerCase(control.charAt(1)));
            if (style < 0) styles = 0;
            else styles |= 1 << style;
        }
        private void appendTo(StringBuilder result) {
            for (int i = 0; i < STYLES.length(); i++) if ((styles & (1 << i)) != 0) result.append('§').append(STYLES.charAt(i));
        }
    }
}
