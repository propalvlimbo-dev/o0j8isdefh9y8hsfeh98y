package ru.rooyzee.elytrixclans.function.impl.shop.kit;

import java.util.Collections;
import java.util.List;
import org.bukkit.inventory.ItemStack;

/** Набор предметов. Цена в монетах Vault, доступность — по уровню клана. */
public class Kit {

    private final String id;
    private final String displayName;
    private final String iconMaterial;
    private final double price;
    private final int requiredLevel;
    private final List<ItemStack> items;
    private final List<String> commands;
    private final int cooldownSeconds;

    public Kit(String id, String displayName, String iconMaterial, double price, int requiredLevel,
               List<ItemStack> items, List<String> commands, int cooldownSeconds) {
        this.id = id;
        this.displayName = displayName;
        this.iconMaterial = iconMaterial;
        this.price = price;
        this.requiredLevel = Math.max(1, requiredLevel);
        this.items = items != null ? items : Collections.<ItemStack>emptyList();
        this.commands = commands != null ? commands : Collections.<String>emptyList();
        this.cooldownSeconds = Math.max(0, cooldownSeconds);
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getIconMaterial() { return iconMaterial; }
    public double getPrice() { return price; }
    public int getRequiredLevel() { return requiredLevel; }

    /** Явный кулдаун покупки в секундах; 0 — считать автоматически от цены. */
    public int getCooldownSeconds() { return cooldownSeconds; }
    public List<ItemStack> getItems() { return items; }

    /** Дополнительные консольные команды выдачи (предметы чужих плагинов). */
    public List<String> getCommands() { return commands; }
}
