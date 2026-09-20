package ru.rooyzee.elytrixclans.command.player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.rooyzee.elytrixclans.Main;
import ru.rooyzee.elytrixclans.clans.Clan;
import ru.rooyzee.elytrixclans.clans.ClanMember;
import ru.rooyzee.elytrixclans.function.impl.confirm.DisbandConfirmManager;
import ru.rooyzee.elytrixclans.function.impl.glow.GlowFunction;
import ru.rooyzee.elytrixclans.function.impl.info.InfoFunction;
import ru.rooyzee.elytrixclans.function.impl.invite.Invite;
import ru.rooyzee.elytrixclans.function.impl.invite.InviteManager;
import ru.rooyzee.elytrixclans.function.impl.sethome.SetHomeFunction;
import ru.rooyzee.elytrixclans.function.impl.shop.ShopFunction;
import ru.rooyzee.elytrixclans.hook.impl.VaultHook;
import ru.rooyzee.elytrixclans.permission.Permissions;
import ru.rooyzee.elytrixclans.role.ClanRoles;
import ru.rooyzee.elytrixclans.utils.ConfigUtil;
import ru.rooyzee.elytrixclans.utils.HexUtil;
import ru.rooyzee.elytrixclans.utils.InviteMessageUtil;
import ru.rooyzee.elytrixclans.utils.LevelUtil;
import ru.rooyzee.elytrixclans.utils.ValidatorUtil;

public class ClanCommand implements CommandExecutor, TabCompleter {

    private final List<String> COMPLETES = Arrays.asList(
            "shop", "chat", "pvp", "menu", "accept", "decline", "home", "promote",
            "demote", "kick", "leave", "sethome", "delhome", "create",
            "invite", "disband", "glow", "info");

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return false;
        Player player = (Player) sender;

        if (args.length == 0) {
            ConfigUtil.sendMessage(player, "messages.usage", null);
            return false;
        }

        if (!COMPLETES.contains(args[0].toLowerCase())) {
            ConfigUtil.sendMessage(player, "messages.uncorrectUsage", null);
            return false;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("info")) return handleInfo(player, args);
        if (sub.equals("create")) return handleCreate(player, args);
        if (sub.equals("accept")) return handleAccept(player);
        if (sub.equals("decline") || sub.equals("deny")) return handleDecline(player);

        Clan clan = Main.getInstance().getClanManager().getPlayerClan(player);
        if (clan == null) {
            ConfigUtil.sendMessage(player, "messages.withoutClan", null);
            return false;
        }

        ClanMember member = Main.getInstance().getClanManager().getPlayerClanMember(player);
        if (member == null) {
            ConfigUtil.sendMessage(player, "messages.withoutClan", null);
            return false;
        }

        switch (sub) {
            case "leave": return handleLeave(player, clan, member);
            case "chat": return handleChat(player, clan, args);
            case "menu": player.openInventory(new InfoFunction(clan, player).getInventory()); return false;
            case "home": return handleHome(player, clan);
            case "invite": return handleInvite(player, clan, member, args);
            case "kick": return handleKick(player, clan, member, args);
            case "pvp": return handlePvp(player, clan, member);
            case "glow": player.openInventory(new GlowFunction().getInventory()); return false;
            case "sethome": return handleSetHome(player, clan, member);
            case "delhome": return handleDelHome(player, clan, member);
            case "shop": player.openInventory(new ShopFunction(player).getInventory()); return false;
            case "promote": return handlePromote(player, clan, member, args);
            case "demote": return handleDemote(player, clan, member, args);
            case "disband": return handleDisband(player, clan, member);
            default: return false;
        }
    }

    private boolean handleInfo(Player player, String[] args) {
        if (args.length == 1) {
            Clan clan = Main.getInstance().getClanManager().getPlayerClan(player);
            // Свой клан открываем с панелью действий, чужой — в режиме просмотра.
            if (clan != null) player.openInventory(new InfoFunction(clan, player).getInventory());
            return false;
        }
        if (args[1].length() > 32) {
            // Не гоняем валидацию по абсурдно длинному вводу.
            ConfigUtil.sendMessage(player, "messages.clanNotFound", null);
            return false;
        }
        Clan target = Main.getInstance().getClanManager().getClanByName(args[1]);
        if (target == null) ConfigUtil.sendMessage(player, "messages.clanNotFound", null);
        else player.openInventory(new InfoFunction(target).getInventory());
        return false;
    }

    private boolean handleCreate(Player player, String[] args) {
        if (args.length != 2) {
            ConfigUtil.sendMessage(player, "messages.createUsage", null);
            return false;
        }
        if (Main.getInstance().getClanManager().getPlayerClan(player) != null) {
            ConfigUtil.sendMessage(player, "messages.alreadyInClan", null);
            return false;
        }
        if (args[1].length() > 32) {
            // Не гоняем валидацию и проверки по абсурдно длинному вводу.
            ConfigUtil.sendMessage(player, "messages.unvalidName", null);
            return false;
        }
        String clanName = ValidatorUtil.getValidClanName(args[1]);
        if (clanName == null) {
            ConfigUtil.sendMessage(player, "messages.unvalidName", null);
            return false;
        }
        int minLen = Main.getInstance().getConfig().getInt("clan_name_min_length", 3);
        int maxLen = Main.getInstance().getConfig().getInt("clan_name_max_length", 6);
        if (clanName.length() < minLen || clanName.length() > maxLen) {
            ConfigUtil.sendMessage(player, "messages.unvalidLength",
                    ConfigUtil.setHolder(new String[]{"%min_length%", "%max_length%"},
                            new String[]{String.valueOf(minLen), String.valueOf(maxLen)}));
            return false;
        }
        if (Main.getInstance().getClanManager().getClanByName(clanName) != null) {
            ConfigUtil.sendMessage(player, "messages.alreadyCreated", null);
            return false;
        }
        List<?> blocked = Main.getInstance().getConfig().getList("blockedNames");
        if (blocked != null) {
            for (Object obj : blocked) {
                if (obj instanceof String && ((String) obj).equalsIgnoreCase(clanName)) {
                    ConfigUtil.sendMessage(player, "messages.blockedName", null);
                    return false;
                }
            }
        }
        if (VaultHook.getEconomy() == null) {
            // Vault есть, но экономический плагин не подключился: без проверки было NPE.
            player.sendMessage(HexUtil.translateHexColorCodes(
                    "&f☁ &#F8BEFBᴇ&#F6BEFBʟ&#F3BEFBʏ&#F1BFFBᴛ&#EEBFFBʀ&#ECBFFBɪ&#E9BFFBx &7» &cЭкономика недоступна, попробуйте позже"));
            return false;
        }
        int basePrice = Math.max(0, Main.getInstance().getConfig().getInt("clan_create_price", 50000));
        int price = player.hasPermission("elytrixclans.discount") ? basePrice / 2 : basePrice;
        if (VaultHook.getEconomy().getBalance(player) < price) {
            ConfigUtil.sendMessage(player, "messages.noMoney",
                    ConfigUtil.setHolder(new String[]{"%price%"}, new String[]{String.valueOf(price)}));
            return false;
        }
        net.milkbowl.vault.economy.EconomyResponse response =
                VaultHook.getEconomy().withdrawPlayer(player, price);
        if (response == null || !response.transactionSuccess()) {
            // Списание не прошло (лимит плагина экономики, ошибка БД) — клан не создаём,
            // иначе он достался бы бесплатно.
            ConfigUtil.sendMessage(player, "messages.noMoney",
                    ConfigUtil.setHolder(new String[]{"%price%"}, new String[]{String.valueOf(price)}));
            return false;
        }
        try {
            Main.getInstance().getClanManager().createClan(clanName, player);
        } catch (Exception e) {
            // Создание клана упало — возвращаем деньги, иначе игрок платит за несуществующий клан.
            VaultHook.getEconomy().depositPlayer(player, price);
            Main.getInstance().getLogger().warning("Не удалось создать клан " + clanName + ": " + e.getMessage());
            player.sendMessage(HexUtil.translateHexColorCodes(
                    "&f☁ &7» &cНе удалось создать клан, деньги возвращены на баланс"));
            return false;
        }
        ConfigUtil.sendMessage(player, "messages.createdSuccessfully",
                ConfigUtil.setHolder(new String[]{"%clan%"}, new String[]{clanName}));
        return false;
    }

    private boolean handleAccept(Player player) {
        if (Main.getInstance().getClanManager().getPlayerClan(player) != null) {
            ConfigUtil.sendMessage(player, "messages.alreadyInClan", null);
            return false;
        }
        if (!InviteManager.hasInvite(player.getName())) {
            ConfigUtil.sendMessage(player, "messages.withoutInvite", null);
            return false;
        }
        Invite invite = InviteManager.getInvite(player.getName());
        Clan clan = Main.getInstance().getClanManager().getClanByName(invite.getClanName());
        if (clan == null) {
            InviteManager.removeInvites(player.getName());
            ConfigUtil.sendMessage(player, "messages.clanDeleted", null);
            return false;
        }
        if (clan.getMemberList().size() >= LevelUtil.getClanLevel(clan.getExp()).getMaxMembers()) {
            InviteManager.removeInvites(player.getName());
            ConfigUtil.sendMessage(player, "messages.playersLimit", null);
            return false;
        }
        InviteManager.removeInvites(player.getName());
        Main.getInstance().getClanManager().addPlayer(clan, player);
        for (ClanMember m : clan.getMemberList()) {
            if (m.getPlayer() != null && m.getPlayer().isOnline()) {
                ConfigUtil.sendMessage(m.getPlayer(), "messages.newMember",
                        ConfigUtil.setHolder(new String[]{"%player%"}, new String[]{player.getName()}));
            }
        }
        return false;
    }

    /** Отказ от приглашения — вторая кнопка под сообщением о приглашении. */
    private boolean handleDecline(Player player) {
        if (!InviteManager.hasInvite(player.getName())) {
            ConfigUtil.sendMessage(player, "messages.withoutInvite", null);
            return false;
        }
        Invite invite = InviteManager.getInvite(player.getName());
        InviteManager.removeInvites(player.getName());
        ConfigUtil.sendMessage(player, "messages.inviteDeclined", null);

        // Пригласившему сообщаем об отказе, если он ещё в сети.
        if (invite != null) {
            Player inviter = Bukkit.getPlayerExact(invite.getInviter());
            if (inviter != null && inviter.isOnline()) {
                ConfigUtil.sendMessage(inviter, "messages.inviteDeclinedByTarget",
                        ConfigUtil.setHolder(new String[]{"%player%"},
                                new String[]{player.getName()}));
            }
        }
        return false;
    }

    private boolean handleLeave(Player player, Clan clan, ClanMember member) {
        if (ClanRoles.LEADER.equals(ClanRoles.normalize(member.getRole().getName()))) {
            ConfigUtil.sendMessage(player, "messages.leaderCantLeave", null);
            return false;
        }
        Main.getInstance().getClanManager().kickPlayer(clan, player);
        ConfigUtil.sendMessage(player, "messages.leaveClan",
                ConfigUtil.setHolder(new String[]{"%player%"}, new String[]{player.getName()}));
        for (ClanMember m : clan.getMemberList()) {
            if (m.getPlayer() != null && m.getPlayer().isOnline()) {
                ConfigUtil.sendMessage(m.getPlayer(), "messages.playerLeft",
                        ConfigUtil.setHolder(new String[]{"%player%"}, new String[]{player.getName()}));
            }
        }
        return false;
    }

    /** Антифлуд клан-чата: когда игроку в последний раз разрешили отправку. */
    private static final Map<UUID, Long> CHAT_COOLDOWN = new ConcurrentHashMap<>();
    private static final long CHAT_COOLDOWN_MS = 1000L;
    private static final int CHAT_MAX_LENGTH = 200;

    private boolean handleChat(Player player, Clan clan, String[] args) {
        if (args.length == 1) return false;

        // Антифлуд: один макрос-клик не должен разливать сотни строк по клану.
        long now = System.currentTimeMillis();
        Long next = CHAT_COOLDOWN.get(player.getUniqueId());
        if (next != null && next > now && !player.hasPermission("elytrixclans.admin")) return false;
        CHAT_COOLDOWN.put(player.getUniqueId(), now + CHAT_COOLDOWN_MS);
        if (CHAT_COOLDOWN.size() > 500) {
            CHAT_COOLDOWN.values().removeIf(value -> value == null || value <= now);
        }

        String msg = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        // Режем цветовые коды и §-секции: иначе любой участник красит чат и подделывает
        // чужой префикс. Право elytrixclans.chatcolor возвращает цвета доверенным игрокам.
        if (!player.hasPermission("elytrixclans.chatcolor")) {
            msg = msg.replace('\u00a7', ' ').replace('&', ' ');
        }
        msg = msg.replace('\n', ' ').replace('\r', ' ').trim();
        if (msg.isEmpty()) return false;
        if (msg.length() > CHAT_MAX_LENGTH) msg = msg.substring(0, CHAT_MAX_LENGTH);

        String line = HexUtil.translateHexColorCodes(
                "&#F8BEFB✦ &f" + player.getName() + " &7» &f") + msg;
        for (ClanMember m : clan.getMemberList()) {
            Player recipient = m.getPlayer();
            if (recipient != null && recipient.isOnline()) {
                recipient.sendMessage(line);
            }
        }
        return false;
    }

    private boolean handleHome(Player player, Clan clan) {
        if (clan.getHome() == null) {
            ConfigUtil.sendMessage(player, "messages.withoutHome", null);
            return false;
        }
        World homeWorld = clan.getHome().getWorld() != null ? Bukkit.getWorld(clan.getHome().getWorld()) : null;
        if (homeWorld == null) {
            // new Location(null, ...) и teleport() = исключение в консоли на каждую попытку.
            ConfigUtil.sendMessage(player, "messages.withoutHome", null);
            return false;
        }
        Location loc = new Location(
                homeWorld,
                clan.getHome().getX(), clan.getHome().getY(), clan.getHome().getZ(),
                (float) clan.getHome().getYaw(), (float) clan.getHome().getPitch());
        player.teleport(loc);
        return false;
    }

    private boolean handleInvite(Player player, Clan clan, ClanMember member, String[] args) {
        if (args.length != 2) {
            ConfigUtil.sendMessage(player, "messages.inviteUsage", null);
            return false;
        }
        if (!member.getRole().getPermissions().contains(Permissions.INVITE)) {
            ConfigUtil.sendMessage(player, "messages.noPermission", null);
            return false;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null || !target.isOnline()) {
            ConfigUtil.sendMessage(player, "messages.playerNotFound", null);
            return false;
        }
        if (target.getName().equalsIgnoreCase(player.getName())) {
            ConfigUtil.sendMessage(player, "messages.notDoYourself", null);
            return false;
        }
        if (Main.getInstance().getClanManager().getPlayerClan(target) != null) {
            ConfigUtil.sendMessage(player, "messages.targetInClan", null);
            return false;
        }
        InviteManager.addInvite(target.getName(), player.getName(), clan.getName());
        // Приглашение приходит с кнопками ПРИНЯТЬ / ОТКЛОНИТЬ.
        InviteMessageUtil.sendInvite(target, player.getName(), clan.getName());
        ConfigUtil.sendMessage(player, "messages.successInvite",
                ConfigUtil.setHolder(new String[]{"%player%"}, new String[]{target.getName()}));
        return false;
    }

    private boolean handleKick(Player player, Clan clan, ClanMember member, String[] args) {
        if (args.length != 2) {
            ConfigUtil.sendMessage(player, "messages.kickUsage", null);
            return false;
        }
        if (!member.getRole().getPermissions().contains(Permissions.KICK)) {
            ConfigUtil.sendMessage(player, "messages.noPermission", null);
            return false;
        }
        String targetName = args[1];
        if (targetName.equalsIgnoreCase(player.getName())) {
            ConfigUtil.sendMessage(player, "messages.notDoYourself", null);
            return false;
        }
        if (targetName.equalsIgnoreCase(clan.getOwner())) {
            ConfigUtil.sendMessage(player, "messages.notAccess", null);
            return false;
        }
        Clan targetClan = Main.getInstance().getClanManager().getPlayerClan(targetName);
        if (targetClan != clan) {
            ConfigUtil.sendMessage(player, "messages.playerNotFound", null);
            return false;
        }
        ClanMember targetMember = Main.getInstance().getClanManager().getPlayerClanMember(targetName);
        if (targetMember == null) {
            ConfigUtil.sendMessage(player, "messages.playerNotFound", null);
            return false;
        }
        Main.getInstance().getClanManager().kickPlayer(clan, targetMember);
        if (targetMember.getPlayer() != null && targetMember.getPlayer().isOnline()) {
            ConfigUtil.sendMessage(targetMember.getPlayer(), "messages.youKicked",
                    ConfigUtil.setHolder(new String[]{"%player%"}, new String[]{player.getName()}));
        }
        for (ClanMember m : clan.getMemberList()) {
            if (m.getPlayer() != null && m.getPlayer().isOnline()) {
                ConfigUtil.sendMessage(m.getPlayer(), "messages.playerKicked",
                        ConfigUtil.setHolder(new String[]{"%player%", "%target%"},
                                new String[]{player.getName(), targetName}));
            }
        }
        return true;
    }

    private boolean handlePvp(Player player, Clan clan, ClanMember member) {
        if (!member.getRole().getPermissions().contains(Permissions.PVP)) {
            ConfigUtil.sendMessage(player, "messages.noPermission", null);
            return false;
        }
        boolean wasPvpEnabled = clan.isPvp();
        clan.setPvp(!wasPvpEnabled);
        String key = wasPvpEnabled ? "messages.clan_pvp_disable" : "messages.clan_pvp_enable";
        for (ClanMember m : clan.getMemberList()) {
            if (m.getPlayer() != null && m.getPlayer().isOnline()) {
                ConfigUtil.sendMessage(m.getPlayer(), key, null);
            }
        }
        if (Main.getInstance().getGlowManager() != null) {
            if (!wasPvpEnabled) {
                Main.getInstance().getGlowManager().disableGlow(clan);
            } else if (Main.getInstance().getGlowManager().hasGlow(clan)) {
                Main.getInstance().getGlowManager().enableGlow(clan);
            }
        }
        return false;
    }

    private boolean handleSetHome(Player player, Clan clan, ClanMember member) {
        if (!member.getRole().getPermissions().contains(Permissions.SETHOME)) {
            ConfigUtil.sendMessage(player, "messages.noPermission", null);
            return false;
        }
        SetHomeFunction.setClanHome(player);
        ConfigUtil.sendMessage(player, "messages.homeSetted",
                ConfigUtil.setHolder(new String[]{"%player%"}, new String[]{player.getName()}));
        return false;
    }

    private boolean handleDelHome(Player player, Clan clan, ClanMember member) {
        if (!member.getRole().getPermissions().contains(Permissions.SETHOME)) {
            ConfigUtil.sendMessage(player, "messages.noPermission", null);
            return false;
        }
        SetHomeFunction.deleteClanHome(player);
        ConfigUtil.sendMessage(player, "messages.homeDeleted",
                ConfigUtil.setHolder(new String[]{"%player%"}, new String[]{player.getName()}));
        return false;
    }

    private boolean handlePromote(Player player, Clan clan, ClanMember member, String[] args) {
        if (args.length != 2) {
            ConfigUtil.sendMessage(player, "messages.promoteUsage", null);
            return false;
        }
        if (!member.getRole().getPermissions().contains(Permissions.PROMOTE)) {
            ConfigUtil.sendMessage(player, "messages.noPermission", null);
            return false;
        }
        String targetName = args[1];
        if (targetName.equalsIgnoreCase(player.getName())) {
            ConfigUtil.sendMessage(player, "messages.notDoYourself", null);
            return false;
        }
        // Без этой проверки лидер одного клана мог повышать/понижать участников ЧУЖИХ кланов.
        if (Main.getInstance().getClanManager().getPlayerClan(targetName) != clan) {
            ConfigUtil.sendMessage(player, "messages.playerNotFound", null);
            return false;
        }
        ClanMember targetMember = Main.getInstance().getClanManager().getPlayerClanMember(targetName);
        if (targetMember == null) {
            ConfigUtil.sendMessage(player, "messages.playerNotFound", null);
            return false;
        }
        if (ClanRoles.isManual(targetMember.getRole().getName())) {
            ConfigUtil.sendMessage(player, "messages.alreadyHasPromote", null);
            return false;
        }
        // Модератора назначает только владелец клана.
        if (!player.getName().equalsIgnoreCase(clan.getOwner())) {
            ConfigUtil.sendMessage(player, "messages.onlyOwnerRole", null);
            return false;
        }
        targetMember.setRole(ClanRoles.create(ClanRoles.MODERATOR));
        ConfigUtil.sendMessage(player, "messages.promoted",
                ConfigUtil.setHolder(new String[]{"%player%"}, new String[]{targetName}));
        return false;
    }

    private boolean handleDemote(Player player, Clan clan, ClanMember member, String[] args) {
        if (args.length != 2) {
            ConfigUtil.sendMessage(player, "messages.demoteUsage", null);
            return false;
        }
        if (!member.getRole().getPermissions().contains(Permissions.DEMOTE)) {
            ConfigUtil.sendMessage(player, "messages.noPermission", null);
            return false;
        }
        String targetName = args[1];
        if (targetName.equalsIgnoreCase(player.getName())) {
            ConfigUtil.sendMessage(player, "messages.notDoYourself", null);
            return false;
        }
        // Без этой проверки лидер одного клана мог повышать/понижать участников ЧУЖИХ кланов.
        if (Main.getInstance().getClanManager().getPlayerClan(targetName) != clan) {
            ConfigUtil.sendMessage(player, "messages.playerNotFound", null);
            return false;
        }
        ClanMember targetMember = Main.getInstance().getClanManager().getPlayerClanMember(targetName);
        if (targetMember == null) {
            ConfigUtil.sendMessage(player, "messages.playerNotFound", null);
            return false;
        }
        if (!ClanRoles.MODERATOR.equals(ClanRoles.normalize(targetMember.getRole().getName()))) {
            ConfigUtil.sendMessage(player, "messages.notModerator", null);
            return false;
        }
        if (!player.getName().equalsIgnoreCase(clan.getOwner())) {
            ConfigUtil.sendMessage(player, "messages.onlyOwnerRole", null);
            return false;
        }
        // Возвращаем ту роль, которую участник заслужил личным вкладом.
        targetMember.setRole(ClanRoles.create(ClanRoles.earnedRole(targetMember.getLevel())));
        ConfigUtil.sendMessage(player, "messages.demoted",
                ConfigUtil.setHolder(new String[]{"%player%"}, new String[]{targetName}));
        return false;
    }

    private boolean handleDisband(Player player, Clan clan, ClanMember member) {
        if (!member.getRole().getPermissions().contains(Permissions.DELETE)) {
            ConfigUtil.sendMessage(player, "messages.noPermission", null);
            return false;
        }
        DisbandConfirmManager.request(player.getName(), clan.getName());
        ConfigUtil.sendMessage(player, "messages.disbandConfirm",
                ConfigUtil.setHolder(new String[]{"%clan%"}, new String[]{clan.getName()}));
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length <= 1) {
            List<String> result = new ArrayList<>();
            String input = args.length == 0 ? "" : args[0].toLowerCase();
            for (String s : COMPLETES) {
                if (s.startsWith(input)) result.add(s);
            }
            return result;
        }
        if (!(sender instanceof Player)) return Collections.emptyList();
        if (Arrays.asList("kick", "invite", "promote", "demote").contains(args[0].toLowerCase())) {
            List<String> names = new ArrayList<>();
            String input = args[1].toLowerCase();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(input)) names.add(p.getName());
            }
            return names;
        }
        return Collections.emptyList();
    }
}