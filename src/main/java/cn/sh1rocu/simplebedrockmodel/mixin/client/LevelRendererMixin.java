package cn.sh1rocu.simplebedrockmodel.mixin.client;

import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.world.WorldMeshRenderer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.2 世界网格入口。
 *
 * <p>旧版 {@code RenderLevelStageEvent}/{@code WorldRenderEvents} 在 26.2 没有公开等价物；
 * 这里先从 {@code LevelRenderer.render} 的返回处驱动 SBM 持久世界网格，输入只取
 * {@link CameraRenderState} 已公开提供的相机位置、剔除截锥体和 view rotation。</p>
 *
 * @author SALIGIA080 (26.2 Fabric port)
 */
@Environment(EnvType.CLIENT)
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Inject(method = "render", at = @At("RETURN"))
    private void sbm$renderWorldMeshes(GraphicsResourceAllocator resourceAllocator, DeltaTracker deltaTracker,
                                       boolean renderBlockOutline, CameraRenderState cameraRenderState,
                                       Matrix4fc frustumMatrix, GpuBufferSlice gpuBufferSlice,
                                       Vector4f clearColor, boolean isFrustumCaptured, CallbackInfo ci) {
        if (cameraRenderState == null || !cameraRenderState.initialized || cameraRenderState.cullFrustum == null) {
            return;
        }
        WorldMeshRenderer.onRenderStage(cameraRenderState.pos, cameraRenderState.cullFrustum,
                cameraRenderState.viewRotationMatrix);
    }
}
