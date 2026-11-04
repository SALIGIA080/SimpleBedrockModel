package com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.runtime;

import com.github.mcmodderanchor.simplebedrockmodel.v1.common.model.LocatorData;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.tree.ICube;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.tree.TreeBedrockModel;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.tree.TreeBoneDefinition;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.Map;

public class TreeModelInstance extends BoneTreeInstance {
    private final TreeBedrockModel baseModel;

    public TreeModelInstance(TreeBedrockModel baseModel) {
        super(baseModel.bones(), baseModel.getBindPose());
        this.baseModel = baseModel;
    }

    public TreeBedrockModel baseModel() {
        return baseModel;
    }

    @Environment(EnvType.CLIENT)
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay) {
        baseModel.renderToBuffer(this, poseStack, buffer, packedLight, packedOverlay);
    }

    @Environment(EnvType.CLIENT)
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay,
                               float red, float green, float blue, float alpha) {
        renderToBuffer(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha, false);
    }

    @Environment(EnvType.CLIENT)
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay,
                               float red, float green, float blue, float alpha, boolean skipNormalVisibilityCull) {
        baseModel.renderToBuffer(this, poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha, skipNormalVisibilityCull);
    }

    @Environment(EnvType.CLIENT)
    public void renderToBuffer(PoseStack poseStack, SubmitNodeCollector bufferSource, RenderType quadRenderType,
                               RenderType triangleRenderType, int packedLight, int packedOverlay) {
        baseModel.renderToBuffer(this, poseStack, bufferSource, quadRenderType, triangleRenderType, packedLight, packedOverlay);
    }

    @Environment(EnvType.CLIENT)
    public void renderToBuffer(PoseStack poseStack, SubmitNodeCollector bufferSource, RenderType quadRenderType,
                               RenderType triangleRenderType, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        renderToBuffer(poseStack, bufferSource, quadRenderType, triangleRenderType, packedLight, packedOverlay, red, green, blue, alpha, false);
    }

    @Environment(EnvType.CLIENT)
    public void renderToBuffer(PoseStack poseStack, SubmitNodeCollector bufferSource, RenderType quadRenderType,
                               RenderType triangleRenderType, int packedLight, int packedOverlay, float red, float green, float blue, float alpha,
                               boolean skipNormalVisibilityCull) {
        baseModel.renderToBuffer(this, poseStack, bufferSource, quadRenderType, triangleRenderType, packedLight, packedOverlay, red, green, blue, alpha, skipNormalVisibilityCull);
    }

    @Environment(EnvType.CLIENT)
    public void renderSingleBonePass(PoseStack poseStack, int boneIndex, VertexConsumer buffer, int packedLight, int packedOverlay,
                                     float red, float green, float blue, float alpha, boolean quadsPass, boolean skipNormalVisibilityCull) {
        poseStack.pushPose();
        mulParentGlobalTransform(poseStack, boneIndex);
        baseModel.renderBone(this, boneIndex, poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha,
                quadsPass, skipNormalVisibilityCull);
        poseStack.popPose();
    }

    @Environment(EnvType.CLIENT)
    public void renderSingleBone(PoseStack poseStack, int boneIndex, SubmitNodeCollector bufferSource, RenderType quadRenderType,
                                 RenderType triangleRenderType, int packedLight, int packedOverlay, float red, float green, float blue,
                                 float alpha, boolean skipNormalVisibilityCull) {
        poseStack.pushPose();
        mulParentGlobalTransform(poseStack, boneIndex);
        bufferSource.submitCustomGeometry(poseStack, quadRenderType, (entryPose, consumer) ->
                baseModel.renderBone(this, boneIndex, poseStack, consumer, packedLight, packedOverlay,
                        red, green, blue, alpha, true, skipNormalVisibilityCull));
        bufferSource.submitCustomGeometry(poseStack, triangleRenderType, (entryPose, consumer) ->
                baseModel.renderBone(this, boneIndex, poseStack, consumer, packedLight, packedOverlay,
                        red, green, blue, alpha, false, skipNormalVisibilityCull));
        poseStack.popPose();
    }

    @Override
    public int getIndex(String boneName) {
        return baseModel.getIndex(boneName);
    }

    @Override
    protected void rayTraceCubes(ModelRayTracer tracer) {
        for (TreeBoneDefinition definition : baseModel.bones()) {
            if (definition.ownCubeBounds() == null) {
                continue;
            }
            BoneState bone = getBone(definition.index());
            if (bone == null || !bone.visible) {
                continue;
            }
            Matrix4f boneTransform = getGlobalTransform(definition.index());
            if (!tracer.traceGroup(definition.ownCubeBounds(), boneTransform)) {
                continue;
            }
            ICube[] cubes = definition.cubes();
            for (int cubeIndex = 0; cubeIndex < cubes.length; cubeIndex++) {
                ICube cube = cubes[cubeIndex];
                tracer.traceCube(definition.index(), cubeIndex, cube.x(), cube.y(), cube.z(), cube.width(), cube.height(), cube.depth(),
                        boneTransform, cubeLocalTransform(cube));
            }
        }
    }

    private static Matrix4f cubeLocalTransform(ICube cube) {
        Matrix4f transform = new Matrix4f();
        if (cube.hasRotation()) {
            float[] pivot = cube.pivot();
            transform.translate(pivot[0], pivot[1], pivot[2]);
            transform.rotate(cube.rotation());
            transform.translate(-pivot[0], -pivot[1], -pivot[2]);
        }
        return transform;
    }

    @Nullable
    public Matrix4f getLocatorTransform(String locatorName) {
        Map.Entry<Integer, LocatorData> locator = baseModel.locator(locatorName);
        if (locator == null) return null;
        Matrix4f transform = new Matrix4f(getGlobalTransform(locator.getKey()));
        TreeBedrockModel.applyLocatorLocalTransform(transform, locator.getValue());
        return transform;
    }

    @Nullable
    public Matrix4f getQueryTransform(String queryName) {
        BoneState bone = getBone(queryName);
        return bone == null ? null : getGlobalTransform(bone.index());
    }
}
