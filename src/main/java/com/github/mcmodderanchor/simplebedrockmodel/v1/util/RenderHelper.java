package com.github.mcmodderanchor.simplebedrockmodel.v1.util;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import org.joml.Matrix4f;

/**
 * 渲染工具类。
 * <p>
 * 26.2 迁移：blit/innerBlit/renderFirstPersonArm 等依赖 Tesselator/PlayerRenderer 旧 API 的
 * 死代码已移除（这些 API 在 26.2 中彻底重构，且无调用链）。
 */
@Environment(EnvType.CLIENT)
public final class RenderHelper {

    private RenderHelper() {
    }

    /**
     * 用摄像机的 pitch/yaw 构建视图旋转矩阵（与 Minecraft 内部一致）。
     * Minecraft 的视图矩阵构建顺序：先绕 X 旋转 pitch，再绕 Y 旋转 (yaw + 180)。
     */
    public static Matrix4f buildCameraRotation(Camera camera) {
        return new Matrix4f()
                .rotationX((float) Math.toRadians(camera.xRot()))
                .rotateY((float) Math.toRadians(camera.yRot() + 180f));
    }
}
