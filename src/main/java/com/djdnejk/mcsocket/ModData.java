package com.djdnejk.mcsocket;

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;

public class ModData {
    public static final String MOD_ID = "mcsocket";
    public static final Logger logger = org.slf4j.LoggerFactory.getLogger("MCsocket");

    public static String getIdentifierString(Item item) {
        Identifier id = Registries.ITEM.getId(item);
        return id != null ? id.toString() : "";
    }

    public static Text brandText() {
        return Text.empty()
            .append(colored("M", 0x55ff55))
            .append(colored("C", 0x55ff80))
            .append(colored("s", 0x55ffaa))
            .append(colored("o", 0x55ffd5))
            .append(colored("c", 0x55ffff))
            .append(colored("k", 0x71e3e3))
            .append(colored("e", 0x8ec6c6))
            .append(colored("t", 0xaaaaaa));
    }

    public static Text prefixed(Text body) {
        Text adjustedBody = body.getStyle().getColor() == null
            ? body.copy().setStyle(body.getStyle().withFormatting(Formatting.GRAY))
            : body.copy();
        return Text.empty()
            .append(brandText())
            .append(Text.literal(" » ").setStyle(Style.EMPTY.withFormatting(Formatting.DARK_GRAY)))
            .append(adjustedBody);
    }

    private static Text colored(String text, int color) {
        return Text.literal(text).setStyle(Style.EMPTY.withColor(color).withFormatting(Formatting.BOLD));
    }
}
