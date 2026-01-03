package org.zamecki.minesocket;

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;

public class ModData {
    public static final String MOD_ID = "minesocket";
    public static final Logger logger = org.slf4j.LoggerFactory.getLogger("MineSocket");

    public static String getIdentifierString(Item item) {
        Identifier id = Registries.ITEM.getId(item);
        return id != null ? id.toString() : "";
    }
}
