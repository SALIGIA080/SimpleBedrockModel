package com.github.mcmodderanchor.simplebedrockmodel.v1.particle.debug;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * 粒子发射器包围盒调试渲染器。
 * <p>
 * 26.2 移除了 WorldRenderContext/WorldRenderEvents/LevelRenderer.renderLineBox/
 * shouldRenderHitBoxes 等旧 API，本调试渲染器暂时降级为空实现。
 * 后续如需恢复，需基于 {@code SubmitRenderPhases} + {@code submitShapeOutline} 重写。
 */
@Environment(EnvType.CLIENT)
public class ParticleDebugRenderer {

    private ParticleDebugRenderer() {
    }

    // 26.2 迁移中暂时禁用：原实现依赖 WorldRenderContext / renderLineBox / shouldRenderHitBoxes，
    // 这些 API 已移除，且本功能仅为调试辅助（渲染发射器包围盒），暂不恢复。
}
