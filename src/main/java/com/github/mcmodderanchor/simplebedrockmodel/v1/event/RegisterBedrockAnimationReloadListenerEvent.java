package com.github.mcmodderanchor.simplebedrockmodel.v1.event;

import cn.sh1rocu.simplebedrockmodel.api.event.BaseEvent;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.animation.BedrockAnimation;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class RegisterBedrockAnimationReloadListenerEvent extends BaseEvent {
    private final List<Consumer<Map<Identifier, List<BedrockAnimation>>>> listeners = new ArrayList<>();

    public static final Event<Callback> EVENT = EventFactory.createArrayBacked(Callback.class, callbacks -> event -> {
        for (Callback callback : callbacks) {
            callback.post(event);
        }
    });

    public void register(Consumer<Map<Identifier, List<BedrockAnimation>>> listener) {
        this.listeners.add(listener);
    }

    public List<Consumer<Map<Identifier, List<BedrockAnimation>>>> getListeners() {
        return listeners;
    }

    public interface Callback {
        void post(RegisterBedrockAnimationReloadListenerEvent event);
    }
}
