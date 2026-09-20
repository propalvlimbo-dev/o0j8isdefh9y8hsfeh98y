package ru.rooyzee.elytrixclans.utils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.command.CommandSender;
import ru.rooyzee.elytrixclans.Main;

public class ConfigUtil {

    /** Отправка сообщения игроку или в консоль — Player тоже является CommandSender. */
    public static void sendMessage(CommandSender receiver, String path, Map<String, String> placeholders) {
        if (receiver == null) return;
        Main main = Main.getInstance();
        if (main == null) return;
        List<String> messages = main.getConfig().getStringList(path);
        for (String message : messages) {
            if (placeholders != null) {
                for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                    message = message.replace(entry.getKey(), entry.getValue());
                }
            }
            receiver.sendMessage(HexUtil.translateHexColorCodes(message));
        }
    }

    public static Map<String, String> setHolder(String[] keys, String[] values) {
        Map<String, String> map = new HashMap<>();
        if (keys == null || values == null) return map;
        for (int i = 0; i < keys.length; i++) {
            map.put(keys[i], i < values.length ? values[i] : "");
        }
        return map;
    }

    public static String getString(String path) {
        Main main = Main.getInstance();
        if (main == null) return "";
        return HexUtil.translateHexColorCodes(main.getConfig().getString(path, ""));
    }
}