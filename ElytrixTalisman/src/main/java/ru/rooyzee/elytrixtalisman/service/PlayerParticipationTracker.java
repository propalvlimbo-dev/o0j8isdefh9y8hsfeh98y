package ru.rooyzee.elytrixtalisman.service;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class PlayerParticipationTracker {

    private final Set<UUID> currentSegment = new HashSet<>();

    public void addPlayer(UUID uuid) {
        currentSegment.add(uuid);
    }

    public int getUniqueCount() {
        return currentSegment.size();
    }

    public void reset() {
        currentSegment.clear();
    }
}