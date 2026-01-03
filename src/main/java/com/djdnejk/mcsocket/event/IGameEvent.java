package com.djdnejk.mcsocket.event;

import net.minecraft.entity.boss.BossBar;
import net.minecraft.text.Text;


public interface IGameEvent {
    String getName();

    boolean start(String[] args);

    boolean tick();

    Text getDisplayName();
    
    float getProgress();

    default BossBar.Color getBossBarColor() {
        return BossBar.Color.WHITE;
    }
    
    default BossBar.Style getBossBarStyle() {
        return BossBar.Style.PROGRESS;
    }
}
