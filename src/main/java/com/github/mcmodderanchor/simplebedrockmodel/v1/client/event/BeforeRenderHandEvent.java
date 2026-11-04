package com.github.mcmodderanchor.simplebedrockmodel.v1.client.event;

import cn.sh1rocu.simplebedrockmodel.api.event.BaseEvent;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

/**
 * 在调用 ItemInHandRenderer#renderHandsWithItems 方法时触发该事件
 * 用于相机动画相关调用
 */
public class BeforeRenderHandEvent extends BaseEvent {
    private final PoseStack poseStack;
    private final float partialTick;

    public static final Event<Callback> EVENT = EventFactory.createArrayBacked(Callback.class, callbacks -> event -> {
        for (Callback callback : callbacks) {
            callback.post(event);
        }
    });


    public BeforeRenderHandEvent(PoseStack poseStack, float partialTicks) {
        this.poseStack = poseStack;
        this.partialTick = partialTicks;
    }

    public PoseStack getPoseStack() {
        return poseStack;
    }

    public float getPartialTick() {
        return partialTick;
    }

    public interface Callback {
        void post(BeforeRenderHandEvent event);
    }
}
