package ru.rooyzee.elytrixclans.function.impl.shop.kit;

import java.util.List;
import org.bukkit.inventory.ItemStack;

public class Kit {

    private final String id;
    private final String displayName;
    private final String iconMaterial;
    private final int slot;
    private final int price;
    private final List<ItemStack> items;

    public Kit(String id, String displayName, String iconMaterial, int slot, int price, List<ItemStack> items) {
        this.id = id;
        this.displayName = displayName;
        this.iconMaterial = iconMaterial;
        this.slot = slot;
        this.price = price;
        this.items = items;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getIconMaterial() { return iconMaterial; }
    public int getSlot() { return slot; }
    public int getPrice() { return price; }
    public List<ItemStack> getItems() { return items; }
}