package com.github.mcmodderanchor.simplebedrockmodel.v1.client.event;

import cn.sh1rocu.simplebedrockmodel.api.event.RenderTickEvent;
import com.github.mcmodderanchor.simplebedrockmodel.v1.client.animation.PausedClientAnimationClock;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.time.AnimationClock;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

@Environment(EnvType.CLIENT)
public final class ClientAnimationClockTicker {
    private ClientAnimationClockTicker() {
    }

    public static AnimationClock getAnimationClock() {
        return PausedClientAnimationClock.getInstance();
    }

    public static void onRenderTick(RenderTickEvent event) {
        if (event.phase == RenderTickEvent.Phase.START) {
            PausedClientAnimationClock.getInstance().update();
        }
    }

    public static void onLoggingOut(ClientPacketListener handler, Minecraft client) {
        PausedClientAnimationClock.getInstance().reset();
    }
}
