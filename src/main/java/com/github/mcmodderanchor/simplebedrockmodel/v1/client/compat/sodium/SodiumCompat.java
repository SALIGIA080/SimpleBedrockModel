package com.github.mcmodderanchor.simplebedrockmodel.v1.client.compat.sodium;

/**
 * Sodium 兼容层（stub）。
 * <p>
 * 26.2 下 Sodium/Embeddium 的 {@code VertexBufferWriter}/{@code ModelVertex} API 已大改，
 * 快速写入路径暂不启用：{@link #isSodiumInstalled()} 恒返回 false，让所有调用方回退到
 * 标准 {@code VertexConsumer} 渲染路径。
 */
public class SodiumCompat {
    public static final String SODIUM = "sodium";

    public static void init() {
    }

    public static boolean isSodiumInstalled() {
        return false;
    }
}
