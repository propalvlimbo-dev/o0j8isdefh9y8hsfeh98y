package ru.rooyzee.elytrixclans.function.impl.shop.object;

import java.util.Collections;
import java.util.List;
import org.bukkit.inventory.ItemStack;
import ru.rooyzee.elytrixclans.utils.ShopItemMeta;

/**
 * Позиция магазина.
 *
 * Слот больше не задаётся в конфиге: позиции раскладываются по страницам автоматически
 * в порядке «сначала дешёвые/низкоуровневые». Валюта — монеты Vault.
 *
 * Товар может быть:
 *  - ванильным предметом (material + amount + мета);
 *  - выдачей чужого плагина через консольные команды (commands), тогда material служит иконкой.
 */
public class ShopItem {

    /** Слепок предмета целиком (со всеми NBT), либо null для обычных позиций. */
    private ItemStack snapshot;

    private final String id;
    private final String name;
    private final String material;
    private final List<String> lore;
    private final double price;
    private final int amount;
    private final int requiredLevel;
    private final List<String> commands;
    private final int cooldownSeconds;
    private final ShopItemMeta.Reader meta;

    public ShopItem(String id, String name, String material, List<String> lore, double price,
                    int amount, int requiredLevel, List<String> commands, int cooldownSeconds,
                    ShopItemMeta.Reader meta) {
        this.id = id;
        this.name = name;
        this.material = material;
        this.lore = lore != null ? lore : Collections.<String>emptyList();
        this.price = price;
        this.amount = Math.max(1, amount);
        this.requiredLevel = Math.max(1, requiredLevel);
        this.commands = commands != null ? commands : Collections.<String>emptyList();
        this.cooldownSeconds = Math.max(0, cooldownSeconds);
        this.meta = meta != null ? meta : ShopItemMeta.empty();
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getMaterial() { return material; }
    public List<String> getLore() { return lore; }
    public double getPrice() { return price; }
    public int getAmount() { return amount; }
    public int getRequiredLevel() { return requiredLevel; }

    /** Явный кулдаун покупки в секундах; 0 — считать автоматически от цены. */
    public int getCooldownSeconds() { return cooldownSeconds; }

    /** Консольные команды выдачи (для предметов чужих плагинов). %player% заменяется на ник. */
    public List<String> getCommands() { return commands; }

    /** Свойства предмета из конфига: зачарования, эффекты, прочность, флаги. */
    public ShopItemMeta.Reader getMeta() { return meta; }

    /**
     * Полный слепок предмета, если он сохранён в конфиге.
     *
     * Используется вместо сборки по ключам: только так переживают выдачу предметы
     * сторонних плагинов, которые держат данные в собственных NBT-тегах.
     */
    public ItemStack getSnapshot() { return snapshot == null ? null : snapshot.clone(); }

    public void setSnapshot(ItemStack snapshot) {
        this.snapshot = snapshot == null ? null : snapshot.clone();
    }

    /** Товар выдаётся командами, а не предметом из инвентаря витрины. */
    public boolean isCommandItem() { return !commands.isEmpty(); }
}
