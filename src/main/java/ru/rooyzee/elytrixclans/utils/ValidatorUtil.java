package ru.rooyzee.elytrixclans.utils;

import java.util.regex.Pattern;
import ru.rooyzee.elytrixclans.Main;

public class ValidatorUtil {

    private static final Pattern HEX_COLOR = Pattern.compile("&#[A-Fa-f0-9]{6}");
    private static final Pattern MC_COLOR = Pattern.compile("&[0-9a-fk-orA-FK-OR]");

    public static String getValidClanName(String rawName) {
        String name = removeAllColors(rawName);
        Main main = Main.getInstance();
        String allowed = main != null
                ? main.getConfig().getString("nameAllowedChars", "")
                : "";
        if (allowed == null) allowed = "";
        // Разрешённые символы держим в множестве: на каждый символ был линейный поиск по строке,
        // а проверка вызывается в том числе на каждый /clan info <название>.
        for (char c : name.toCharArray()) {
            if (allowed.indexOf(Character.toLowerCase(c)) < 0) {
                return null;
            }
        }
        return name;
    }

    public static String removeAllColors(String text) {
        if (text == null) return "";
        String result = text.replace('§', '&');
        result = HEX_COLOR.matcher(result).replaceAll("");
        result = MC_COLOR.matcher(result).replaceAll("");
        return result.trim();
    }
}