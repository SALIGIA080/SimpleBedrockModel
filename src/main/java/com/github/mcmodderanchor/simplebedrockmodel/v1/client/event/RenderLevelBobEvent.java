package com.github.mcmodderanchor.simplebedrockmodel.v1.client.event;

import cn.sh1rocu.simplebedrockmodel.api.event.BaseEvent;
import cn.sh1rocu.simplebedrockmodel.api.event.ICancellableEvent;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

/**
 * 当第一人称视角触发摇晃时，世界背景的摇晃
 */
public class RenderLevelBobEvent extends BaseEvent implements ICancellableEvent {
    public static final Event<BobHurt.Callback> BOB_HURT = EventFactory.createArrayBacked(BobHurt.Callback.class, callbacks -> event -> {
        for (BobHurt.Callback callback : callbacks) {
            callback.post(event);
        }
    });
    public static final Event<BobView.Callback> BOB_VIEW = EventFactory.createArrayBacked(BobView.Callback.class, callbacks -> event -> {
        for (BobView.Callback callback : callbacks) {
            callback.post(event);
        }
    });

    public static class BobHurt extends RenderLevelBobEvent {
        public BobHurt() {
        }

        public interface Callback {
            void post(BobHurt event);
        }
    }

    public static class BobView extends RenderLevelBobEvent {
        public BobView() {
        }

        public interface Callback {
            void post(BobView event);
        }
    }
}
