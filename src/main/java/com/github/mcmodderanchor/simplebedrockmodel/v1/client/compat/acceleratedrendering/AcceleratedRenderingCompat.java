package com.github.mcmodderanchor.simplebedrockmodel.v1.client.compat.acceleratedrendering;

import com.github.mcmodderanchor.simplebedrockmodel.v1.common.model.BedrockBone;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.runtime.ParticleInstance;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Accelerated Rendering 兼容层（stub）。
 * <p>
 * 26.2 尚无 AcceleratedRendering（com.github.argon4w）对应版本，故本兼容层整体降级为
 * 「未加载」状态：{@link #isLoaded()} 恒返回 false，所有 {@code render*} 均返回 false，
 * 让调用方回退到标准 {@link VertexConsumer} 渲染路径。
 */
@Environment(EnvType.CLIENT)
public final class AcceleratedRenderingCompat {
    private AcceleratedRenderingCompat() {
    }

    public static boolean isLoaded() {
        return false;
    }

    public static boolean renderCubes(BedrockBone bone, PoseStack.Pose pose, VertexConsumer consumer,
                                      int lightmap, int overlay, float red, float green, float blue, float alpha) {
        return false;
    }

    public static boolean renderMeshes(BedrockBone bone, PoseStack.Pose pose, VertexConsumer consumer,
                                       int lightmap, int overlay, float red, float green, float blue, float alpha) {
        return false;
    }

    public static boolean renderParticleBillboard(VertexConsumer consumer,
                                                  ParticleInstance particle,
                                                  Matrix4f effectivePose, Matrix3f normal,
                                                  int light) {
        return false;
    }
}
