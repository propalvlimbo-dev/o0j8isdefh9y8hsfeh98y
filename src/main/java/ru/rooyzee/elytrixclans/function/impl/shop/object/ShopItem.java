package ru.rooyzee.elytrixclans.function.impl.shop.object;

import java.util.List;

public class ShopItem {

    private String name;
    private String material;
    private List<String> lore;
    private int price;
    private int count;
    private int slot;
    private String metaPath;

    public ShopItem(String name, String material, List<String> lore, int price, int count, int slot) {
        this(name, material, lore, price, count, slot, null);
    }

    public ShopItem(String name, String material, List<String> lore, int price, int count, int slot, String metaPath) {
        this.name = name;
        this.material = material;
        this.lore = lore;
        this.price = price;
        this.count = count;
        this.slot = slot;
        this.metaPath = metaPath;
    }

    public String getName() { return name; }
    public String getMaterial() { return material; }
    public List<String> getLore() { return lore; }
    public int getPrice() { return price; }
    public int getCount() { return count; }
    public int getSlot() { return slot; }
    /** Путь записи в shop_item.yml: оттуда берутся зачарования/эффекты/прочность. */
    public String getMetaPath() { return metaPath; }
}