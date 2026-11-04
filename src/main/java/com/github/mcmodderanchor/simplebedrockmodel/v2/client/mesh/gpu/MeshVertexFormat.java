package com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.gpu;

import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.capture.MeshSink;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;

/** 探测渲染兼容钩子提供的实际 BufferBuilder 顶点布局。 */
public class MeshVertexFormat {
    public MeshVertexFormat() {}
    /** 用最小代价问一次"当前生效的顶点格式"：构造 1 个 quad 再丢弃，不碰 GL。 */
    public static VertexFormat probe() {
        ByteBufferBuilder scratch = new ByteBufferBuilder(1024);
        try {
            BufferBuilder probe = new BufferBuilder(scratch, PrimitiveTopology.QUADS, MeshSink.FORMAT);
            for (int i = 0; i < 4; i++) {
                probe.addVertex(0.0F, 0.0F, 0.0F).setColor(255, 255, 255, 255).setUv(0.0F, 0.0F)
                        .setOverlay(0).setLight(0).setNormal(0.0F, 1.0F, 0.0F);
            }
            MeshData rendered = probe.build();
            if (rendered == null) {
                return null;
            }
            try (rendered) {
                return rendered.drawState().format();
            }
        } finally {
            scratch.close();
        }
    }

}
