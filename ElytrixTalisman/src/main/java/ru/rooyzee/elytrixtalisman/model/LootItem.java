package ru.rooyzee.elytrixtalisman.model;

import org.bukkit.inventory.ItemStack;

public class LootItem {

    private final ItemStack itemStack;

    public LootItem(ItemStack itemStack) {
        this.itemStack = itemStack;
    }

    public ItemStack getItemStack() {
        return itemStack.clone();
    }
}