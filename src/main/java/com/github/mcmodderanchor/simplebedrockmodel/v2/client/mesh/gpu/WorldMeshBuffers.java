package com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.gpu;

import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.capture.MeshSink;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.world.WorldMeshMetrics;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * 管理世界网格的 GPU 所有权和可变光照流；阶段调度由世界渲染入口负责。
 *
 * <p><b>26.2 迁移</b>：上传从「{@code ChunkRenderDispatcher.uploadChunkLayer} 异步入队」
 * 改为「{@code GpuDevice.createBuffer} 同步创建」（26.2 无上传队列概念）；实例光照
 * （26.2 无「整数参数表 + divisor」公开等价物）实现为「CPU 顶点模板 + 按光照值缓存
 * GPU 副本」，共享几何多光照实例的稳态回写次数从每帧 ×光照数 降为 0。</p>
 */
public class WorldMeshBuffers {
    private final WorldMeshMetrics metrics;
    private final List<WorldMeshPart> parts = new ArrayList<>();
    public final VertexBufferPool pool = new VertexBufferPool();
    private long generation;

    public WorldMeshBuffers(WorldMeshMetrics metrics) {
        this.metrics = metrics;
    }

    public int size() {
        return parts.size();
    }

    public void clear() {
        RenderSystem.assertOnRenderThread();
        generation++;
        dropAllHandles();
        pool.clear();
    }

    public void removeOwner(String ownerId) { dropHandlesOf(ownerId); }

    /**
     * 绘制前按实例光照准备句柄，返回本次绘制应绑定的 GPU 缓冲。
     *
     * <p>26.2 实例光照实现（按光照值缓存 GPU 副本，替代早前的「整段回写同一缓冲」）：</p>
     * <ul>
     *   <li>FIXED：烘焙光照已在顶点里，直接返回基础缓冲；</li>
     *   <li>UNIFORM：优先复用该光照值的缓存副本（命中即零上传）；未命中时从 CPU 模板
     *       补丁出该光照的副本并一次性上传（LRU 上限见 {@link WorldMeshPart}）；</li>
     *   <li>MUTABLE：把脏范围从 lightScratch 同步进 CPU 副本后整段回写基础缓冲。</li>
     * </ul>
     * 共享几何被多个不同光照实例使用时，稳态下每帧零回写，只切换绑定的副本。
     */
    @Nullable
    public GpuBuffer prepareDraw(WorldMeshPart part, int packedLight) {
        if (part.lightMode() == WorldMeshPart.LightMode.UNIFORM) {
            GpuBuffer cached = part.lightCopy(packedLight);
            if (cached != null) return cached;
            ByteBuffer data = part.vertexData();
            int uv2 = part.uv2Offset();
            if (data == null || uv2 < 0) return part.buffer(); // 无 UV2 可补丁时按烘焙光照绘制
            cached = createLightCopy(part, packedLight);
            part.cacheLightCopy(packedLight, cached);
            return cached;
        } else if (part.lightMode() == WorldMeshPart.LightMode.MUTABLE) {
            flushLightUpdates(part);
        }
        return part.buffer();
    }

    /**
     * 从 CPU 顶点模板补丁出指定实例光照的独立 GPU 副本。
     * 模板本身保持 pristine（不写入光照），供后续其它光照值复用。
     */
    private GpuBuffer createLightCopy(WorldMeshPart part, int packedLight) {
        ByteBuffer template = part.vertexData();
        ByteBuffer patched = ByteBuffer.allocateDirect(template.capacity())
                .order(ByteOrder.nativeOrder());
        patched.put(template.duplicate().clear());
        patched.flip();
        int stride = part.format().getVertexSize();
        MeshIntegerAttributes.patchLight(patched, stride, part.uv2Offset(), 0,
                patched.capacity() / stride, packedLight);
        return RenderSystem.getDevice().createBuffer(
                () -> "sbm-world-mesh-light/" + part.ownerId() + "/" + packedLight,
                GpuBuffer.USAGE_VERTEX, patched);
    }

    /**
     * 提交局部网格；光照语义必须显式指定。调用前 owner 必须已注册。
     * 返回句柄由提交者独占管理；26.2 下上传同步完成。空网格或无世界返回 null。
     * MeshSink 在本方法内同步读取，返回后可复用。所有操作必须在渲染线程执行。
     */
    @Nullable
    public WorldMeshPart submit(String ownerId, MeshSink mesh, RenderType material, MeshLighting lighting) {
        RenderSystem.assertOnRenderThread();
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(mesh, "mesh");
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(lighting, "lighting");
        if (mesh.isEmpty() || Minecraft.getInstance().level == null) return null;

        WorldMeshPart.LightMode lightMode = MeshLighting.classify(mesh, lighting);

        MeshData meshData = mesh.buildBuffer();
        if (meshData == null) return null;
        VertexFormat format = meshData.drawState().format();
        int indexCount = meshData.drawState().indexCount();
        pool.acquire(format);

        // UNIFORM/MUTABLE 需要 CPU 顶点副本供光照补丁；FIXED 直接随 MeshData 释放。
        boolean keepCpuCopy = lightMode != WorldMeshPart.LightMode.FIXED;
        ByteBuffer vertexData = null;
        int uv2Offset = -1;
        if (keepCpuCopy) {
            ByteBuffer source = meshData.vertexBuffer();
            vertexData = ByteBuffer.allocateDirect(source.remaining()).order(ByteOrder.nativeOrder());
            vertexData.put(source.duplicate());
            vertexData.flip();
            uv2Offset = MeshIntegerAttributes.findUv2Offset(format);
        }

        GpuBuffer buffer;
        try {
            buffer = RenderSystem.getDevice().createBuffer(
                    () -> "sbm-world-mesh/" + ownerId, GpuBuffer.USAGE_VERTEX, meshData.vertexBuffer());
        } finally {
            meshData.close();
        }

        ByteBuffer lightScratch = null;
        if (lightMode == WorldMeshPart.LightMode.MUTABLE) {
            lightScratch = ByteBuffer.allocateDirect(mesh.vertexCount() * 4).order(ByteOrder.nativeOrder());
            fillLightTemplate(lightScratch, mesh);
        }

        WorldMeshPart handle = new WorldMeshPart(this, ownerId, buffer, indexCount,
                mesh.mode(), format, material, mesh.bounds(),
                generation, CompletableFuture.completedFuture(null),
                vertexData, uv2Offset, lightScratch, lightMode);
        parts.add(handle);
        metrics.recordSubmit(lightMode);
        return handle;
    }

    /** 初始化 MUTABLE 光照流：动态范围留 0，固定范围写入捕获值。 */
    public void fillLightTemplate(ByteBuffer data, MeshSink mesh) {
        for (int i = 0, count = mesh.lightRunCount(); i < count; i++) {
            int value = mesh.lightRunValue(i);
            if (value == 0) {
                continue;
            }
            int end = Math.min(mesh.lightRunStart(i) + mesh.lightRunLength(i), mesh.vertexCount());
            for (int vertex = mesh.lightRunStart(i); vertex < end; vertex++) {
                data.putShort(vertex * 4, (short) (value & 0xFFFF));
                data.putShort(vertex * 4 + 2, (short) (value >>> 16));
            }
        }
        data.clear();
    }

    /** 仅回写 MUTABLE 流的脏区间对应顶点；几何缓冲及其它实例的光照均保持不变。 */
    public void flushLightUpdates(WorldMeshPart handle) {
        LightRangeUpdates updates = handle.lightUpdates();
        if (updates == null || updates.ranges().isEmpty()) return;
        ByteBuffer data = handle.vertexData();
        int uv2 = handle.uv2Offset();
        if (data == null || uv2 < 0 || handle.lightScratch() == null) {
            updates.clear();
            return;
        }
        int stride = handle.format().getVertexSize();
        for (Map.Entry<Integer, Integer> range : updates.ranges().entrySet()) {
            MeshIntegerAttributes.patchFromScratch(data, stride, uv2,
                    handle.lightScratch(), range.getKey(), range.getValue() - range.getKey());
            metrics.recordLightRangeUpload((range.getValue() - range.getKey()) * stride);
        }
        updates.clear();
        writeBack(handle, data);
    }

    /** 把补丁后的 CPU 顶点副本整段回写 GPU 缓冲。 */
    private static void writeBack(WorldMeshPart part, ByteBuffer data) {
        ByteBuffer payload = data.duplicate();
        payload.clear();
        RenderSystem.getDevice().createCommandEncoder()
                .writeToBuffer(part.buffer().slice(), payload);
    }

    public void release(WorldMeshPart handle) {
        RenderSystem.assertOnRenderThread();
        if (!handle.retire()) return;
        parts.remove(handle);
        metrics.recordRelease();
        recycleWhenUploaded(handle);
    }

    /** LRU／TTL 淘汰时直接释放 GPU 存储。 */
    public void discard(WorldMeshPart handle) {
        RenderSystem.assertOnRenderThread();
        if (!parts.remove(handle)) return;
        handle.kill();
        metrics.recordRelease();
        recycleWhenUploaded(handle);
    }

    public void dropHandlesOf(String ownerId) {
        parts.removeIf(handle -> {
            if (!handle.ownerId().equals(ownerId)) {
                return false;
            }
            handle.kill();
            recycleWhenUploaded(handle);
            return true;
        });
    }

    public void dropAllHandles() {
        for (WorldMeshPart handle : parts) {
            handle.kill();
            recycleWhenUploaded(handle);
        }
        parts.clear();
    }

    /**
     * 26.2 下上传同步完成，future 必然已 done；保留异步分支以兼容语义。
     */
    public void recycleWhenUploaded(WorldMeshPart handle) {
        CompletableFuture<Void> upload = handle.upload();
        if (upload.isDone()) {
            recycle(handle);
            return;
        }
        upload.whenComplete((ignored, failure) -> {
            if (RenderSystem.isOnRenderThread()) {
                recycle(handle);
            } else {
                RenderSystem.queueFencedTask(() -> recycle(handle));
            }
        });
    }

    public void recycle(WorldMeshPart handle) {
        if (!handle.markRecycled()) {
            return;
        }
        // 26.2 的 GpuBuffer 大小固定、上传即定形，无法复用；一律关闭。
        handle.closeLightCopies();
        handle.buffer().close();
    }

}
