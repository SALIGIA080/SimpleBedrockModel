package com.github.mcmodderanchor.simplebedrockmodel.v1.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.trim.ArmorTrim;

/**
 * 允许盔甲模型获取当前 {@link SubmitNodeCollector} 进行渲染
 * <p>
 * 只在通过{@link HumanoidArmorLayer}进行渲染时有效，默认不处理trim和glint
 */
public interface ICustomArmorRenderer {

    void renderArmorToBuffer(PoseStack poseStack, SubmitNodeCollector bufferSource, int packedLight, int packedOverlay,
                             float red, float green, float blue, float alpha);

    default void renderArmorTrimToBuffer(ArmorMaterial armorMaterial, PoseStack poseStack, SubmitNodeCollector bufferSource,
                                         int packedLight, ArmorTrim trim, boolean innerTexture) {
    }

    default void renderArmorGlintToBuffer(PoseStack poseStack, SubmitNodeCollector bufferSource, int packedLight) {
    }

    default boolean shouldRenderVanillaTrim() {
        return false;
    }

    default boolean shouldRenderVanillaGlint() {
        return false;
    }
}
