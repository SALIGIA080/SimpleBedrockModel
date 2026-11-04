package com.github.mcmodderanchor.simplebedrockmodel.v1.event;

import cn.sh1rocu.simplebedrockmodel.api.event.BaseEvent;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.animation.BedrockAnimation;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.model.BedrockModel;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo.BedrockAnimationFile;
import com.github.mcmodderanchor.simplebedrockmodel.v1.resource.BedrockAnimationResourceProcessor;
import com.github.mcmodderanchor.simplebedrockmodel.v1.resource.RawResourceLoader;
import com.google.common.collect.Maps;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Used to register bedrock animations so that loaders can load them.
 */
public class RegisterBedrockAnimationEvent extends BaseEvent {
    private final Map<Identifier, BedrockAnimationResourceProcessor> animationRegistry;
    private final EnvType envType;

    public static final Event<Callback> EVENT = EventFactory.createArrayBacked(Callback.class, callbacks -> event -> {
        for (Callback callback : callbacks) {
            callback.post(event);
        }
    });

    public RegisterBedrockAnimationEvent(EnvType envType) {
        this.animationRegistry = Maps.newHashMap();
        this.envType = envType;
    }

    public void register(Identifier animationLocation,
                         Identifier modelLocation,
                         RawResourceLoader loader,
                         BiFunction<BedrockAnimationFile, BedrockModel, List<BedrockAnimation>> converter) {
        animationRegistry.put(animationLocation, new BedrockAnimationResourceProcessor(loader, modelLocation, converter));
    }

    public void register(Identifier animationLocation,
                         Identifier modelLocation,
                         RawResourceLoader loader) {
        register(animationLocation, modelLocation, loader, BedrockAnimation::createAnimation);
    }


    public EnvType getEnvType() {
        return envType;
    }

    public Map<Identifier, BedrockAnimationResourceProcessor> getAnimationRegistry() {
        return animationRegistry;
    }

    public interface Callback {
        void post(RegisterBedrockAnimationEvent event);
    }
}
