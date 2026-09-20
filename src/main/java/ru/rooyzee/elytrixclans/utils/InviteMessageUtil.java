package ru.rooyzee.elytrixclans.utils;

import java.util.List;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;
import ru.rooyzee.elytrixclans.Main;

/**
 * Кликабельное приглашение в клан.
 *
 * Обычный sendMessage печатает только текст, поэтому под приглашением добавляется строка
 * с двумя кнопками: «ПРИНЯТЬ» выполняет /clan accept, «ОТКЛОНИТЬ» — /clan decline.
 * Команды те же, что игрок мог бы ввести руками, так что все проверки прав и сроков
 * приглашения остаются на своих местах.
 *
 * Тексты берутся из config.yml (inviteAcceptButton / inviteDeclineButton и подсказки),
 * чтобы их можно было перевести или перекрасить без пересборки плагина.
 */
public final class InviteMessageUtil {

    private InviteMessageUtil() {
    }

    /**
     * Отправляет приглашение: сначала обычные строки messages.targetInvited,
     * затем строку с кнопками.
     */
    public static void sendInvite(Player target, String inviterName, String clanName) {
        if (target == null) return;
        ConfigUtil.sendMessage(target, "messages.targetInvited",
                ConfigUtil.setHolder(new String[]{"%player%", "%clan%"},
                        new String[]{inviterName, clanName}));
        target.spigot().sendMessage(buildButtons());
    }

    private static BaseComponent[] buildButtons() {
        String prefix = string("messages.inviteButtonsPrefix",
                "&f☁ &7» ");
        String acceptText = string("messages.inviteAcceptButton", "&a&l[ПРИНЯТЬ]");
        String declineText = string("messages.inviteDeclineButton", "&c&l[ОТКЛОНИТЬ]");
        String acceptHover = string("messages.inviteAcceptHover", "&aВступить в клан");
        String declineHover = string("messages.inviteDeclineHover", "&cОтказаться от приглашения");

        ComponentBuilder builder = new ComponentBuilder("");
        builder.append(components(prefix), ComponentBuilder.FormatRetention.NONE);
        builder.append(button(acceptText, acceptHover, "/clan accept"),
                ComponentBuilder.FormatRetention.NONE);
        builder.append(components("  "), ComponentBuilder.FormatRetention.NONE);
        builder.append(button(declineText, declineHover, "/clan decline"),
                ComponentBuilder.FormatRetention.NONE);
        return builder.create();
    }

    private static BaseComponent[] button(String label, String hover, String command) {
        BaseComponent[] parts = components(label);
        ClickEvent click = new ClickEvent(ClickEvent.Action.RUN_COMMAND, command);
        // Подсказка при наведении собирается тем же конвертером, что и сам текст,
        // поэтому в ней работают и обычные, и hex-цвета.
        HoverEvent hoverEvent = new HoverEvent(HoverEvent.Action.SHOW_TEXT, components(hover));
        for (BaseComponent part : parts) {
            part.setClickEvent(click);
            part.setHoverEvent(hoverEvent);
        }
        return parts;
    }

    private static BaseComponent[] components(String legacyText) {
        return TextComponent.fromLegacyText(HexUtil.translateHexColorCodes(legacyText));
    }

    /**
     * Строка из конфига. Поддерживает и обычное значение, и список
     * (тогда берётся первая строка) — чтобы формат совпадал с остальными messages.*.
     */
    private static String string(String path, String fallback) {
        Main main = Main.getInstance();
        if (main == null) return fallback;
        if (main.getConfig().isList(path)) {
            List<String> list = main.getConfig().getStringList(path);
            if (!list.isEmpty()) return list.get(0);
            return fallback;
        }
        return main.getConfig().getString(path, fallback);
    }
}
