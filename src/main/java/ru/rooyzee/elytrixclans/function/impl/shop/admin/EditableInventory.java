package ru.rooyzee.elytrixclans.function.impl.shop.admin;

import java.util.UUID;

/**
 * Помечает инвентари админ-редакторов: у такого окна есть владелец, и сохранение
 * должно произойти ровно один раз (закрытие окна или выход игрока с открытым окном).
 */
public interface EditableInventory {

    UUID getOwner();

    boolean isSaved();

    void markSaved();
}
