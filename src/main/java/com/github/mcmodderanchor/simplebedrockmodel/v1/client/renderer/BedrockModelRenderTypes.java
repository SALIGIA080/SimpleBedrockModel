package com.github.mcmodderanchor.simplebedrockmodel.v1.client.renderer;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/**
 * 26.2 简化：旧版自定义 polyMeshCutout（TRIANGLES 拓扑）在 26.2 已不再需要，
 * 因为 26.2 的 RenderType 统一使用 TRIANGLES 拓扑（不再区分 QUADS/TRIANGLES），
 * {@code RenderTypes.entityCutout} 即可覆盖 cube 与 mesh 两种几何。
 */
@Environment(EnvType.CLIENT)
public final class BedrockModelRenderTypes {
    private BedrockModelRenderTypes() {
    }

    public static RenderType polyMeshCutout(Identifier texture) {
        return RenderTypes.entityCutout(texture);
    }
}
