package ru.rooyzee.elytrixclans.function.impl.shop.object;

import java.util.List;

public class ArrowItem {

    private String name;
    private String material;
    private String effect;
    private List<String> lore;
    private int price;
    private int count;
    private int slot;
    private int amplifier;
    private int duration;
    private String metaPath;

    public ArrowItem(String name, String material, List<String> lore, int price, int count, int slot, int amplifier, int duration, String effect) {
        this(name, material, lore, price, count, slot, amplifier, duration, effect, null);
    }

    public ArrowItem(String name, String material, List<String> lore, int price, int count, int slot, int amplifier, int duration, String effect, String metaPath) {
        this.name = name;
        this.material = material;
        this.lore = lore;
        this.price = price;
        this.count = count;
        this.slot = slot;
        this.amplifier = amplifier - 1;
        this.duration = duration;
        this.effect = effect;
        this.metaPath = metaPath;
    }

    public String getName() { return name; }
    public String getMaterial() { return material; }
    public String getEffect() { return effect; }
    public List<String> getLore() { return lore; }
    public int getPrice() { return price; }
    public int getCount() { return count; }
    public int getSlot() { return slot; }
    public int getAmplifier() { return amplifier; }
    public int getDuration() { return duration; }
    public String getMetaPath() { return metaPath; }
}