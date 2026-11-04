package com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.gpu;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * 静态 GPU 缓冲计数（26.2）。
 *
 * <p>1.20.1 的 {@code VertexBuffer} 支持重复上传，因此这里曾是一个按顶点格式分桶的
 * 缓冲池。26.2 的 {@link GpuBuffer} 由 {@code RenderSystem.getDevice().createBuffer(...)}
 * 创建后大小固定、上传即定形，不存在「回收后重新上传」的复用语义，因此本类退化为
 * 纯统计：创建计数在 {@link #acquire}，回收即关闭（{@link #recycle}）。</p>
 *
 * <p>所有方法都必须在渲染线程调用。</p>
 */
@Environment(EnvType.CLIENT)
public class VertexBufferPool {
    private int created;
    private int reused;

    public void acquire(VertexFormat format) {
        this.created++;
    }

    /** 26.2 下 GpuBuffer 不可复用，回收即关闭。 */
    public void recycle(VertexFormat format, GpuBuffer buffer) {
        buffer.close();
    }

    public void clear() {
        // 无驻留资源；保留入口供生命周期对齐。
    }

    public int created() {
        return this.created;
    }

    public int reused() {
        return this.reused;
    }

    public int idleCount() {
        return 0;
    }
}
