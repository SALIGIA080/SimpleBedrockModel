package com.github.mcmodderanchor.simplebedrockmodel.v1.event;

import cn.sh1rocu.simplebedrockmodel.api.event.BaseEvent;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.model.BedrockModel;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class RegisterBedrockModelReloadListenerEvent extends BaseEvent {
    private final List<Consumer<Map<Identifier, BedrockModel>>> listeners = new ArrayList<>();

    public static final Event<Callback> EVENT = EventFactory.createArrayBacked(Callback.class, callbacks -> event -> {
        for (Callback callback : callbacks) {
            callback.post(event);
        }
    });

    public void register(Consumer<Map<Identifier, BedrockModel>> listener) {
        this.listeners.add(listener);
    }

    public List<Consumer<Map<Identifier, BedrockModel>>> getListeners() {
        return listeners;
    }

    public interface Callback {
        void post(RegisterBedrockModelReloadListenerEvent event);
    }
}
