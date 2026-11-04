package com.github.mcmodderanchor.simplebedrockmodel.v1.client.model;

import com.github.mcmodderanchor.simplebedrockmodel.v1.common.model.BedrockBone;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.model.BedrockCubePerFace;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo.FaceItem;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo.FaceUVsItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * 26.2: EntityModel 现在要求 EntityRenderState 类型参数、Model 要求 ModelPart 构造器。
 * SlotModel 只是一个简单的 quad 渲染器，因此改为独立类。
 */
public class SlotModel {
    private static final FaceItem EMPTY = new FaceItem(new float[]{0f, 0f}, new float[]{0f, 0f});
    private static final FaceItem X16 = new FaceItem(new float[]{0f, 0f}, new float[]{16f, 16f});
    private static final FaceUVsItem SINGLE_SOUTH_X16 = new FaceUVsItem(EMPTY, EMPTY, EMPTY, X16, EMPTY, EMPTY);
    private final BedrockBone bone;

    public SlotModel(boolean illuminated) {
        bone = new BedrockBone();
        bone.x = 8.0F;
        bone.y = 8.0F;
        bone.z = 8.0F;
        bone.cubes.add(new BedrockCubePerFace(-16.0F, -16.0F, 0F, 16.0F, 16.0F, 0, 0, 16, 16, SINGLE_SOUTH_X16));
        bone.updateGeometryFlags();
        bone.illuminated = illuminated;
    }

    public SlotModel() {
        this(false);
    }

    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay,
                               float red, float green, float blue, float alpha) {
        bone.render(poseStack, buffer, packedLight, packedOverlay);
    }
}
