package cn.sh1rocu.simplebedrockmodel.util.client;

import net.minecraft.client.Minecraft;

public class MinecraftUtil {

    public static float getPartialTick() {
        Minecraft mc = Minecraft.getInstance();
        return mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
    }
}
