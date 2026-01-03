package com.djdnejk.mcsocket.config;

import java.nio.file.Path;

import static com.djdnejk.mcsocket.ModData.MOD_ID;

public class MCsocketConfiguration extends Config {
    public int port;
    public String host;
    public Boolean autoStart;
    public Boolean eventBossBar;
    public Boolean requireAuthToken;
    public String authToken;
    public Boolean sslEnabled;
    public String sslKeyStorePath;
    public String sslKeyStorePassword;

    public MCsocketConfiguration() {
        super(Path.of("config", MOD_ID + ".toml"));
    }

    @Override
    public void load() {
        port = this.getOrAdd("port", 8887, "The port to listen on");
        host = this.getOrAdd("host", "0.0.0.0", "The host to listen on");
        autoStart = this.getOrAdd("auto_start", true, "Automatically start the WebSocket server");
        eventBossBar = this.getOrAdd("event_boss_bar", false, "Show boss bar for events");
        requireAuthToken = this.getOrAdd("require_auth_token", false, "Require bearer/token authentication for WebSocket clients");
        authToken = this.getOrAdd("auth_token", "change-me", "Token clients must provide when authentication is required");
        sslEnabled = this.getOrAdd("ssl_enabled", false, "Enable TLS for the WebSocket server (wss://)");
        sslKeyStorePath = this.getOrAdd("ssl_keystore_path", "config/" + MOD_ID + "/keystore.p12", "Path to the PKCS12 or JKS keystore containing the certificate");
        sslKeyStorePassword = this.getOrAdd("ssl_keystore_password", "changeit", "Password protecting the keystore");
    }

    public void reload() {
        this.read();
        this.load();
    }

    public Path getConfigPath() {
        return super.getPath().getParent();
    }
}
