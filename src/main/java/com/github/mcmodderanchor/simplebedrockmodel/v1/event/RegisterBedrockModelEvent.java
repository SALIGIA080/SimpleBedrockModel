package com.github.mcmodderanchor.simplebedrockmodel.v1.event;

import cn.sh1rocu.simplebedrockmodel.api.event.BaseEvent;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.model.BedrockModel;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo.BedrockModelPOJO;
import com.github.mcmodderanchor.simplebedrockmodel.v1.resource.BedrockModelResourceProcessor;
import com.github.mcmodderanchor.simplebedrockmodel.v1.resource.RawResourceLoader;
import com.google.common.collect.Maps;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.function.Function;

public class RegisterBedrockModelEvent extends BaseEvent {
    private final Map<Identifier, BedrockModelResourceProcessor> modelRegistry;
    private final EnvType envType;

    public static final Event<Callback> EVENT = EventFactory.createArrayBacked(Callback.class, callbacks -> event -> {
        for (Callback callback : callbacks) {
            callback.post(event);
        }
    });

    public RegisterBedrockModelEvent(EnvType envType) {
        this.modelRegistry = Maps.newHashMap();
        this.envType = envType;
    }

    public void register(Identifier modelLocation,
                         RawResourceLoader loader,
                         Function<BedrockModelPOJO, BedrockModel> converter) {
        modelRegistry.put(modelLocation, new BedrockModelResourceProcessor(loader, converter));
    }

    public void register(Identifier modelLocation,
                         RawResourceLoader loader) {
        register(modelLocation, loader, BedrockModel::new);
    }

    public EnvType getEnvType() {
        return envType;
    }

    public Map<Identifier, BedrockModelResourceProcessor> getModelRegistry() {
        return modelRegistry;
    }

    public interface Callback {
        void post(RegisterBedrockModelEvent event);
    }
}
