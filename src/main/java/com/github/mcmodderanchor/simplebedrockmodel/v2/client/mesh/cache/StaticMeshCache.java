package com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.cache;

import cn.sh1rocu.simplebedrockmodel.api.event.RenderTickEvent;
import com.github.mcmodderanchor.simplebedrockmodel.SimpleBedrockModel;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.capture.GeometryCollector;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.capture.MeshGeometryProvider;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.capture.MeshSink;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.gpu.MeshIntegerAttributes;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.gpu.MeshVertexFormat;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.gpu.VertexBufferPool;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.ApiStatus;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 共享静态几何的准备任务、帧预算、近期驻留与按所有者失效。
 *
 * <p><b>26.2 迁移</b>：{@code VertexBuffer.upload(RenderedBuffer)} 异步入队上传已移除，
 * 改为捕获时 {@code GpuDevice.createBuffer} 同步上传（future 立即完成，READY 不再跨帧）。
 * 实例光照从整数参数表降级为「CPU 顶点副本补丁 + writeToBuffer 整段回写」，
 * 仅在实例光照实际变化时回写（光照值量化，实际变化频率低）。</p>
 */
@Environment(EnvType.CLIENT)
public class StaticMeshCache {
    public enum Preparation {READY, PENDING, UNSUPPORTED}

    private static final int MAX_ENTRIES = 128;
    private static final long MAX_BYTES = 256L * 1024L * 1024L;
    private static final int FORMAT_CHECK_DRAWS = 60;
    private static VertexBufferPool POOL = new VertexBufferPool();
    public static final Map<CacheKey, Entry> CACHE = new LinkedHashMap<>(16, 0.75F, true);
    public static final MeshPreparationPolicy POLICY = MeshPreparationPolicy.DEFAULT;
    private static final MeshPathFailures<Identifier> FAILURES = new MeshPathFailures<>();
    private static final MeshCacheResidency<CacheKey, Entry> RESIDENCY = new MeshCacheResidency<>(
            CACHE, MAX_ENTRIES, MAX_BYTES, System::nanoTime, entry -> entry.references,
            Entry::preparing, entry -> entry.bytes, StaticMeshCache::discard);

    private static ClientLevel level;
    private static VertexFormat probedFormat;
    private static int formatCheckCountdown = FORMAT_CHECK_DRAWS;
    public static long generation;
    private static long frame;
    private static int capturesInFrame;
    private static long captureNanosInFrame, capturedBytesInFrame, capturesTotal;
    private static long residentBytes, reservedBytes;

    public static class Part {
        public GpuBuffer buffer;
        public int indexCount;
        public PrimitiveTopology topology = PrimitiveTopology.QUADS;
        public final VertexFormat requestedFormat;
        public final RenderType material;
        public final CompletableFuture<Void> upload;
        public final boolean instanceLight;
        /** instanceLight 网格的 CPU 顶点副本（实例光照补丁源）。 */
        public final ByteBuffer vertexData;
        public final int uv2Offset;
        public int patchedLight = -1;
        public Identifier owner;
        public long residentBytes;

        public Part(VertexFormat requestedFormat, RenderType material,
                    CompletableFuture<Void> upload, boolean instanceLight,
                    ByteBuffer vertexData, int uv2Offset) {
            this.requestedFormat = requestedFormat;
            this.material = material;
            this.upload = upload;
            this.instanceLight = instanceLight;
            this.vertexData = vertexData;
            this.uv2Offset = uv2Offset;
        }
    }

    public record CacheKey(Identifier owner, Object geometry) {
    }

    public static class Entry {
        public final CacheKey key;
        public int references;
        public List<Part> parts;
        public long bytes;
        public long retryAtNanos;
        public int failures;
        public boolean unsupported;
        public long capturedGeneration;
        public boolean reserved;

        public Entry(CacheKey key) {
            this.key = key;
        }

        public boolean ready() {
            if (parts == null || parts.isEmpty()) return false;
            for (Part part : parts) if (!part.upload.isDone() || part.upload.isCompletedExceptionally()) return false;
            return true;
        }

        public boolean failed() {
            if (parts != null) for (Part part : parts) if (part.upload.isCompletedExceptionally()) return true;
            return false;
        }

        public boolean preparing() {
            return !unsupported && (parts == null || !ready() && !failed());
        }
    }

    public record PreparationStats(long frame, int entries, int ready, int pendingParts,
                                   long pendingBytes, long residentBytes, long reservedBytes,
                                   int captures, long capturedBytes, long captureNanos,
                                   int uploads, long uploadedBytes, long uploadNanos, long capturesTotal) {
    }

    public static PreparationStats preparationStats() {
        RenderSystem.assertOnRenderThread();
        return new PreparationStats(frame, CACHE.size(), (int) CACHE.values().stream().filter(Entry::ready).count(),
                0, 0, residentBytes, reservedBytes,
                capturesInFrame, capturedBytesInFrame, captureNanosInFrame,
                0, 0, 0, capturesTotal);
    }

    public static boolean isOwnerEnabled(Identifier owner) {
        return FAILURES.enabled(owner);
    }

    public static long currentFrame() {
        return frame;
    }

    /**
     * 首次异常记录完整调用栈并取消该 owner；清理失败时传播异常，禁止带着损坏状态继续渲染。
     */
    public static void disableOwner(Identifier owner, String stage, RuntimeException failure) {
        if (!FAILURES.disable(owner)) return;
        boolean cleaned = true;
        try {
            invalidateOwner(owner);
        } catch (RuntimeException cleanup) {
            failure.addSuppressed(cleanup);
            cleaned = false;
        }
        SimpleBedrockModel.LOGGER.error("Static mesh owner {} disabled after failure in {}. "
                + "Reload resources or change worlds to reset this path.", owner, stage, failure);
        if (!cleaned) throw failure;
    }

    /**
     * 26.2 下持久 VBO + CPU 补丁路径不依赖特殊 GL 能力，恒可用；
     * 保留入口供接入方做「是否走 GPU 路径」的统一判定。
     */
    public static boolean supportsInstanceAttributes(int packedLight, int packedOverlay) {
        RenderSystem.assertOnRenderThread();
        return true;
    }

    public static Entry acquire(CacheKey key) {
        Entry entry = CACHE.computeIfAbsent(key, Entry::new);
        pin(entry);
        return entry;
    }

    public static void pin(Entry entry) {
        RESIDENCY.request(entry.key, entry);
        entry.references++;
    }

    public static void releaseEntry(Entry entry, Duration idleRetention) {
        if (entry.references <= 0) throw new IllegalStateException("Mesh reference already released");
        entry.references--;
        RESIDENCY.retention(entry.key, entry, idleRetention);
    }

    public static void discard(Entry entry) {
        RESIDENCY.forget(entry.key, entry);
        CACHE.remove(entry.key, entry);
        retire(entry, true);
    }

    public static void failEntry(Entry entry) {
        if (entry.parts == null) return;
        retire(entry, true);
        entry.retryAtNanos = System.nanoTime() + retryDelay(entry.failures++);
    }

    public static boolean prepareRequest(Object key, MeshGeometryProvider provider, PoseStack pose,
                                         int packedLight, int packedOverlay, Duration retention) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(pose, "pose");
        Objects.requireNonNull(retention, "retention");
        if (retention.isNegative()) throw new IllegalArgumentException("Negative idle retention");
        retention.toNanos();
        ClientLevel current = Minecraft.getInstance().level;
        if (current == null) return false;
        if (current != level) {
            clear();
            level = current;
        }
        if (--formatCheckCountdown <= 0) {
            formatCheckCountdown = FORMAT_CHECK_DRAWS;
            VertexFormat format = MeshVertexFormat.probe();
            if (format != null && probedFormat != null && probedFormat != format) clear();
            if (format != null) probedFormat = format;
        }
        return true;
    }

    public static Preparation prepareTarget(Entry entry, MeshGeometryProvider provider) {
        if (!isOwnerEnabled(entry.key.owner())) return Preparation.UNSUPPORTED;
        if (entry.unsupported) return Preparation.UNSUPPORTED;
        if (entry.failed()) {
            retire(entry, true);
            entry.parts = null;
            entry.retryAtNanos = System.nanoTime() + retryDelay(entry.failures++);
        }
        if (entry.parts == null) {
            long now = System.nanoTime();
            if (now < entry.retryAtNanos || !captureBudgetAvailable(now)) return Preparation.PENDING;
            long start = System.nanoTime();
            boolean captured = capture(entry, entry.key, provider);
            capturesInFrame++;
            captureNanosInFrame += System.nanoTime() - start;
            if (!captured) {
                if (!isOwnerEnabled(entry.key.owner())) return Preparation.UNSUPPORTED;
                if (entry.unsupported) return Preparation.UNSUPPORTED;
                entry.retryAtNanos = System.nanoTime() + retryDelay(entry.failures++);
                return Preparation.PENDING;
            }
        }
        if (!entry.ready()) return Preparation.PENDING;
        if (!layoutSupported(entry)) return Preparation.UNSUPPORTED;

        return Preparation.READY; // 网格已就绪，可立即绘制，也可由批次持有引用后入队。
    }

    public static boolean captureBudgetAvailable(long now) {
        return capturesInFrame == 0 || capturesInFrame < POLICY.maxCaptures()
                && captureNanosInFrame < POLICY.captureNanos();
    }

    public static long retryDelay(int failures) {
        return Math.min(5_000_000_000L, 100_000_000L << Math.min(failures, 5));
    }

    public static boolean capture(Entry entry, Object key, MeshGeometryProvider provider) {
        if (!isOwnerEnabled(entry.key.owner())) return false;
        if (entry.parts != null) return true;
        long capturedGeneration = generation;
        GeometryCollector collector = new GeometryCollector();
        List<Part> parts = new ArrayList<>();
        try {
            if (!provider.capture(collector)) return false;
            Map<GeometryCollector.Pass, MeshSink> meshes = collector.snapshot();
            if (meshes.isEmpty()) {
                entry.unsupported = true;
                return false;
            }
            long bytes = 0;
            for (Map.Entry<GeometryCollector.Pass, MeshSink> pass : meshes.entrySet()) {
                MeshSink mesh = pass.getValue();
                boolean dynamic = false;
                boolean fixed = false;
                for (int i = 0; i < mesh.lightRunCount(); i++) {
                    if (mesh.lightRunValue(i) == 0) dynamic = true;
                    else fixed = true;
                }
                if (dynamic && fixed) {
                    parts.forEach(StaticMeshCache::closePart);
                    SimpleBedrockModel.LOGGER.warn("Immediate mesh has mixed per-vertex lighting: {}", key);
                    entry.unsupported = true;
                    return false;
                }
                MeshData meshData = mesh.buildBuffer();
                if (meshData == null) continue;
                VertexFormat format = meshData.drawState().format();
                // instanceLight 网格保留 CPU 顶点副本供实例光照补丁。
                ByteBuffer vertexData = null;
                int uv2Offset = -1;
                if (dynamic) {
                    ByteBuffer source = meshData.vertexBuffer();
                    vertexData = ByteBuffer.allocateDirect(source.remaining()).order(ByteOrder.nativeOrder());
                    vertexData.put(source.duplicate());
                    vertexData.flip();
                    uv2Offset = MeshIntegerAttributes.findUv2Offset(format);
                }
                Part part = new Part(format, pass.getKey().material(), new CompletableFuture<>(),
                        dynamic, vertexData, uv2Offset);
                part.owner = entry.key.owner();
                part.topology = mesh.mode();
                part.indexCount = meshData.drawState().indexCount();
                try {
                    part.buffer = RenderSystem.getDevice().createBuffer(
                            () -> "sbm-static-mesh/" + key, GpuBuffer.USAGE_VERTEX, meshData.vertexBuffer());
                    long partBytes = meshData.vertexBuffer().remaining();
                    part.residentBytes = partBytes;
                    residentBytes += partBytes;
                    bytes += partBytes;
                    part.upload.complete(null);
                } catch (RuntimeException failure) {
                    part.upload.completeExceptionally(failure);
                    throw failure;
                } finally {
                    meshData.close();
                }
                parts.add(part);
            }
            if (parts.isEmpty()) return false;
            if (capturedGeneration != generation || CACHE.get(entry.key) != entry) {
                parts.forEach(StaticMeshCache::closePart);
                return false;
            }
            entry.parts = parts;
            entry.bytes = bytes;
            entry.capturedGeneration = capturedGeneration;
            entry.failures = 0;
            if (!admit(entry)) {
                parts.forEach(StaticMeshCache::closePart);
                entry.parts = null;
                entry.bytes = 0;
                return false;
            }
            capturedBytesInFrame += bytes;
            capturesTotal++;
            return true;
        } catch (RuntimeException exception) {
            parts.forEach(StaticMeshCache::closePart);
            entry.parts = null;
            entry.bytes = 0;
            disableOwner(entry.key.owner(), "capture " + key, exception);
            return false;
        }
    }

    private static void closePart(Part part) {
        residentBytes -= part.residentBytes;
        part.residentBytes = 0;
        if (part.buffer != null) {
            part.buffer.close();
            part.buffer = null;
        }
        if (!part.upload.isDone()) {
            part.upload.cancel(false);
        }
    }

    private static boolean admit(Entry entry) {
        if (entry.reserved) return true;
        while (reservedBytes > 0 && entry.bytes > POLICY.residentBytes() - reservedBytes) {
            if (!RESIDENCY.reclaimIdle()) return false;
        }
        // 至少允许一个完整几何单独驻留，避免大模型上传部分 Part 后永久等待。
        reservedBytes += entry.bytes;
        entry.reserved = true;
        return true;
    }

    public static boolean layoutSupported(Entry entry) {
        // 26.2 下持久 VBO + CPU 补丁无属性索引上限约束；格式存在即可绘制。
        for (Part part : entry.parts) {
            if (part.requestedFormat == null || part.buffer == null) return false;
        }
        return true;
    }

    public static void retire(Entry entry, boolean invalidated) {
        if (entry.reserved) {
            reservedBytes -= entry.bytes;
            entry.reserved = false;
        }
        if (entry.parts == null) return;
        for (Part part : entry.parts) closePart(part);
        entry.parts = null;
        entry.bytes = 0;
    }

    @ApiStatus.Internal
    public static void onRenderTick(RenderTickEvent event) {
        if (event.phase != RenderTickEvent.Phase.START) return;
        beginFrame();
    }

    /**
     * 帧开始重置预算；26.2 下上传同步完成，无跨帧队列可排。
     */
    public static void beginFrame() {
        RenderSystem.assertOnRenderThread();
        frame++;
        capturesInFrame = 0;
        captureNanosInFrame = capturedBytesInFrame = 0;
        ClientLevel current = Minecraft.getInstance().level;
        if (current != level) {
            clear();
            level = current;
        }
        if (current == null) return;
        RESIDENCY.beginFrame(frame);
    }

    @ApiStatus.Internal
    public static void onLevelUnload(Level unloadedLevel) {
        if (!unloadedLevel.isClientSide()) return;
        Runnable action = () -> {
            if (unloadedLevel == level) {
                clear();
                level = null;
            }
        };
        if (RenderSystem.isOnRenderThread()) {
            action.run();
        } else {
            RenderSystem.queueFencedTask(action::run);
        }
    }

    /**
     * 失效指定接入方的缓存几何，并取消引用这些几何的待绘制命令。
     */
    public static void invalidateOwner(Identifier owner) {
        RenderSystem.assertOnRenderThread();
        Objects.requireNonNull(owner, "owner");
        List<Entry> retiring = CACHE.values().stream().filter(entry -> entry.key.owner().equals(owner)).toList();
        for (Entry entry : retiring) {
            discard(entry);
        }
    }

    /**
     * 资源重载或渲染上下文丢失时，失效全部物品网格。
     */
    public static void clear() {
        RenderSystem.assertOnRenderThread();
        generation++;
        FAILURES.reset();
        RESIDENCY.clear();
        for (Entry entry : List.copyOf(CACHE.values())) {
            retire(entry, true);
        }
        CACHE.clear();
        POOL.clear();
        probedFormat = null;
        formatCheckCountdown = FORMAT_CHECK_DRAWS;
    }
}
