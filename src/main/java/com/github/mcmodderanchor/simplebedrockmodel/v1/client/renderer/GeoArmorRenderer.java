package com.github.mcmodderanchor.simplebedrockmodel.v1.client.renderer;

import com.github.mcmodderanchor.simplebedrockmodel.v1.client.handler.FirstPersonArmorHandler;
import com.github.mcmodderanchor.simplebedrockmodel.v1.client.model.BedrockArmorModel;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.model.BedrockBone;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// 说是模型，实际上是一个适配器，用来敷衍原版的）
// 26.2：不再继承 HumanoidModel（其泛型与 baby 字段体系彻底重构），改为独立适配器，
// baby 缩放参数从传入的 original HumanoidModel 与 livingEntity 动态读取。
public class GeoArmorRenderer implements IFPArmorHandRenderer, ICustomArmorRenderer {
    protected final BedrockArmorModel model;
    private final Identifier texture;

    @Nullable
    protected LivingEntity livingEntity;
    @Nullable
    protected ItemStack itemStack;
    @Nullable
    protected EquipmentSlot equipmentSlot;
    @Nullable
    protected HumanoidModel<?> original;

    public GeoArmorRenderer(BedrockArmorModel origin, Identifier texture) {
        this.model = origin;
        this.texture = texture;
    }

    public void preparePose(LivingEntity livingEntity, ItemStack itemStack, EquipmentSlot equipmentSlot, HumanoidModel<?> original) {
        model.applyPose(model.getBindPose());

        copyModelPart(original.head, model.getArmorHead(), 0, 24, 0);
        copyModelPart(original.body, model.getArmorBody(), 0, 24, 0);
        copyModelPart(original.rightArm, model.getArmorRightArm(), 5, 22, 0);
        copyModelPart(original.leftArm, model.getArmorLeftArm(), -5, 22, 0);
        copyModelPart(original.rightLeg, model.getArmorRightLeg(), 1.9f, 12, 0);
        copyModelPart(original.leftLeg, model.getArmorLeftLeg(), -1.9f, 12, 0);
        copyModelPart(original.rightLeg, model.getArmorRightBoot(), 1.9f, 12, 0);
        copyModelPart(original.leftLeg, model.getArmorLeftBoot(), -1.9f, 12, 0);

        setVisibilityBySlot(equipmentSlot);

        this.livingEntity = livingEntity;
        this.itemStack = itemStack;
        this.equipmentSlot = equipmentSlot;
        this.original = original;
    }

    public void copyModelPart(ModelPart part, BedrockBone bone, float initX, float initY, float initZ) {
        if (bone != null) {
            float deltaX = part.x - initX;
            float deltaY = part.y - initY;
            float deltaZ = part.z - initZ;

            bone.x += deltaX;
            bone.y += deltaY;
            bone.z += deltaZ;

            bone.rotation.rotationZYX(part.zRot, part.yRot, part.xRot);

            bone.xScale = -part.xScale;
            bone.yScale = -part.yScale;
            bone.zScale = part.zScale;
            bone.visible = part.visible;
        }
    }

    public void setVisibilityBySlot(EquipmentSlot slot) {
        setBoneVisible(model.getArmorHead(), slot == EquipmentSlot.HEAD);
        setBoneVisible(model.getArmorBody(), slot == EquipmentSlot.CHEST);
        setBoneVisible(model.getArmorRightArm(), slot == EquipmentSlot.CHEST);
        setBoneVisible(model.getArmorLeftArm(), slot == EquipmentSlot.CHEST);
        setBoneVisible(model.getArmorRightLeg(), slot == EquipmentSlot.LEGS);
        setBoneVisible(model.getArmorLeftLeg(), slot == EquipmentSlot.LEGS);
        setBoneVisible(model.getArmorRightBoot(), slot == EquipmentSlot.FEET);
        setBoneVisible(model.getArmorLeftBoot(), slot == EquipmentSlot.FEET);
    }

    public void setBoneVisible(BedrockBone bone, boolean visible) {
        if (bone != null) {
            bone.visible = visible;
        }
    }

    public void scaleModelForBaby(PoseStack poseStack, LivingEntity livingEntity, float partialTick, EquipmentSlot slot,
                                  HumanoidModel<?> original) {
        if (livingEntity == null || !livingEntity.isBaby())
            return;

        if (slot == EquipmentSlot.HEAD) {
            // 26.2 移除了 HumanoidModel 上的 scaleHead/babyHeadScale 等字段（改由 BABY_TRANSFORMER 处理），
            // 这里降级为固定缩放：头部放大 1.5 倍
            float headScale = 1.5f;
            poseStack.scale(headScale, headScale, headScale);
        } else {
            // 身体部分不做 baby 缩放（原版通过 BABY_TRANSFORMER 在构建期处理）
        }
    }

    /** 如果被非原版layer意外调了，还是直接用它的吧，不过rendertype是固定的
     * <br>
     *  需要正确实现{@link IForgeItem#getArmorTexture}
     * */
    public void renderToBuffer(PoseStack poseStack, @NotNull VertexConsumer buffer, int light, int overlay,
                               float r, float g, float b, float a) {
        float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);

        poseStack.pushPose();
        if (this.livingEntity != null && this.equipmentSlot != null && this.original != null) {
            scaleModelForBaby(poseStack, this.livingEntity, partialTick, this.equipmentSlot, this.original);
        }

        model.renderToBuffer(poseStack, buffer, light, overlay, r, g, b, a);
        poseStack.popPose();
    }

    @Override
    public void renderArmorToBuffer(PoseStack poseStack, SubmitNodeCollector bufferSource, int light, int overlay,
                                    float r, float g, float b, float a) {
        float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);

        poseStack.pushPose();
        if (this.livingEntity != null && this.equipmentSlot != null && this.original != null) {
            scaleModelForBaby(poseStack, this.livingEntity, partialTick, this.equipmentSlot, this.original);
        }

        model.submit(poseStack, bufferSource, getRenderType(this.getTexture()), getRenderType(this.getTexture()),
                light, overlay, r, g, b, a);
        poseStack.popPose();
    }

    public void afterRender(PoseStack poseStack, VertexConsumer buffer, int light, int overlay,
                            float r, float g, float b, float a) {
        this.livingEntity = null;
        this.itemStack = null;
        this.equipmentSlot = null;
        this.original = null;
    }

    @Override
    public void renderFirstPersonArmorArm(@NotNull AbstractClientPlayer player, @NotNull HumanoidArm arm, @NotNull PoseStack poseStack,
                                          @NotNull SubmitNodeCollector bufferSource, int packedLight) {
        BedrockBone armBone = arm == HumanoidArm.RIGHT
                ? this.model.getArmorRightArm()
                : this.model.getArmorLeftArm();
        if (armBone == null) {
            return;
        }

        poseStack.pushPose();
        poseStack.mulPose(FirstPersonArmorHandler.getGlobalTransform(armBone));
        // 26.2：用快照捕获单个骨骼的几何，再经 submitCustomGeometry 提交
        BedrockRenderSnapshot snapshot = BedrockRenderSnapshot.capture(
                armBone, poseStack, packedLight, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F, false);
        if (!snapshot.isEmpty()) {
            PoseStack identity = new PoseStack();
            bufferSource.submitCustomGeometry(identity, getRenderType(getTexture()),
                    (entryPose, consumer) -> snapshot.write(consumer));
        }
        poseStack.popPose();
    }

    public RenderType getRenderType(Identifier texture) {
        return RenderTypes.armorCutoutNoCull(texture);
    }

    public Identifier getTexture() {
        return this.texture;
    }

    @Nullable
    public EquipmentSlot getEquipmentSlot() {
        return this.equipmentSlot;
    }

    public BedrockArmorModel getModel() {
        return this.model;
    }
}
