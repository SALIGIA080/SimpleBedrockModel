package com.github.mcmodderanchor.simplebedrockmodel.v1.particle.world;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
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
     * 26.2 粒子 atlas 的 directory source 是 {@code {type:"directory", prefix:"", source:"particle"}}，
     * 它扫描 {@code textures/particle/} 目录，注册的 sprite 名【保留 particle/ 前缀】：
     * {@code namespace:textures/particle/flash_strip.png} → sprite id {@code namespace:particle/flash_strip}
     * （DirectoryLister 字节码：fileToId → withPrefix(idPrefix="")，仅去掉 textures/ 前缀与 .png 后缀）。
     * <p>
     * 因此这里【只去 textures/ 前缀和 .png 后缀，保留 particle/ 前缀】。
     * 此前误多删一层 particle/ 前缀 → atlas.getSprite 查不到 → 返回 missing sprite →
     * 烟雾/火焰世界粒子 UV 错乱，表现为「放射状青色细长条 + 火光垂直竖立」。
     * <p>
     * {@code minecraft:textures/particle/generic_0.png} → {@code minecraft:particle/generic_0}
     */
    public static Identifier toSpriteId(Identifier texture) {
        String path = texture.getPath();
        if (path.startsWith("textures/")) {
            path = path.substring("textures/".length());
        }
        if (path.endsWith(".png")) {
            path = path.substring(0, path.length() - ".png".length());
        }
        return Identifier.fromNamespaceAndPath(texture.getNamespace(), path);
    }

    /**
     * 从粒子 atlas 获取 sprite；未注册的纹理降级到 missing sprite。
     * <p>
     * 【崩溃修复 · 2026-11-06】26.2 的 {@code AtlasManager.getAtlasOrThrow} 用
     * {@code atlasById} 查找，key 是「atlas id」（{@code AtlasIds.PARTICLES} =
     * {@code minecraft:particles}），不是「sheet id」（{@code TextureAtlas.LOCATION_PARTICLES} =
     * {@code minecraft:textures/atlas/particles.png}）。此前误传 sheet id 导致
     * {@code Invalid atlas id} 崩溃（修复 basePose 后烟雾世界粒子首次走到本路径时触发）。
     */
    public static TextureAtlasSprite getSprite(Identifier texture) {
        try {
            var atlas = Minecraft.getInstance().getAtlasManager()
                    .getAtlasOrThrow(AtlasIds.PARTICLES);
            Identifier spriteId = toSpriteId(texture);
            return atlas.getSprite(spriteId);
        } catch (Exception e) {
            var atlas = Minecraft.getInstance().getAtlasManager()
                    .getAtlasOrThrow(AtlasIds.PARTICLES);
            return atlas.missingSprite();
        }
    }
}
