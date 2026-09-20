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
import ru.rooyzee.elytrixclans.permission.Permissions;
import ru.rooyzee.elytrixclans.role.Roles;
import ru.rooyzee.elytrixclans.status.Status;
import ru.rooyzee.elytrixclans.utils.CloseInventoryUtil;
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
        unindexClan(clan);
        clan.getMemberList().forEach(member -> {
            member.setDeaths(0);
            member.setKDA(0);
            member.setKills(0);
            member.setPoints(0);
            member.setLevel(0);
            member.setRole(new Roles("Участник"));
        });
        clan.setMemberList(new CopyOnWriteArrayList<>());
        clans.remove(clan);
    }

    public void createClan(String name, Player owner) {
        ClanMember ownerMember = new ClanMember(owner.getName(), Status.ONLINE, 0, 0, 1.0, 0, 0,
                new Roles("Лидер", Permissions.values()));
        CopyOnWriteArrayList<ClanMember> members = new CopyOnWriteArrayList<>();
        members.add(ownerMember);
        Clan clan = new Clan(0, null, owner.getName(), members, 0, false, false, name);
        clans.add(clan);
        indexClan(clan);
    }

    public void addPlayer(Clan clan, Player player) {
        ClanMember member = new ClanMember(player.getName(), Status.ONLINE, 0, 0, 1.0, 0, 0,
                new Roles("Участник"));
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
        double pointsToRemove = Math.min(member.getPoints(), clan.getPoints());
        double expToRemove = Math.min(member.getLevel(), clan.getExp());
        clan.setPoints(clan.getPoints() - pointsToRemove);
        clan.setExp(clan.getExp() - expToRemove);
        clan.getMemberList().removeIf(m -> m.getName().equalsIgnoreCase(player.getName()));
        byMemberName.remove(key(player.getName()), clan);
        if (Main.getInstance().getGlowManager() != null) {
            Main.getInstance().getGlowManager().resetArmorForEveryone(player);
        }
    }

    public void kickPlayer(Clan clan, ClanMember clanMember) {
        if (clan == null || clanMember == null) return;
        if (clanMember.getPlayer() != null && clanMember.getPlayer().isOnline()) {
            CloseInventoryUtil.closePlayerMenus(clanMember.getPlayer());
        }
        double pointsToRemove = Math.min(clanMember.getPoints(), clan.getPoints());
        double expToRemove = Math.min(clanMember.getLevel(), clan.getExp());
        clan.setPoints(clan.getPoints() - pointsToRemove);
        clan.setExp(clan.getExp() - expToRemove);
        clan.getMemberList().removeIf(m -> m.getName().equalsIgnoreCase(clanMember.getName()));
        byMemberName.remove(key(clanMember.getName()), clan);
        if (Main.getInstance().getGlowManager() != null && clanMember.getPlayer() != null) {
            Main.getInstance().getGlowManager().resetArmorForEveryone(clanMember.getPlayer());
        }
    }

    public void rebuildIndexes() {
        byClanName.clear();
        byMemberName.clear();
        for (Clan clan : clans) {
            indexClan(clan);
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