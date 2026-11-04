package com.github.mcmodderanchor.simplebedrockmodel.v1.client.handler;

import cn.sh1rocu.simplebedrockmodel.api.event.ViewportEvent;
import com.github.mcmodderanchor.simplebedrockmodel.v1.client.event.BeforeRenderHandEvent;
import com.github.mcmodderanchor.simplebedrockmodel.v1.client.event.RenderItemInHandBobEvent;
import com.github.mcmodderanchor.simplebedrockmodel.v1.client.renderer.AbstractGeoItemRenderer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import cn.sh1rocu.simplebedrockmodel.compat.fabric.BuiltinItemRendererRegistry;
import com.github.mcmodderanchor.simplebedrockmodel.v1.client.renderer.IFPGeoItemRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.joml.*;

@Environment(EnvType.CLIENT)
public class CameraEventHandler {

    // 测试用
    public static Vector3f extractEulerAnglesYXZ(Matrix3fc matrix) {
        Vector3f euler = new Vector3f();

        float r32 = matrix.m21();
        float r12 = matrix.m01();
        float r22 = matrix.m11();
        float r31 = matrix.m20();
        float r33 = matrix.m22();

        final float EPSILON = 1e-4f;

        // Clamp r32 to the range [-1, 1] to avoid NaN from asin due to floating point inaccuracies
        r32 = org.joml.Math.clamp(r32, -1.0f, 1.0f);

        if (org.joml.Math.abs(r32) < 1.0f - EPSILON) {
            euler.x = org.joml.Math.asin(-r32);
            euler.y = org.joml.Math.atan2(r31, r33);
            euler.z = org.joml.Math.atan2(r12, r22);
        } else {
            if (org.joml.Math.abs(r31) < EPSILON && org.joml.Math.abs(r33) < EPSILON &&
                    org.joml.Math.abs(r12) < EPSILON && org.joml.Math.abs(r22) < EPSILON) {
                euler.x = (float) (r32 > 0 ? -org.joml.Math.PI / 2 : org.joml.Math.PI / 2);
                // when x ≈ -90°
                // r21 ≈ -(sin(y)cos(z) + cos(y)sin(z))
                // r11 ≈ cos(y)cos(z) - sin(y)sin(z)
                // when x ≈ 90°
                // r21 ≈ sin(y)cos(z) - cos(y)sin(z)
                // r11 ≈ cos(y)cos(z) + sin(y)sin(z)
                float r21 = matrix.m10();
                float r11 = matrix.m00();
                euler.z = (float) java.lang.Math.atan2(-r21, r11);
                euler.y = 0;
            } else {
                euler.x = (float) java.lang.Math.asin(-r32);
                euler.y = (float) java.lang.Math.atan2(r31, r33);
                euler.z = (float) java.lang.Math.atan2(r12, r22);
            }
        }

        return  euler;
    }

    public static Vector3fc asEulerAngle(Quaternionf quaternion) {
        Matrix3f m = new Matrix3f().set(quaternion);
        return extractEulerAnglesYXZ(m);
    }

    /**
     * 当主手拿着枪械物品的时候，取消应用在它上面的 viewBobbing，以便应用自定义的跑步/走路动画。
     */
    public static void cancelItemInHandViewBobbing(RenderItemInHandBobEvent.BobView event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        var instance = FirstPersonRenderHandler.getActiveAnimationInstanceForCamera();

        if (instance != null && BuiltinItemRendererRegistry.INSTANCE.get(instance.currentItem().getItem()) instanceof IFPGeoItemRenderer renderer) {
            event.setCanceled(renderer.blockViewBobbing());
        }
    }

    public static void applyLevelCameraAnimation(ViewportEvent.ComputeCameraAngles event) {
        if (!Minecraft.getInstance().options.bobView().get()) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        var instance = FirstPersonRenderHandler.getActiveAnimationInstanceForCamera();

        if (instance != null && BuiltinItemRendererRegistry.INSTANCE.get(instance.currentItem().getItem()) instanceof IFPGeoItemRenderer renderer) {
            renderer.applyLevelCameraAnimation(event, instance.currentItem(), instance.getCameraRotation(), (float) event.getPartialTick());
        }
    }

    public static void applyItemInHandCameraAnimation(BeforeRenderHandEvent event) {
        if (!Minecraft.getInstance().options.bobView().get()) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        var instance = FirstPersonRenderHandler.getActiveAnimationInstanceForCamera();

        if (instance != null && BuiltinItemRendererRegistry.INSTANCE.get(instance.currentItem().getItem()) instanceof IFPGeoItemRenderer renderer) {
            renderer.applyItemInHandCameraAnimation(event.getPoseStack(), instance.currentItem(), instance.getCameraRotation(), event.getPartialTick());
        }
    }
}
