package ru.rooyzee.elytrixclans.api;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.bukkit.entity.Player;
import ru.rooyzee.elytrixclans.Main;
import ru.rooyzee.elytrixclans.clans.Clan;
import ru.rooyzee.elytrixclans.clans.ClanMember;
import ru.rooyzee.elytrixclans.role.ClanRoles;
import ru.rooyzee.elytrixclans.status.Status;
import ru.rooyzee.elytrixclans.level.Level;
import ru.rooyzee.elytrixclans.utils.CloseInventoryUtil;
import ru.rooyzee.elytrixclans.utils.ConfigUtil;
import ru.rooyzee.elytrixclans.utils.LevelUtil;
import ru.rooyzee.elytrixclans.utils.ValidatorUtil;

public class ClanManager {

    private List<Clan> clans = new CopyOnWriteArrayList<>();

    // Индексы «имя клана -> клан» и «ник игрока -> клан».
    // Раньше getPlayerClan() обходил ВСЕ кланы и ВСЕх участников: метод вызывается на каждом
    // ударе, убийстве моба, поломке блока, клике в меню и на каждом пакете экипировки —
    // при сотнях кланов это заметная нагрузка на главный поток.
    private final Map<String, Clan> byClanName = new ConcurrentHashMap<>();
    private final Map<String, Clan> byMemberName = new ConcurrentHashMap<>();

    public List<Clan> getClans() {
        return clans;
    }

    public void setClans(List<Clan> clans) {
        this.clans = new CopyOnWriteArrayList<>(clans != null ? clans : java.util.Collections.<Clan>emptyList());
        rebuildIndexes();
    }

    public Clan getPlayerClan(Player player) {
        if (player == null) return null;
        return getPlayerClan(player.getName());
    }

    public Clan getPlayerClan(String playerName) {
        if (playerName == null || clans == null) return null;
        String key = key(playerName);
        Clan indexed = byMemberName.get(key);
        if (indexed != null) {
            if (hasMember(indexed, playerName)) return indexed;
            byMemberName.remove(key, indexed);
        }
        for (Clan clan : clans) {
            if (clan != null && hasMember(clan, playerName)) {
                byMemberName.put(key, clan);
                return clan;
            }
        }
        return null;
    }

    public Clan getClanByName(String name) {
        if (name == null || clans.isEmpty()) return null;
        String validName = ValidatorUtil.getValidClanName(name);
        if (validName == null || validName.isEmpty()) return null;
        String key = key(validName);
        Clan indexed = byClanName.get(key);
        if (indexed != null) {
            if (indexed.getName() != null && indexed.getName().equalsIgnoreCase(validName)) return indexed;
            byClanName.remove(key, indexed);
        }
        for (Clan clan : clans) {
            if (clan != null && clan.getName() != null && clan.getName().equalsIgnoreCase(validName)) {
                byClanName.put(key, clan);
                return clan;
            }
        }
        return null;
    }

    public ClanMember getPlayerClanMember(Player player) {
        if (player == null) return null;
        return getPlayerClanMember(player.getName());
    }

    public ClanMember getPlayerClanMember(String name) {
        if (name == null) return null;
        Clan clan = getPlayerClan(name);
        if (clan == null) return null;
        return getMember(clan, name);
    }

    /** Поиск участника внутри конкретного клана (без глобального обхода). */
    public ClanMember getMember(Clan clan, String name) {
        if (clan == null || name == null) return null;
        for (ClanMember member : clan.getMemberList()) {
            if (member != null && member.getName() != null && member.getName().equalsIgnoreCase(name)) {
                return member;
            }
        }
        return null;
    }

    public void deleteClan(Clan clan) {
        if (clan == null) return;
        CloseInventoryUtil.closeClanMenus(clan);
        if (Main.getInstance().getGlowManager() != null) {
            Main.getInstance().getGlowManager().removeClanColor(clan);
        }
        // Подсветку снимаем ДО очистки состава: resetGlowForLeaver ходит по memberList,
        // а после очистки рассылать пакеты уже некому — шлемы «застревали» до перезахода.
        if (Main.getInstance().getGlowManager() != null) {
            for (ClanMember member : clan.getMemberList()) {
                if (member == null) continue;
                Player online = member.getPlayer();
                if (online != null && online.isOnline()) {
                    Main.getInstance().getGlowManager().resetGlowForLeaver(online, clan);
                }
            }
        }
        unindexClan(clan);
        clan.getMemberList().forEach(member -> {
            member.setDeaths(0);
            member.setKDA(0);
            member.setKills(0);
            member.setLevel(0);
            member.setRole(ClanRoles.rookie());
        });
        clan.setMemberList(new CopyOnWriteArrayList<>());
        clans.remove(clan);
    }

    public void createClan(String name, Player owner) {
        ClanMember ownerMember = new ClanMember(owner.getName(), Status.ONLINE, 0, 1.0, 0, 0,
                ClanRoles.leader());
        CopyOnWriteArrayList<ClanMember> members = new CopyOnWriteArrayList<>();
        members.add(ownerMember);
        Clan clan = new Clan(0, null, owner.getName(), members, false, false, null, name);
        clans.add(clan);
        indexClan(clan);
    }

    public void addPlayer(Clan clan, Player player) {
        ClanMember member = new ClanMember(player.getName(), Status.ONLINE, 0, 1.0, 0, 0,
                ClanRoles.rookie());
        clan.getMemberList().add(member);
        byMemberName.put(key(player.getName()), clan);
        if (Main.getInstance().getGlowManager() != null && clan.isGlow()) {
            Main.getInstance().getGlowManager().refreshPlayer(player);
        }
    }

    public void kickPlayer(Clan clan, Player player) {
        if (player == null) return;
        if (player.isOnline()) {
            CloseInventoryUtil.closePlayerMenus(player);
        }
        ClanMember member = getPlayerClanMember(player.getName());
        if (member == null) return;
        double expToRemove = Math.min(member.getLevel(), clan.getExp());
        clan.setExp(clan.getExp() - expToRemove);
        // Сначала гасим подсветку (игрок ещё числится в клане и пакеты дойдут до обеих сторон),
        // и только потом убираем его из состава.
        if (Main.getInstance().getGlowManager() != null && player.isOnline()) {
            Main.getInstance().getGlowManager().resetGlowForLeaver(player, clan);
        }
        clan.getMemberList().removeIf(m -> m.getName().equalsIgnoreCase(player.getName()));
        byMemberName.remove(key(player.getName()), clan);
    }

    public void kickPlayer(Clan clan, ClanMember clanMember) {
        if (clan == null || clanMember == null) return;
        if (clanMember.getPlayer() != null && clanMember.getPlayer().isOnline()) {
            CloseInventoryUtil.closePlayerMenus(clanMember.getPlayer());
        }
        double expToRemove = Math.min(clanMember.getLevel(), clan.getExp());
        clan.setExp(clan.getExp() - expToRemove);
        Player target = clanMember.getPlayer();
        if (Main.getInstance().getGlowManager() != null && target != null && target.isOnline()) {
            Main.getInstance().getGlowManager().resetGlowForLeaver(target, clan);
        }
        clan.getMemberList().removeIf(m -> m.getName().equalsIgnoreCase(clanMember.getName()));
        byMemberName.remove(key(clanMember.getName()), clan);
    }

    public void rebuildIndexes() {
        byClanName.clear();
        byMemberName.clear();
        for (Clan clan : clans) {
            indexClan(clan);
            migrateRoles(clan);
        }
    }

    /**
     * Приведение состава к актуальной лестнице ролей.
     *
     * Кланы, сохранённые до появления ролей Новичок/Стажёр/Опытный, хранят «Участник»
     * с произвольным набором прав. Здесь такие роли пересчитываются: владелец клана
     * всегда Лидер, Модератор сохраняется, остальным роль выдаётся по личному вкладу.
     */
    private void migrateRoles(Clan clan) {
        if (clan == null) return;
        for (ClanMember member : clan.getMemberList()) {
            if (member == null || member.getName() == null) continue;

            if (member.getName().equalsIgnoreCase(clan.getOwner())) {
                if (!ClanRoles.LEADER.equals(member.getRole().getName())) {
                    member.setRole(ClanRoles.leader());
                }
                continue;
            }

            String current = ClanRoles.normalize(member.getRole().getName());
            if (ClanRoles.MODERATOR.equals(current)) {
                member.setRole(ClanRoles.create(ClanRoles.MODERATOR));
                continue;
            }
            // Лидером может быть только владелец: чужой «Лидер» из старого файла сбрасывается.
            String earned = ClanRoles.earnedRole(member.getLevel());
            member.setRole(ClanRoles.create(earned));
        }
    }

    private void indexClan(Clan clan) {
        if (clan == null) return;
        if (clan.getName() != null) {
            byClanName.put(key(clan.getName()), clan);
        }
        for (ClanMember member : clan.getMemberList()) {
            if (member == null || member.getName() == null) continue;
            byMemberName.put(key(member.getName()), clan);
        }
    }

    private void unindexClan(Clan clan) {
        if (clan == null) return;
        if (clan.getName() != null) {
            byClanName.remove(key(clan.getName()), clan);
        }
        for (ClanMember member : clan.getMemberList()) {
            if (member == null || member.getName() == null) continue;
            byMemberName.remove(key(member.getName()), clan);
        }
    }

    // --- Опыт клана -----------------------------------------------------------------------
    // Единая точка начисления: её зовут и убийства игроков, и ивент «Талисман»
    // (/elytrixclan addexp, а позже — прямой хук в ElytrixTalisman).

    /**
     * Начисляет клану опыт и, если игрок указан, записывает вклад участнику.
     * Сам рассылает сообщения о повышении/понижении уровня.
     *
     * @return true, если опыт действительно начислен
     */
    public boolean addClanExp(Clan clan, double amount, String contributorName) {
        if (clan == null || amount == 0) return false;
        Level before = LevelUtil.getClanLevel(clan.getExp());
        clan.setExp(clan.getExp() + amount);
        if (contributorName != null) {
            ClanMember member = getMember(clan, contributorName);
            if (member != null) {
                member.setLevel(Math.max(0, member.getLevel() + amount));
                // Личный вклад вырос — возможно, участник дорос до следующей роли.
                checkRolePromotion(clan, member);
            }
        }
        Level after = LevelUtil.getClanLevel(clan.getExp());
        if (before != null && after != null) {
            if (before.getLevel() < after.getLevel()) {
                announceLevel(clan, after, "messages.lvlUp");
            } else if (before.getLevel() > after.getLevel()) {
                announceLevel(clan, after, "messages.lvlDown");
            }
        }
        return true;
    }

    public boolean addClanExp(Clan clan, double amount) {
        return addClanExp(clan, amount, null);
    }

    /**
     * Автоматическое повышение роли за личный вклад: Новичок -> Стажёр -> Опытный.
     *
     * Роли, выданные владельцем вручную (Модератор, Лидер), не трогаются: они выше
     * любой «заработанной», и понижать их начислением опыта нельзя.
     */
    public void checkRolePromotion(Clan clan, ClanMember member) {
        if (clan == null || member == null) return;
        String current = ClanRoles.normalize(member.getRole().getName());
        if (ClanRoles.isManual(current)) return;

        String earned = ClanRoles.earnedRole(member.getLevel());
        if (ClanRoles.weight(earned) <= ClanRoles.weight(current)) return;

        member.setRole(ClanRoles.create(earned));
        Player online = member.getPlayer();
        if (online != null && online.isOnline()) {
            ConfigUtil.sendMessage(online, "messages.roleUp", ConfigUtil.setHolder(
                    new String[]{"%role%", "%player%"},
                    new String[]{earned, member.getName()}));
        }
        for (ClanMember m : clan.getMemberList()) {
            if (m == null || m == member) continue;
            Player other = m.getPlayer();
            if (other != null && other.isOnline()) {
                ConfigUtil.sendMessage(other, "messages.roleUpBroadcast", ConfigUtil.setHolder(
                        new String[]{"%role%", "%player%"},
                        new String[]{earned, member.getName()}));
            }
        }
    }

    /** Начисление опыта по нику игрока: клан находится сам. */
    public boolean addExpByPlayer(String playerName, double amount) {
        Clan clan = getPlayerClan(playerName);
        if (clan == null) return false;
        return addClanExp(clan, amount, playerName);
    }

    private void announceLevel(Clan clan, Level level, String messageKey) {
        Map<String, String> holder = ConfigUtil.setHolder(
                new String[]{"%lvl%", "%clan%"},
                new String[]{String.valueOf(level.getLevel()), String.valueOf(clan.getName())});
        for (ClanMember member : clan.getMemberList()) {
            if (member == null) continue;
            Player online = member.getPlayer();
            if (online != null && online.isOnline()) {
                ConfigUtil.sendMessage(online, messageKey, holder);
            }
        }
    }

    private boolean hasMember(Clan clan, String playerName) {
        for (ClanMember member : clan.getMemberList()) {
            if (member != null && member.getName() != null && member.getName().equalsIgnoreCase(playerName)) {
                return true;
            }
        }
        return false;
    }

    private static String key(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT);
    }
}