package org.zamecki.minesocket.config;

import static org.zamecki.minesocket.ModData.MOD_ID;
import java.nio.file.Path;

public class MineSocketConfiguration extends Config {
    public int port;
    public String host;
    public Boolean autoStart;
    public Boolean eventBossBar;

    public MineSocketConfiguration() {
        super(Path.of("config", MOD_ID + ".toml"));
    }

    @Override
    public void load() {
        port = this.getOrAdd("port", 8887, "The port to listen on");
        host = this.getOrAdd("host", "localhost", "The host to listen on");
        autoStart = this.getOrAdd("auto_start", true, "Automatically start the WebSocket server");
        eventBossBar = this.getOrAdd("event_boss_bar", false, "Show boss bar for events");
    }

    public void reload() {
        this.read();
        this.load();
    }

    public Path getConfigPath() {
        return super.getPath().getParent();
    }
}
