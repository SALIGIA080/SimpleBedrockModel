package com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.gpu;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.nio.ByteBuffer;

/**
 * 26.2 迁移：原「整数参数表 + glVertexAttribIPointer divisor」实例光照在 26.2 的
 * {@code RenderPipeline} 体系下无公开 API 等价物（顶点绑定静态声明，无运行时改 UV2
 * 属性来源的钩子）。本类降级为纯 CPU 光照补丁助手：
 *
 * <ul>
 *   <li>UNIFORM 网格：实例光照变化时把全部顶点 UV2 补丁为实例值；</li>
 *   <li>MUTABLE 网格：按 {@link LightRangeUpdates} 的脏范围从 lightScratch 补丁 UV2；</li>
 *   <li>补丁后的 CPU 副本经 {@code CommandEncoder.writeToBuffer} 整段回写 GPU。</li>
 * </ul>
 *
 * <p>实例光照精确逐实例读取的能力就此损失，换取 26.2 可实现的语义等价降级。</p>
 */
@Environment(EnvType.CLIENT)
public final class MeshIntegerAttributes {

    private MeshIntegerAttributes() {
    }

    /**
     * UV2 在顶点格式里的字节偏移；格式无 UV2 时返回 -1。
     *
     * <p>26.2 的 {@code VertexFormatElement} 是 record {@code (name, offset, format)}，
     * 偏移直接可读，不再需要按元素累加字节。</p>
     */
    public static int findUv2Offset(VertexFormat format) {
        if (format == null) return -1;
        VertexFormatElement element = format.getElement(DefaultVertexFormat.UV2_SEMANTIC_NAME);
        return element == null ? -1 : element.offset();
    }

    /**
     * 把指定顶点范围的 UV2 补丁为给定打包光照（stride 为顶点字节步长）。
     */
    public static void patchLight(ByteBuffer vertexData, int stride, int uv2Offset,
                                  int firstVertex, int vertexCount, int packedLight) {
        short low = (short) (packedLight & 0xFFFF);
        short high = (short) (packedLight >>> 16);
        for (int vertex = firstVertex, end = firstVertex + vertexCount; vertex < end; vertex++) {
            int offset = vertex * stride + uv2Offset;
            vertexData.putShort(offset, low);
            vertexData.putShort(offset + 2, high);
        }
    }

    /**
     * 把 MUTABLE 网格脏范围内的 UV2 从 lightScratch（逐顶点 4 字节）同步进顶点副本。
     */
    public static void patchFromScratch(ByteBuffer vertexData, int stride, int uv2Offset,
                                        ByteBuffer lightScratch, int firstVertex, int vertexCount) {
        for (int vertex = firstVertex, end = firstVertex + vertexCount; vertex < end; vertex++) {
            int offset = vertex * stride + uv2Offset;
            int scratch = vertex * 4;
            vertexData.putShort(offset, lightScratch.getShort(scratch));
            vertexData.putShort(offset + 2, lightScratch.getShort(scratch + 2));
        }
    }
}
