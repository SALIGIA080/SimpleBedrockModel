package com.github.mcmodderanchor.simplebedrockmodel.v1.mixin.client;

import com.github.mcmodderanchor.simplebedrockmodel.v1.client.event.RenderItemInHandBobEvent;
import com.github.mcmodderanchor.simplebedrockmodel.v1.client.event.RenderLevelBobEvent;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Unique
    private boolean sbm$useFovSetting;

    /**
     * 26.2 里原 {@code getFov} 已被删除（FOV 迁至 {@code Camera#calculateFov/calculateHudFov}），
     * 原先靠「getFov 时 pUseFovSetting 是否为 true」来区分「当前在渲染世界还是手持」的 hack 失效。
     * 改用在 {@code renderItemInHand} 的 HEAD/RETURN 边界维护标志位，语义等价且更直接。
     */
    @Inject(method = "renderItemInHand", at = @At("HEAD"))
    private void sbm$beginHandPass(CameraRenderState cameraState, float partialTick, Matrix4fc projection, CallbackInfo ci) {
        this.sbm$useFovSetting = true;
    }

    @Inject(method = "renderItemInHand", at = @At("RETURN"))
    private void sbm$endHandPass(CameraRenderState cameraState, float partialTick, Matrix4fc projection, CallbackInfo ci) {
        this.sbm$useFovSetting = false;
    }

    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    public void onBobHurt(CameraRenderState cameraState, PoseStack poseStack, CallbackInfo ci) {
        boolean cancel;
        if (!sbm$useFovSetting) {
            var event = new RenderItemInHandBobEvent.BobHurt();
            RenderItemInHandBobEvent.BOB_HURT.invoker().post(event);
            cancel = event.isCanceled();
        } else {
            var event = new RenderLevelBobEvent.BobHurt();
            RenderLevelBobEvent.BOB_HURT.invoker().post(event);
            cancel = event.isCanceled();
        }
        if (cancel) {
            ci.cancel();
        }
    }

    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    public void onBobView(CameraRenderState cameraState, PoseStack poseStack, CallbackInfo ci) {
        boolean cancel;
        if (!sbm$useFovSetting) {
            var event = new RenderItemInHandBobEvent.BobView();
            RenderItemInHandBobEvent.BOB_VIEW.invoker().post(event);
            cancel = event.isCanceled();
        } else {
            var event = new RenderLevelBobEvent.BobView();
            RenderLevelBobEvent.BOB_VIEW.invoker().post(event);
            cancel = event.isCanceled();
        }
        if (cancel) {
            ci.cancel();
        }
    }
}
