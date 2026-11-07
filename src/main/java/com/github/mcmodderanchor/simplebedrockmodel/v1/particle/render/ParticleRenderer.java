package com.github.mcmodderanchor.simplebedrockmodel.v1.particle.render;

import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.ParticleDescription;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component.ParticleAppearanceBillboard;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component.ParticleAppearanceLighting;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.runtime.ParticleEmitterInstance;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.runtime.ParticleInstance;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;

import org.jetbrains.annotations.Nullable;
import java.util.List;

/**
 * 粒子渲染器。将发射器中的局部空间粒子渲染到 PoseStack 局部坐标系中。
 * <p>
 * 世界空间粒子由原版 {@code ParticleEngine} 渲染，不经过此渲染器。
 */
@Environment(EnvType.CLIENT)
public final class ParticleRenderer {

    private ParticleRenderer() {}

    /**
     * 渲染一个发射器的所有局部空间粒子。
     * <p>
     * 发射器变换矩阵从 emitter 实例上读取。粒子坐标在发射器局部空间中，
     * 渲染时叠加发射器变换。
     *
     * @param cameraPitch    摄像机 pitch（弧度）
     * @param cameraRoll     摄像机 roll（弧度）
     * @param cameraRotation 摄像机旋转矩阵（世界对齐空间 → 视图空间），
     *                       用于 fpDetached 粒子。可为 null。
     */
    public static void render(ParticleEmitterInstance emitter, PoseStack poseStack,
                               SubmitNodeCollector bufferSource, int light, float partialTick,
                               float cameraPitch, float cameraRoll,
                               @Nullable Matrix4f cameraRotation) {
        List<ParticleInstance> particles = emitter.getParticles();
        if (particles.isEmpty()) return;

        var definition = emitter.getDefinition();
        ParticleDescription desc = definition.getDescription();
        Identifier texture = desc.getTexture();

        // 选择 RenderType
        RenderType renderType = getRenderType(desc.getMaterial(), texture);

        // 确定朝向模式
        ParticleAppearanceBillboard billboard = definition.findComponent(ParticleAppearanceBillboard.class);
        ParticleAppearanceBillboard.FaceCameraMode mode = billboard != null
                ? billboard.faceCameraMode()
                : ParticleAppearanceBillboard.FaceCameraMode.ROTATE_XYZ;

        Matrix4f emitterTransform = emitter.getEmitterTransform();
        boolean localPos = emitter.isLocalPosition();
        boolean localRot = emitter.isLocalRotation();

        // 光照：存在 particle_appearance_lighting 组件时按环境光照（传入 light）着色，否则自发光恒满亮度
        boolean hasLighting = definition.findComponent(ParticleAppearanceLighting.class) != null;
        int particleLight = hasLighting ? light : 15728880;

        // 26.2：SubmitNodeCollector 无 getBuffer，几何经 submitCustomGeometry 提交（同步回调）。
        // 【火焰「粘连」修复】BillboardHelper 已把顶点变换到「世界轴空间」（poseStack 的
        // pose 作用后的最终空间，含 viewRotationMatrix.invert()），并用手动变换后的坐标写顶点。
        // 若这里仍传非 identity 的 poseStack，collector 会用 entryPose（= poseStack 的 pose）
        // 把这些「已在世界轴空间」的顶点再变换一次 → 双重变换 → 火焰 billboard 位置/朝向错乱
        // （表现为「粘连」）。对齐 BedrockModel.submit 的做法：顶点已含完整 pose，用 identity
        // stack 提交，避免 entryPose 二次变换。
        PoseStack identityStack = new PoseStack();
        bufferSource.submitCustomGeometry(identityStack, renderType, (entryPose, consumer) -> {
            for (ParticleInstance particle : particles) {
                BillboardHelper.renderBillboard(particle, poseStack, consumer, particleLight, mode,
                        emitterTransform, localPos, localRot, cameraPitch, cameraRoll, cameraRotation);
            }
        });
    }

    /**
     * 根据材质类型选择 RenderType。
     */
    private static RenderType getRenderType(ParticleDescription.Material material, Identifier texture) {
        return switch (material) {
            case PARTICLES_OPAQUE -> RenderTypes.entityCutout(texture);
            case PARTICLES_ALPHA -> RenderTypes.entityTranslucent(texture);
            case PARTICLES_BLEND -> RenderTypes.entityTranslucent(texture);
            case PARTICLES_ADD -> ParticleRenderType.additiveParticle(texture);
            case ENERGY_SWIRL -> RenderTypes.energySwirl(texture, 1, 1);
            case PARTICLES_EMISSIVE -> ParticleRenderType.emissiveParticle(texture);
        };
    }
}
