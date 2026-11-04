package com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.gpu;

import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.world.GeometryCache;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.phys.AABB;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 一个 GPU 网格的内部所有权记录；共享引用只在 GeometryCache.Entry 计数，句柄只释放一次。
 *
 * <p><b>26.2 迁移</b>：{@code VertexBuffer} 已移除，改为 {@link GpuBuffer} + 索引计数。
 * 实例光照（UV2 重绑定）在 26.2 无公开 API，实现为「CPU 顶点模板 + 按光照值的 GPU 副本
 * 缓存」：{@link #vertexData} 持有 UNIFORM/MUTABLE 网格的 pristine CPU 模板，
 * UNIFORM 网格的每种实例光照由 {@link WorldMeshBuffers#prepareDraw} 首次出现时
 * 补丁出独立副本并缓存复用（LRU 上限 {@link #MAX_LIGHT_COPIES}），稳态零回写。</p>
 */
@Environment(EnvType.CLIENT)
public class WorldMeshPart {
    /**
     * 这个 shard 的光照怎么给。
     *
     * <ul>
     *   <li>{@link LightMode#FIXED}：所有顶点都是烘焙时的实际光照，UV2 直接读几何 buffer；</li>
     *   <li>{@link LightMode#UNIFORM}：所有顶点都跟随实例光照；每种光照值对应一个缓存的
     *       GPU 副本（首次出现时从 CPU 模板补丁上传），命中即直接绑定；</li>
     *   <li>{@link LightMode#MUTABLE}：实际光照独立存储（{@link #lightScratch}），
     *       由调用方按顶点范围更新，脏范围合并后补丁回写。</li>
     * </ul>
     */
    public enum LightMode {
        FIXED,
        UNIFORM,
        MUTABLE
    }

    private static int nextId;

    /** UNIFORM 网格最多缓存的按光照副本数；超出后最久未用的副本被关闭淘汰。 */
    private static final int MAX_LIGHT_COPIES = 4;

    private final WorldMeshBuffers owner;
    private final int id;
    private final String ownerId;
    private final GpuBuffer buffer;
    private final int indexCount;
    private final PrimitiveTopology topology;
    private final VertexFormat format;
    private final RenderType material;
    private final AABB localBounds;
    private final long generation;
    private final CompletableFuture<Void> upload;
    /** UNIFORM/MUTABLE 网格的 CPU 顶点副本（光照补丁源）；FIXED 为 null。 */
    private final ByteBuffer vertexData;
    /** UV2 在顶点里的字节偏移；-1 表示格式无 UV2。 */
    private final int uv2Offset;
    /**
     * UNIFORM 网格的「按光照值缓存的 GPU 副本」（LRU，{@link #MAX_LIGHT_COPIES} 上限）。
     * 26.2 无实例光照参数表/divisor 公开 API，原降级方案是每次光照切换都 CPU 补丁 +
     * 整段回写同一缓冲——共享几何被多个不同光照实例使用时每帧反复全量上传。
     * 缓存后：每种光照值只在首次出现时上传一次副本，之后帧内/跨帧直接绑定既有副本，
     * 稳态零回写；超出上限时淘汰最久未用的副本并关闭。
     */
    private final LinkedHashMap<Integer, GpuBuffer> lightCopies =
            new LinkedHashMap<Integer, GpuBuffer>(MAX_LIGHT_COPIES, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Integer, GpuBuffer> eldest) {
                    if (size() <= MAX_LIGHT_COPIES) return false;
                    eldest.getValue().close();
                    return true;
                }
            };
    /** MUTABLE 光照流的 CPU 暂存内容（逐顶点 4 字节 UV2）。 */
    private final ByteBuffer lightScratch;
    private final LightMode lightMode;
    private final LightRangeUpdates lightUpdates;

    private boolean retired;
    private boolean dead;
    private boolean recycled;

    public WorldMeshPart(WorldMeshBuffers owner, String ownerId, GpuBuffer buffer, int indexCount,
                PrimitiveTopology topology, VertexFormat format, RenderType material, AABB localBounds,
                long generation, CompletableFuture<Void> upload,
                ByteBuffer vertexData, int uv2Offset, ByteBuffer lightScratch, LightMode lightMode) {
        this.owner = owner;
        this.id = nextId++;
        this.ownerId = ownerId;
        this.buffer = buffer;
        this.indexCount = indexCount;
        this.topology = topology;
        this.format = format;
        this.material = material;
        this.localBounds = localBounds;
        this.generation = generation;
        this.upload = upload;
        this.vertexData = vertexData;
        this.uv2Offset = uv2Offset;
        this.lightScratch = lightScratch;
        this.lightMode = lightMode;
        this.lightUpdates = lightMode == LightMode.MUTABLE && lightScratch != null
                ? new LightRangeUpdates(lightScratch) : null;
    }

    /** 单调递增的句柄号，供库内排序（分组绘制）与统计使用。 */
    public int id() {
        return this.id;
    }

    public RenderType material() {
        return this.material;
    }

    public AABB localBounds() {
        return this.localBounds;
    }

    /**
     * 修改 MUTABLE 网格的实际光照；范围单位为顶点，零光照表示黑暗。
     * 渲染线程调用，暂存于 CPU 副本，首次可见绘制前合并补丁回写。
     * 本操作影响共享此句柄的所有实例；调用方负责排除自发光等固定范围。
     * @return CPU 暂存光照是否实际发生变化
     */
    public boolean updateLight(int firstVertex, int vertexCount, int packedLight) {
        RenderSystem.assertOnRenderThread();
        if (!isAlive() || uploadFailed()) {
            throw new IllegalStateException("Cannot update a released or failed shard");
        }
        if (this.lightUpdates == null) {
            throw new IllegalStateException("Light range updates require MeshLighting.MUTABLE");
        }
        return this.lightUpdates.set(firstVertex, vertexCount, packedLight);
    }

    public LightRangeUpdates lightUpdates() {
        return this.lightUpdates;
    }

    public long generation() {
        return this.generation;
    }

    /** 是否有独立光照数据（CPU 副本）；FIXED 网格不需要补丁。 */
    public boolean hasLightBuffer() {
        return this.vertexData != null;
    }

    public LightMode lightMode() {
        return this.lightMode;
    }

    /** UNIFORM/MUTABLE 的 CPU 顶点副本（光照补丁源）。 */
    public ByteBuffer vertexData() {
        return this.vertexData;
    }

    public int uv2Offset() {
        return this.uv2Offset;
    }

    public ByteBuffer lightScratch() {
        return this.lightScratch;
    }

    /** UNIFORM 网格：取该光照值的已缓存 GPU 副本；未缓存返回 null。 */
    public GpuBuffer lightCopy(int packedLight) {
        return this.lightCopies.get(packedLight);
    }

    /** UNIFORM 网格：登记该光照值的 GPU 副本（LRU；超上限时最旧的副本被关闭）。 */
    public void cacheLightCopy(int packedLight, GpuBuffer buffer) {
        GpuBuffer previous = this.lightCopies.put(packedLight, buffer);
        if (previous != null && previous != buffer) {
            previous.close();
        }
    }

    /** 释放全部按光照缓存的副本；recycle 时调用。 */
    public void closeLightCopies() {
        for (GpuBuffer copy : this.lightCopies.values()) {
            copy.close();
        }
        this.lightCopies.clear();
    }

    /** 上传已成功完成；实际使用前还需检查 isAlive()。26.2 下上传为同步，构造即完成。 */
    public boolean isUploaded() {
        return this.upload.isDone() && !this.upload.isCompletedExceptionally();
    }

    public boolean uploadFailed() {
        return this.upload.isCompletedExceptionally();
    }

    /** 是否仍可用：既没有硬失效，也没有被所有者释放。 */
    public boolean isAlive() {
        return !this.dead && !this.retired;
    }

    /** 所有者不再使用时释放；重复释放无操作，上传结束后再回收。 */
    public void release() {
        this.owner.release(this);
    }

    public String ownerId() {
        return this.ownerId;
    }

    public GpuBuffer buffer() {
        return this.buffer;
    }

    public int indexCount() {
        return this.indexCount;
    }

    public PrimitiveTopology topology() {
        return this.topology;
    }

    public VertexFormat format() {
        return this.format;
    }

    public CompletableFuture<Void> upload() {
        return this.upload;
    }

    public boolean retire() {
        if (this.dead || this.retired) return false;
        this.retired = true;
        return true;
    }

    public boolean isInvalidated() {
        return this.dead;
    }

    public boolean markRecycled() {
        if (this.recycled) {
            return false;
        }
        this.recycled = true;
        return true;
    }

    public void kill() {
        this.dead = true;
        this.retired = true;
    }
}
