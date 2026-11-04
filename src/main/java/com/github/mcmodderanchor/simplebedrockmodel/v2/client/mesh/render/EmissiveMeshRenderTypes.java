package com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.render;

import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/**
 * 26.2 简化：旧版自定义发光 cutout 材质在 26.2 已不需要，
 * 统一使用 {@code RenderTypes.entityCutout}（cutout + 无 cull，覆盖 cube 与 mesh 两种拓扑）。
 */
public final class EmissiveMeshRenderTypes {
    private EmissiveMeshRenderTypes() {
    }

    public static RenderType cutout(Identifier texture, Object mode) {
        return RenderTypes.entityCutout(texture);
    }
}
