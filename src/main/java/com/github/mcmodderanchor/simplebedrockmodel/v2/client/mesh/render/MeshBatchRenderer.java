package com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.render;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

/**
 * 管理每组材质的绘制状态；实例光照由对应几何的所有者管理（CPU 补丁回写）。
 *
 * <p><b>26.2 迁移</b>：{@code ShaderInstance}/{@code Uniform} 已移除，绘制改为
 * 「{@code RenderType.prepare()} 快照当前 ModelView 栈顶 →
 * {@code drawFromBuffer(vb, ib, type, 0, 0, indexCount)}」的官方范式
 * （{@code drawFromBuffer} 内部即 {@code drawIndexed(indexCount, 1, 0, 0, 0)}）。
 * 光照方向 uniform（LIGHT0/LIGHT1）的手动上传整体删除——26.2 实体管线的光照由
 * DynamicTransforms + UV2 光照图在管线内部处理，与 vanilla collector 路径一致。</p>
 */
public class MeshBatchRenderer implements AutoCloseable {
    private RenderType material;
    private GpuBuffer boundBuffer;
    private int boundIndexCount;
    private PrimitiveTopology boundTopology = PrimitiveTopology.QUADS;
    private int draws, setups, binds;

    public MeshBatchRenderer() {}

    /**
     * 26.2 下材质切换没有显式的 setup/teardown（管线状态在 prepare() 时快照），
     * 这里只记录当前材质；保持旧 API 形状以减少调用方改动。
     */
    public boolean beginMaterial(RenderType next, Matrix4f modelView) {
        if (material != next) {
            material = next;
            setups++;
        }
        return true;
    }

    /** 记录本次 draw 要消费的持久顶点缓冲；索引由顺序索引缓冲按拓扑生成。 */
    public void bind(GpuBuffer vertexBuffer, int indexCount, PrimitiveTopology topology) {
        boundBuffer = vertexBuffer;
        boundIndexCount = indexCount;
        boundTopology = topology;
        binds++;
    }

    public void drawItem(MeshDrawTransform transform) {
        drawWithModelView(transform.modelView);
    }

    public void drawWorld(Matrix4f modelView, Matrix4f localTransform) {
        drawWithModelView(modelView);
    }

    /**
     * 单次绘制：把捕获的 ModelView 压栈（prepare() 从栈顶快照 DynamicTransforms），
     * 绘制后弹栈。弹栈必须在 drawFromBuffer 之后——Iris 的光影法线矩阵在绘制执行
     * 那一刻读栈顶逆转置，提前弹栈会丢局部变换的旋转层（TACZ 实测病灶）。
     */
    private void drawWithModelView(Matrix4f modelView) {
        if (material == null || boundBuffer == null || boundIndexCount <= 0) return;
        Matrix4fStack mvStack = RenderSystem.getModelViewStack();
        mvStack.pushMatrix();
        mvStack.set(modelView);
        try {
            PreparedRenderType prepared = material.prepare();
            RenderSystem.AutoStorageIndexBuffer indices =
                    RenderSystem.getSequentialBuffer(boundTopology);
            GpuBuffer indexBuffer = indices.getBuffer(boundIndexCount);
            prepared.drawFromBuffer(boundBuffer, indexBuffer, indices.type(), 0, 0, boundIndexCount);
            draws++;
        } finally {
            mvStack.popMatrix();
        }
    }

    public int draws() { return draws; }
    public int setups() { return setups; }
    public int binds() { return binds; }

    public void finishMaterial() {
        material = null;
        boundBuffer = null;
        boundIndexCount = 0;
    }

    /** 仅首次异常收尾；26.2 下无显式着色器状态可清理。 */
    public void abort(RuntimeException failure) {
        material = null;
        boundBuffer = null;
        boundIndexCount = 0;
    }

    @Override public void close() { finishMaterial(); }
}
