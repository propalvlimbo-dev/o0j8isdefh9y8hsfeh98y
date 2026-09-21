package ru.rooyzee.elytrixtalisman.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Кто участвовал в захвате.
 *
 * Две разные вещи считаются раздельно, и их важно не путать:
 *
 *   currentSegment — участники текущего отрезка до выдачи лута. Сбрасывается каждый раз,
 *                    когда банк добирает до очередной выдачи (по ним считается объём дропа).
 *   sessionHolders — участники за ВСЮ сессию, с привязкой к клану и с никами.
 *                    Нужны для наград в конце: кланам платят поимённо, поэтому здесь
 *                    сброса нет — список живёт до конца ивента.
 */
public class PlayerParticipationTracker {

    private final Set<UUID> currentSegment = new HashSet<>();

    /** Клан → уникальные ники захватчиков. LinkedHash — порядок появления на точке. */
    private final Map<String, Set<String>> sessionHolders = new LinkedHashMap<>();

    /** Ник → сколько тиков игрок простоял на точке. Пригодится для статистики и отладки. */
    private final Map<String, Integer> ticksOnPoint = new HashMap<>();

    /** Отрезок до выдачи лута. */
    public void addPlayer(UUID uuid) {
        currentSegment.add(uuid);
    }

    public int getUniqueCount() {
        return currentSegment.size();
    }

    /** Сбрасывает ТОЛЬКО отрезок лута. Список захватчиков сессии остаётся. */
    public void reset() {
        currentSegment.clear();
    }

    /** Отмечает, что игрок клана стоял на точке. Зовётся каждый тик захвата. */
    public void addHolder(String clanName, String playerName) {
        if (clanName == null || playerName == null) return;
        sessionHolders.computeIfAbsent(clanName, k -> new LinkedHashSet<>()).add(playerName);
        ticksOnPoint.merge(playerName, 1, Integer::sum);
    }

    /** Ники захватчиков клана за всю сессию. Пустой список, если клан не стоял на точке. */
    public List<String> getHolders(String clanName) {
        Set<String> names = sessionHolders.get(clanName);
        return names == null ? new ArrayList<>() : new ArrayList<>(names);
    }

    public int getTicksOnPoint(String playerName) {
        return ticksOnPoint.getOrDefault(playerName, 0);
    }

    /** Полный сброс — при старте новой сессии. */
    public void resetSession() {
        currentSegment.clear();
        sessionHolders.clear();
        ticksOnPoint.clear();
    }
}
