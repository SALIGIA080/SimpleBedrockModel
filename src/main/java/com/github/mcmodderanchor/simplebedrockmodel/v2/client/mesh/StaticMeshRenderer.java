package com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh;

import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.cache.MeshCachePolicy;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.cache.StaticMeshCache;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.capture.MeshGeometryProvider;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.debug.MeshRenderDebug;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.gpu.MeshIntegerAttributes;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.render.MeshBatchRenderer;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.render.MeshDrawTransform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Objects;

import static com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.cache.StaticMeshCache.*;

/**
 * 临时共享静态网格的即时绘制入口，以及显式渲染阶段批次入口。
 *
 * <p><b>26.2 迁移</b>：实例光照从「整数参数表 + divisor 重绑定」降级为
 * 「CPU 顶点副本补丁 + writeToBuffer 整段回写」，仅在实例光照实际变化时回写
 * （光照值量化，同一位置变化频率低）。overlay（UV1）保持捕获烘焙值——
 * 受击闪白等逐实例 overlay 变化不再体现，与 TACZ 的 GPU 烘焙路径一致。</p>
 */
@Environment(EnvType.CLIENT)
public class StaticMeshRenderer {
    public enum BatchOrder {
        SUBMISSION,
        MATERIAL
    }

    public StaticMeshRenderer() {
    }

    /**
     * 阶段所有者负责协调原版缓冲，并在改变阶段状态前关闭批次。
     */
    public static StaticMeshBufferSource openBatch(Identifier owner, BatchOrder order,
                                                   java.util.function.BooleanSupplier isCurrent) {
        RenderSystem.assertOnRenderThread();
        if (MeshRenderDebug.attributes != null) throw new IllegalStateException("Batch uses production attributes");
        return new StaticMeshBufferSource(Objects.requireNonNull(owner), Objects.requireNonNull(order),
                Objects.requireNonNull(isCurrent));
    }

    public static StaticMeshBufferSource openBatch(Identifier owner, BatchOrder order) {
        return openBatch(owner, order, () -> true);
    }

    public static boolean supportsInstanceAttributes(int packedLight, int packedOverlay) {
        return StaticMeshCache.supportsInstanceAttributes(packedLight, packedOverlay);
    }

    public static void invalidateOwner(Identifier owner) {
        StaticMeshCache.invalidateOwner(owner);
    }

    public static boolean isOwnerEnabled(Identifier owner) {
        return StaticMeshCache.isOwnerEnabled(owner);
    }

    public static StaticMeshCache.PreparationStats preparationStats() {
        return StaticMeshCache.preparationStats();
    }

    public static MeshDrawResult tryDraw(Identifier owner, Object key, MeshGeometryProvider provider,
                                         PoseStack pose, int light, int overlay) {
        return tryDraw(owner, key, null, provider, pose, light, overlay, MeshCachePolicy.DEFAULT.idleRetention());
    }

    /**
     * 在当前调用中准备网格，旧几何只查询；PENDING 接管本次调用，也包括部分绘制失败。
     */
    public static MeshDrawResult tryDraw(Identifier owner, Object key, @Nullable Object previousKey,
                                         MeshGeometryProvider provider, PoseStack pose, int light, int overlay,
                                         Duration retention) {
        RenderSystem.assertOnRenderThread();
        Objects.requireNonNull(owner, "owner");
        if (!isOwnerEnabled(owner)) return MeshDrawResult.UNSUPPORTED;
        if (!prepareRequest(key, provider, pose, light, overlay, retention)) return MeshDrawResult.UNSUPPORTED;
        Entry target = acquire(new CacheKey(owner, key));
        Entry previous = previousKey == null || previousKey.equals(key) ? null : CACHE.get(new CacheKey(owner, previousKey));
        if (previous != null) pin(previous);
        MeshDrawResult result;
        try {
            Preparation prepared = prepareTarget(target, provider);
            if (prepared == Preparation.READY) {
                // 开始绘制后不能尝试其他网格，因为可能已经提交了部分绘制。
                result = draw(target, pose, light, overlay) ? MeshDrawResult.DRAWN : MeshDrawResult.PENDING;
            } else if (previous != null && previous.ready() && layoutSupported(previous)) {
                result = draw(previous, pose, light, overlay) ? MeshDrawResult.DRAWN_PREVIOUS : MeshDrawResult.PENDING;
            } else {
                result = prepared == Preparation.UNSUPPORTED ? MeshDrawResult.UNSUPPORTED : MeshDrawResult.PENDING;
            }
        } catch (RuntimeException failure) {
            releaseEntry(target, retention);
            if (previous != null) releaseEntry(previous, retention);
            disableOwner(owner, "immediate request " + key, failure);
            if (MeshRenderDebug.attributes != null || failure.getSuppressed().length != 0) throw failure;
            return MeshDrawResult.PENDING;
        }
        releaseEntry(target, retention);
        if (previous != null) releaseEntry(previous, retention);
        return result;
    }

    public static boolean draw(Entry entry, PoseStack pose, int packedLight, int packedOverlay) {
        try {
            return drawParts(entry, pose, packedLight, packedOverlay);
        } catch (RuntimeException exception) {
            disableOwner(entry.key.owner(), "immediate draw " + entry.key.geometry(), exception);
            if (exception.getSuppressed().length != 0) throw exception;
            return false;
        }
    }

    /**
     * instanceLight 网格在实例光照变化时补丁全部顶点 UV2 并整段回写。
     */
    public static void prepareLight(Part part, int packedLight) {
        if (!part.instanceLight || part.patchedLight == packedLight) return;
        if (part.vertexData == null || part.uv2Offset < 0 || part.buffer == null) return;
        int stride = part.requestedFormat.getVertexSize();
        MeshIntegerAttributes.patchLight(part.vertexData, stride, part.uv2Offset, 0,
                part.vertexData.capacity() / stride, packedLight);
        ByteBuffer payload = part.vertexData.duplicate();
        payload.clear();
        RenderSystem.getDevice().createCommandEncoder()
                .writeToBuffer(part.buffer.slice(), payload);
        part.patchedLight = packedLight;
    }

    public static boolean drawParts(Entry entry, PoseStack pose, int packedLight, int packedOverlay) {
        MeshDrawTransform transform = new MeshDrawTransform(RenderSystem.getModelViewMatrixCopy(), pose.last().pose());
        MeshBatchRenderer batch = new MeshBatchRenderer();
        try {
            for (Part part : entry.parts) {
                batch.beginMaterial(part.material, transform.modelView);
                prepareLight(part, packedLight);
                batch.bind(part.buffer, part.indexCount, part.topology);
                batch.drawItem(transform);
                batch.finishMaterial();
            }
            batch.close();
        } catch (RuntimeException failure) {
            batch.abort(failure);
            throw failure;
        }
        return true;
    }

}
