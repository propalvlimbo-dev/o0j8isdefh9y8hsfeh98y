package ru.rooyzee.elytrixtalisman.util;

import java.util.Map;

public class PlaceholderUtil {

    public static String replace(String text, Map<String, String> placeholders) {
        if (text == null) return "";
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            text = text.replace(entry.getKey(), entry.getValue());
        }
        return text;
    }
}