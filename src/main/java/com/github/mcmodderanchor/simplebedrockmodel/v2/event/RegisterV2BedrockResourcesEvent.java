package com.github.mcmodderanchor.simplebedrockmodel.v2.event;

import cn.sh1rocu.simplebedrockmodel.api.event.BaseEvent;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.animation.BedrockAnimation;
import com.github.mcmodderanchor.simplebedrockmodel.v1.resource.RawResourceLoader;
import com.github.mcmodderanchor.simplebedrockmodel.v1.resource.RawResourceLoaders;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.baked.BakerOptions;
import com.github.mcmodderanchor.simplebedrockmodel.v2.resource.BedrockAnimationEntry;
import com.github.mcmodderanchor.simplebedrockmodel.v2.resource.BedrockAnimationFactory;
import com.github.mcmodderanchor.simplebedrockmodel.v2.resource.BedrockModelBakeContext;
import com.github.mcmodderanchor.simplebedrockmodel.v2.resource.BedrockModelEntry;
import com.github.mcmodderanchor.simplebedrockmodel.v2.resource.BedrockModelResource;
import com.github.mcmodderanchor.simplebedrockmodel.v2.resource.ModelType;
import com.google.common.collect.Maps;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

public class RegisterV2BedrockResourcesEvent extends BaseEvent {
    private final Map<Identifier, BedrockModelEntry> modelRegistry;
    private final Map<Identifier, BedrockAnimationEntry> animationRegistry;
    private final List<Consumer<Map<Identifier, BedrockModelResource>>> reloadListeners;
    private final EnvType dist;

    public static final Event<Callback> EVENT = EventFactory.createArrayBacked(Callback.class, callbacks -> event -> {
        for (Callback callback : callbacks) {
            callback.post(event);
        }
    });

    public RegisterV2BedrockResourcesEvent(EnvType dist) {
        this.modelRegistry = Maps.newHashMap();
        this.animationRegistry = Maps.newHashMap();
        this.reloadListeners = new ArrayList<>();
        this.dist = dist;
    }

    public ModelBuilder treeModel(Identifier modelId) {
        return treeModel(modelId, modelId);
    }

    public ModelBuilder treeModel(Identifier modelId, Identifier sourceId) {
        return treeModel(modelId, sourceId, RawResourceLoaders.COMMON_LOADER);
    }

    public ModelBuilder treeModel(Identifier modelId, Identifier sourceId, RawResourceLoader modelLoader) {
        return new ModelBuilder(modelId, sourceId, modelLoader, ModelType.TREE);
    }

    public ModelBuilder bakedModel(Identifier modelId) {
        return bakedModel(modelId, modelId);
    }

    public ModelBuilder bakedModel(Identifier modelId, Identifier sourceId) {
        return bakedModel(modelId, sourceId, RawResourceLoaders.COMMON_LOADER);
    }

    public ModelBuilder bakedModel(Identifier modelId, Identifier sourceId, RawResourceLoader modelLoader) {
        return new ModelBuilder(modelId, sourceId, modelLoader, ModelType.BAKED);
    }

    /**
     * @deprecated Use {@link #bakedModel(Identifier)} or {@link #treeModel(Identifier)} to make the
     * runtime model type explicit.
     */
    @Deprecated
    public ModelBuilder model(Identifier modelId) {
        return bakedModel(modelId);
    }

    /**
     * @deprecated Use {@link #bakedModel(Identifier, Identifier, RawResourceLoader)} or
     * {@link #treeModel(Identifier, Identifier, RawResourceLoader)} to make the runtime model type explicit.
     */
    @Deprecated
    public ModelBuilder model(Identifier modelId, RawResourceLoader modelLoader) {
        return bakedModel(modelId, modelId, modelLoader);
    }

    public void onReload(Consumer<Map<Identifier, BedrockModelResource>> listener) {
        reloadListeners.add(listener);
    }

    public EnvType getDist() {
        return dist;
    }

    public Map<Identifier, BedrockModelEntry> getModelRegistry() {
        return modelRegistry;
    }

    public Map<Identifier, BedrockAnimationEntry> getAnimationRegistry() {
        return animationRegistry;
    }

    public List<Consumer<Map<Identifier, BedrockModelResource>>> getReloadListeners() {
        return reloadListeners;
    }

    public interface Callback {
        void post(RegisterV2BedrockResourcesEvent event);
    }

    public final class ModelBuilder {
        private final Identifier modelId;
        private final Identifier sourceId;
        private final RawResourceLoader modelLoader;
        private final ModelType kind;
        private final LinkedHashMap<Identifier, AnimationRegistration> animations = new LinkedHashMap<>();
        private Function<BedrockModelBakeContext, BakerOptions> optionsFactory;
        private boolean lazy;

        private ModelBuilder(Identifier modelId, Identifier sourceId, RawResourceLoader modelLoader, ModelType kind) {
            this.modelId = modelId;
            this.sourceId = sourceId;
            this.modelLoader = modelLoader;
            this.kind = kind;
        }

        public ModelBuilder lazy() {
            this.lazy = true;
            return this;
        }

        public ModelBuilder options(BakerOptions options) {
            return options(context -> options);
        }

        public ModelBuilder options(Function<BedrockModelBakeContext, BakerOptions> optionsFactory) {
            this.optionsFactory = optionsFactory;
            return this;
        }

        public ModelBuilder animation(Identifier animationId) {
            return animation(animationId, RawResourceLoaders.COMMON_LOADER, BedrockAnimation::createAnimation);
        }

        public ModelBuilder animation(Identifier animationId, BedrockAnimationFactory factory) {
            return animation(animationId, RawResourceLoaders.COMMON_LOADER, factory);
        }

        public ModelBuilder animation(Identifier animationId, RawResourceLoader animationLoader, BedrockAnimationFactory factory) {
            animations.put(animationId, new AnimationRegistration(animationLoader, factory));
            return this;
        }

        public void register() {
            Function<BedrockModelBakeContext, BakerOptions> factory = optionsFactory != null
                    ? optionsFactory
                    : context -> animations.isEmpty() ? BakerOptions.defaults() : context.optionsFromAnimations();
            modelRegistry.put(modelId, new BedrockModelEntry(
                    modelLoader, sourceId, kind, factory, new ArrayList<>(animations.keySet()), lazy));
            for (Map.Entry<Identifier, AnimationRegistration> entry : animations.entrySet()) {
                AnimationRegistration animation = entry.getValue();
                animationRegistry.put(entry.getKey(), new BedrockAnimationEntry(
                        animation.loader(), modelId, animation.factory(), lazy, true));
            }
        }
    }

    private record AnimationRegistration(
            RawResourceLoader loader,
            BedrockAnimationFactory factory
    ) {
    }
}
