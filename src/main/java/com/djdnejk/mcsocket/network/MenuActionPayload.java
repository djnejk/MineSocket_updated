package com.djdnejk.mcsocket.network;

import com.djdnejk.mcsocket.ModData;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record MenuActionPayload(Action action, String recordingName) implements CustomPayload {
    public static final Id<MenuActionPayload> ID = new Id<>(Identifier.of(ModData.MOD_ID, "menu_action"));
    public static final PacketCodec<RegistryByteBuf, MenuActionPayload> CODEC = new PacketCodec<>() {
        @Override
        public MenuActionPayload decode(RegistryByteBuf buf) {
            Action action = buf.readEnumConstant(Action.class);
            String name = buf.readString();
            return new MenuActionPayload(action, name);
        }

        @Override
        public void encode(RegistryByteBuf buf, MenuActionPayload value) {
            buf.writeEnumConstant(value.action());
            buf.writeString(value.recordingName());
        }
    };

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public enum Action {
        START_WS("screen.mcsocket.start_ws"),
        STOP_WS("screen.mcsocket.stop_ws"),
        START_RECORDING("screen.mcsocket.record"),
        SAVE_RECORDING("screen.mcsocket.save_recording"),
        PLAY_RECORDING("screen.mcsocket.play_recording"),
        LIST_RECORDINGS("screen.mcsocket.list_recordings"),
        CANCEL_RECORDING("screen.mcsocket.cancel_recording");

        private final String translationKey;

        Action(String translationKey) {
            this.translationKey = translationKey;
        }

        public String getTranslationKey() {
            return translationKey;
        }
    }
}
