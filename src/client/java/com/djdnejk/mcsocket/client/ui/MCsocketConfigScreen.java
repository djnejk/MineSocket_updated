package com.djdnejk.mcsocket.client.ui;

import com.djdnejk.mcsocket.ModData;
import com.djdnejk.mcsocket.network.MenuActionPayload;
import com.djdnejk.mcsocket.config.MCsocketConfiguration;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class MCsocketConfigScreen extends Screen {
    private final Screen parent;
    private final MCsocketConfiguration configuration;

    private TextFieldWidget hostField;
    private TextFieldWidget portField;
    private TextFieldWidget tokenField;
    private TextFieldWidget keystorePathField;
    private TextFieldWidget keystorePasswordField;
    private CyclingButtonWidget<Boolean> autoStartToggle;
    private CyclingButtonWidget<Boolean> eventBossBarToggle;
    private CyclingButtonWidget<Boolean> requireTokenToggle;
    private CyclingButtonWidget<Boolean> sslToggle;
    private TextFieldWidget recordingNameField;

    private Text statusText;

    public MCsocketConfigScreen(Screen parent) {
        super(Text.translatable("screen.mcsocket.title"));
        this.parent = parent;
        this.configuration = new MCsocketConfiguration();
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int startY = this.height / 6 + 20;
        int fieldWidth = 200;
        int fieldHeight = 20;

        hostField = new TextFieldWidget(textRenderer, centerX - fieldWidth / 2, startY, fieldWidth, fieldHeight,
            Text.translatable("screen.mcsocket.host"));
        hostField.setText(configuration.host);
        addDrawableChild(hostField);

        portField = new TextFieldWidget(textRenderer, centerX - fieldWidth / 2, startY + 25, fieldWidth, fieldHeight,
            Text.translatable("screen.mcsocket.port"));
        portField.setText(String.valueOf(configuration.port));
        addDrawableChild(portField);

        autoStartToggle = addDrawableChild(CyclingButtonWidget.onOffBuilder(configuration.autoStart)
            .build(centerX - fieldWidth / 2, startY + 55, fieldWidth, fieldHeight,
                Text.translatable("screen.mcsocket.auto_start"), (button, value) -> {}));

        eventBossBarToggle = addDrawableChild(CyclingButtonWidget.onOffBuilder(configuration.eventBossBar)
            .build(centerX - fieldWidth / 2, startY + 80, fieldWidth, fieldHeight,
                Text.translatable("screen.mcsocket.event_boss_bar"), (button, value) -> {}));

        requireTokenToggle = addDrawableChild(CyclingButtonWidget.onOffBuilder(configuration.requireAuthToken)
            .build(centerX - fieldWidth / 2, startY + 105, fieldWidth, fieldHeight,
                Text.translatable("screen.mcsocket.require_auth_token"), (button, value) -> tokenField.active = value));

        tokenField = new TextFieldWidget(textRenderer, centerX - fieldWidth / 2, startY + 135, fieldWidth, fieldHeight,
            Text.translatable("screen.mcsocket.auth_token"));
        tokenField.setText(configuration.authToken);
        tokenField.setEditableColor(Formatting.WHITE.getColorValue());
        addDrawableChild(tokenField);
        tokenField.active = configuration.requireAuthToken;

        sslToggle = addDrawableChild(CyclingButtonWidget.onOffBuilder(configuration.sslEnabled)
            .build(centerX - fieldWidth / 2, startY + 160, fieldWidth, fieldHeight,
                Text.translatable("screen.mcsocket.ssl_enabled"), (button, value) -> {
                    keystorePathField.active = value;
                    keystorePasswordField.active = value;
                }));

        keystorePathField = new TextFieldWidget(textRenderer, centerX - fieldWidth / 2, startY + 190, fieldWidth, fieldHeight,
            Text.translatable("screen.mcsocket.keystore_path"));
        keystorePathField.setText(configuration.sslKeyStorePath);
        addDrawableChild(keystorePathField);
        keystorePathField.active = configuration.sslEnabled;

        keystorePasswordField = new TextFieldWidget(textRenderer, centerX - fieldWidth / 2, startY + 215, fieldWidth, fieldHeight,
            Text.translatable("screen.mcsocket.keystore_password"));
        keystorePasswordField.setText(configuration.sslKeyStorePassword);
        addDrawableChild(keystorePasswordField);
        keystorePasswordField.active = configuration.sslEnabled;

        int actionsTop = startY + 250;
        int halfWidth = (fieldWidth / 2) - 5;

        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.mcsocket.save"), button -> saveAndClose())
            .position(centerX - 105, actionsTop).size(100, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.mcsocket.cancel"), button -> close())
            .position(centerX + 5, actionsTop).size(100, 20).build());

        int controlsTop = actionsTop + 30;
        recordingNameField = new TextFieldWidget(textRenderer, centerX - fieldWidth / 2, controlsTop, fieldWidth, fieldHeight,
            Text.translatable("screen.mcsocket.recording_name"));
        addDrawableChild(recordingNameField);

        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.mcsocket.start_ws"),
                button -> sendAction(MenuActionPayload.Action.START_WS))
            .position(centerX - fieldWidth / 2, controlsTop + 25).size(halfWidth, fieldHeight).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.mcsocket.stop_ws"),
                button -> sendAction(MenuActionPayload.Action.STOP_WS))
            .position(centerX + 5, controlsTop + 25).size(halfWidth, fieldHeight).build());

        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.mcsocket.record"),
                button -> sendAction(MenuActionPayload.Action.START_RECORDING))
            .position(centerX - fieldWidth / 2, controlsTop + 50).size(halfWidth, fieldHeight).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.mcsocket.cancel_recording"),
                button -> sendAction(MenuActionPayload.Action.CANCEL_RECORDING))
            .position(centerX + 5, controlsTop + 50).size(halfWidth, fieldHeight).build());

        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.mcsocket.save_recording"), button -> {
                sendAction(MenuActionPayload.Action.SAVE_RECORDING, getRecordingName());
            })
            .position(centerX - fieldWidth / 2, controlsTop + 75).size(halfWidth, fieldHeight).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.mcsocket.play_recording"), button -> {
                sendAction(MenuActionPayload.Action.PLAY_RECORDING, getRecordingName());
            })
            .position(centerX + 5, controlsTop + 75).size(halfWidth, fieldHeight).build());

        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.mcsocket.list_recordings"),
                button -> sendAction(MenuActionPayload.Action.LIST_RECORDINGS))
            .position(centerX - fieldWidth / 2, controlsTop + 100).size(fieldWidth, fieldHeight).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Draw a simple gradient background to avoid triggering the screen blur twice in a frame
        context.fillGradient(0, 0, this.width, this.height, 0xC0101010, 0xD0101010);
        context.drawCenteredTextWithShadow(textRenderer, ModData.brandText(), width / 2, 12, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable("screen.mcsocket.subtitle"), width / 2, 24, Formatting.GRAY.getColorValue());

        super.render(context, mouseX, mouseY, delta);

        drawLabel(context, Text.translatable("screen.mcsocket.host"), hostField);
        drawLabel(context, Text.translatable("screen.mcsocket.port"), portField);
        drawLabel(context, Text.translatable("screen.mcsocket.auto_start"), autoStartToggle);
        drawLabel(context, Text.translatable("screen.mcsocket.event_boss_bar"), eventBossBarToggle);
        drawLabel(context, Text.translatable("screen.mcsocket.require_auth_token"), requireTokenToggle);
        drawLabel(context, Text.translatable("screen.mcsocket.auth_token"), tokenField);
        drawLabel(context, Text.translatable("screen.mcsocket.ssl_enabled"), sslToggle);
        drawLabel(context, Text.translatable("screen.mcsocket.keystore_path"), keystorePathField);
        drawLabel(context, Text.translatable("screen.mcsocket.keystore_password"), keystorePasswordField);
        drawLabel(context, Text.translatable("screen.mcsocket.recording_name"), recordingNameField);

        if (statusText != null) {
            context.drawCenteredTextWithShadow(textRenderer, statusText, width / 2, height - 40, Formatting.YELLOW.getColorValue());
        }
    }

    private void drawLabel(DrawContext context, Text text, net.minecraft.client.gui.Element element) {
        int y = 0;
        int x = 0;
        if (element instanceof TextFieldWidget widget) {
            x = widget.getX();
            y = widget.getY() - 10;
        } else if (element instanceof CyclingButtonWidget<?> widget) {
            x = widget.getX();
            y = widget.getY() - 10;
        }
        context.drawText(textRenderer, text, x, y, Formatting.GRAY.getColorValue(), false);
    }

    private void saveAndClose() {
        int parsedPort;
        try {
            parsedPort = Integer.parseInt(portField.getText());
            if (parsedPort <= 0 || parsedPort > 65535) {
                throw new NumberFormatException("Port out of range");
            }
        } catch (NumberFormatException ex) {
            statusText = Text.translatable("screen.mcsocket.invalid_port");
            return;
        }

        configuration.applyAndSave(
            hostField.getText(),
            parsedPort,
            autoStartToggle.getValue(),
            eventBossBarToggle.getValue(),
            requireTokenToggle.getValue(),
            tokenField.getText(),
            sslToggle.getValue(),
            keystorePathField.getText(),
            keystorePasswordField.getText()
        );

        statusText = Text.translatable("screen.mcsocket.saved");

        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            client.setScreen(parent);
        }
    }

    private void sendAction(MenuActionPayload.Action action) {
        sendAction(action, "");
    }

    private void sendAction(MenuActionPayload.Action action, String recordingName) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.getNetworkHandler() == null) {
            statusText = Text.translatable("screen.mcsocket.no_player");
            return;
        }

        if (!ClientPlayNetworking.canSend(MenuActionPayload.ID)) {
            statusText = Text.translatable("screen.mcsocket.cannot_send_action");
            return;
        }

        ClientPlayNetworking.send(new MenuActionPayload(action, recordingName));
        statusText = Text.translatable("screen.mcsocket.sent_action", Text.translatable(action.getTranslationKey()));
    }

    private String getRecordingName() {
        String text = recordingNameField.getText();
        return text.isBlank() ? "default" : text.trim();
    }

    @Override
    public void close() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            client.setScreen(parent);
        }
    }
}
