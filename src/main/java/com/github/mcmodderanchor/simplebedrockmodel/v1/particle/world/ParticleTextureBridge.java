package com.github.mcmodderanchor.simplebedrockmodel.v1.particle.world;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;

/**
 * 26.2 粒子纹理桥接：把 SBM 的独立纹理路径（{@code namespace:textures/particle/xxx.png}）
 * 转换为粒子 atlas 的 sprite 名称（{@code namespace:xxx}），并从粒子 atlas 获取 sprite。
 * <p>
 * 1.20.1 时代 SBM 用独立 Identifier 纹理直接绑定（不走 atlas）；26.2 强制粒子走
 * {@code TextureAtlasSprite}。这里做路径转换 + atlas 查询，未注册的纹理优雅降级到 missing sprite。
 */
@Environment(EnvType.CLIENT)
public final class ParticleTextureBridge {

    private ParticleTextureBridge() {
    }

    /**
     * 把 SBM 纹理 Identifier 转为粒子 atlas sprite 名称。
     * <p>
     * {@code minecraft:textures/particle/generic_0.png} → {@code minecraft:generic_0}
     */
    public static Identifier toSpriteId(Identifier texture) {
        String path = texture.getPath();
        if (path.startsWith("textures/")) {
            path = path.substring("textures/".length());
        }
        if (path.startsWith("particle/")) {
            path = path.substring("particle/".length());
        }
        if (path.endsWith(".png")) {
            path = path.substring(0, path.length() - ".png".length());
        }
        return Identifier.fromNamespaceAndPath(texture.getNamespace(), path);
    }

    /**
     * 从粒子 atlas 获取 sprite；未注册的纹理降级到 missing sprite。
     */
    public static TextureAtlasSprite getSprite(Identifier texture) {
        try {
            var atlas = Minecraft.getInstance().getAtlasManager()
                    .getAtlasOrThrow(TextureAtlas.LOCATION_PARTICLES);
            return atlas.getSprite(toSpriteId(texture));
        } catch (Exception e) {
            // 兜底：atlas 不可用时返回 missing sprite
            var atlas = Minecraft.getInstance().getAtlasManager()
                    .getAtlasOrThrow(TextureAtlas.LOCATION_PARTICLES);
            return atlas.missingSprite();
        }
    }
}
