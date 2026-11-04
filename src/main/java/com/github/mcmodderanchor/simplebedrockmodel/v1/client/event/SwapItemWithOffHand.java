package com.github.mcmodderanchor.simplebedrockmodel.v1.client.event;

import cn.sh1rocu.simplebedrockmodel.api.event.BaseEvent;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

public class SwapItemWithOffHand extends BaseEvent {

    public static final Event<Callback> EVENT = EventFactory.createArrayBacked(Callback.class, callbacks -> event -> {
        for (Callback callback : callbacks) {
            callback.post(event);
        }
    });

    public SwapItemWithOffHand() {

    }

    public interface Callback {
        void post(SwapItemWithOffHand event);
    }
}