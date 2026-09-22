package ru.rooyzee.elytrixtalisman.model;

import org.bukkit.Location;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class TalismanSession {

    private final String regionId;
    private Location location;
    private Location talismanBlockLocation;
    private int captureRadius;
    private int bank;
    private final int maxBank;
    private TalismanState state;
    private final Map<String, ClanCaptureData> clanData;

    public TalismanSession(int maxBank) {
        // Префикс общий с RegionService: по нему находятся регионы прошлых запусков.
        this.regionId = ru.rooyzee.elytrixtalisman.service.RegionService.REGION_PREFIX
                + UUID.randomUUID().toString().substring(0, 8);
        this.bank = 0;
        this.maxBank = maxBank;
        this.state = TalismanState.IDLE;
        this.clanData = new ConcurrentHashMap<>();
    }

    public String getRegionId() {
        return regionId;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public Location getTalismanBlockLocation() {
        return talismanBlockLocation;
    }

    public void setTalismanBlockLocation(Location talismanBlockLocation) {
        this.talismanBlockLocation = talismanBlockLocation;
    }

    public int getCaptureRadius() {
        return captureRadius;
    }

    public void setCaptureRadius(int captureRadius) {
        this.captureRadius = captureRadius;
    }

    public int getBank() {
        return bank;
    }

    public void addBank(int amount) {
        this.bank = Math.min(this.bank + amount, maxBank);
    }

    public int getMaxBank() {
        return maxBank;
    }

    public boolean isBankFull() {
        return bank >= maxBank;
    }

    public TalismanState getState() {
        return state;
    }

    public void setState(TalismanState state) {
        this.state = state;
    }

    public ClanCaptureData getData(String clanName) {
        return clanData.computeIfAbsent(clanName, k -> new ClanCaptureData());
    }

    public Map<String, ClanCaptureData> getClanData() {
        return clanData;
    }

    public Map<String, ClanCaptureData> getTopClans(int limit) {
        return clanData.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue().getCapturePoints(), a.getValue().getCapturePoints()))
                .limit(limit)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
    }

    public String getLeaderName(String fallback) {
        return clanData.entrySet().stream()
                .max(Comparator.comparingInt(e -> e.getValue().getCapturePoints()))
                .map(Map.Entry::getKey)
                .orElse(fallback);
    }

    public double getProgress() {
        if (maxBank <= 0) return 1.0;
        return Math.min((double) bank / maxBank, 1.0);
    }
}