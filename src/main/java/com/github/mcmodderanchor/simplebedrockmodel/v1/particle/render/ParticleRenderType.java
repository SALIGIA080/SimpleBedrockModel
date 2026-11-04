package com.github.mcmodderanchor.simplebedrockmodel.v1.particle.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import java.util.function.Function;

/**
 * 粒子自定义材质工厂（26.2 还原版）。
 *
 * <p>对照 1.20.1 原版实现（CompositeState 配方，见 git dfc79c8^）逐项还原：
 * <ul>
 *   <li>additive：entity translucent shader + ADDITIVE 混合 + 不写深度
 *       （原 COLOR_WRITE 等价为 ColorTargetState 全通道写 + DepthStencilState 关深度写）；</li>
 *   <li>emissive：entity translucent emissive shader + ADDITIVE 混合 + 写深度
 *       （原 COLOR_DEPTH_WRITE）。</li>
 * </ul>
 * 26.2 中自定义 RenderType = RenderPipeline（管线）+ RenderSetup（纹理/光照/overlay 绑定），
 * 经 {@link RenderType#create} 合成。此前降级为内置 entityTranslucentEmissive 时
 * 混合模式为半透明而非叠加，枪口焰/烟雾观感发灰；本版恢复真 additive。
 */
@Environment(EnvType.CLIENT)
public final class ParticleRenderType {

    /**
     * additive 管线：ENTITY 片段（光照贴图采样）+ 叠加混合 + 关深度写入。
     * 与 1.20.1 {@code ADDITIVE_TRANSPARENCY + COLOR_WRITE} 组合等价。
     */
    private static final RenderPipeline ADDITIVE_PIPELINE =
            RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
                    .withLocation(Identifier.parse("simplebedrockmodel:pipeline/particle_additive"))
                    .withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE))
                    .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
                    .withCull(false)
                    .build();

    /**
     * emissive 管线：ENTITY_EMISSIVE 片段（自发光，不乘光照贴图）+ 叠加混合 + 写深度。
     * 与 1.20.1 {@code ADDITIVE_TRANSPARENCY + COLOR_DEPTH_WRITE} 组合等价。
     */
    private static final RenderPipeline EMISSIVE_PIPELINE =
            RenderPipeline.builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET)
                    .withLocation(Identifier.parse("simplebedrockmodel:pipeline/particle_emissive"))
                    .withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE))
                    .withDepthStencilState(DepthStencilState.DEFAULT)
                    .withCull(false)
                    .build();

    private static final Function<Identifier, RenderType> ADDITIVE_PARTICLE =
            Util.memoize(ParticleRenderType::createAdditiveParticle);
    private static final Function<Identifier, RenderType> EMISSIVE_PARTICLE =
            Util.memoize(ParticleRenderType::createEmissiveParticle);

    private ParticleRenderType() {
    }

    public static RenderType additiveParticle(Identifier texture) {
        return ADDITIVE_PARTICLE.apply(texture);
    }

    public static RenderType emissiveParticle(Identifier texture) {
        return EMISSIVE_PARTICLE.apply(texture);
    }

    private static RenderType createAdditiveParticle(Identifier texture) {
        return RenderType.create("particle_additive",
                RenderSetup.builder(ADDITIVE_PIPELINE)
                        .withTexture("Sampler0", texture)
                        .useLightmap()
                        .useOverlay()
                        .createRenderSetup());
    }

    private static RenderType createEmissiveParticle(Identifier texture) {
        return RenderType.create("particle_emissive",
                RenderSetup.builder(EMISSIVE_PIPELINE)
                        .withTexture("Sampler0", texture)
                        .useLightmap()
                        .useOverlay()
                        .createRenderSetup());
    }
}
