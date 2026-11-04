package com.github.mcmodderanchor.simplebedrockmodel.v1.mixin.client;

import com.github.mcmodderanchor.simplebedrockmodel.v1.client.handler.FirstPersonRenderHandler;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
public class PlayerModelMixin extends HumanoidModel<AvatarRenderState> {
    @Shadow
    @Final
    public ModelPart leftSleeve;
    @Shadow
    @Final
    public ModelPart rightSleeve;

    public PlayerModelMixin(ModelPart part) {
        super(part);
    }

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At(value = "TAIL"))
    private void setRotationAnglesTail(AvatarRenderState renderState, CallbackInfo ci) {
        // 用于清除默认的手臂旋转
        // 当第一人称渲染时，ageInTicks 正好是 0
        var instance = FirstPersonRenderHandler.getActiveAnimationInstance(InteractionHand.MAIN_HAND);
        var instance2 = FirstPersonRenderHandler.getActiveAnimationInstance(InteractionHand.OFF_HAND);
        boolean rl = instance != null && instance.shouldRenderHand();
        boolean rl2 = instance2 != null && instance2.shouldRenderHand();
        if (renderState.ageInTicks == 0F && (rl || rl2)) {
            sbm$resetAll(this.rightArm);
            sbm$resetAll(this.leftArm);
        }
    }

    /**
     * 将给定模型的旋转角度和旋转点重置为零
     */
    @Unique
    private void sbm$resetAll(ModelPart part) {
        part.xRot = 0.0F;
        part.yRot = 0.0F;
        part.zRot = 0.0F;
    }
}
