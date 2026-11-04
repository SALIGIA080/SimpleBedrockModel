package com.github.mcmodderanchor.simplebedrockmodel.v1.resource;

import com.github.mcmodderanchor.simplebedrockmodel.SimpleBedrockModel;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.animation.BedrockAnimation;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.model.BedrockModel;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo.BedrockAnimationFile;
import com.google.common.collect.Maps;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.UnmodifiableView;

import javax.annotation.ParametersAreNonnullByDefault;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class BedrockAnimationResourceSet extends SimplePreparableReloadListener<Map<Identifier, BedrockAnimationFile>> implements IdentifiableResourceReloadListener {
    private final Map<Identifier, BedrockAnimationResourceProcessor> processors;
    private final List<Consumer<Map<Identifier, List<BedrockAnimation>>>> listeners;
    private final Map<Identifier, List<BedrockAnimation>> animationCache;

    public static final Identifier ID = SimpleBedrockModel.modLoc("bedrock_animation_resource_set");

    static BedrockAnimationResourceSet INSTANCE;

    public static BedrockAnimationResourceSet getInstance() {
        return INSTANCE;
    }

    BedrockAnimationResourceSet(Map<Identifier, BedrockAnimationResourceProcessor> processors,
                                List<Consumer<Map<Identifier, List<BedrockAnimation>>>> listeners) {
        this.processors = processors;
        this.listeners = listeners;
        this.animationCache = Maps.newHashMap();
    }

    @Override
    @NotNull
    @ParametersAreNonnullByDefault
    protected Map<Identifier, BedrockAnimationFile> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<Identifier, BedrockAnimationFile> pojoMap = new HashMap<>();
        processors.forEach((location, processor) -> {
            // 将 ID 转换成实际动画文件路径： <namespace>:animations/<path>.json
            Identifier path = Identifier.fromNamespaceAndPath(location.getNamespace(), "animations/" + location.getPath() + ".json");
            resourceManager.getResource(path).ifPresentOrElse(resource -> {
                try (InputStream stream = resource.open()) {
                    BedrockAnimationFile pojo = processor.rawLoader().load(stream, BedrockAnimationFile.class);
                    if (pojo != null) {
                        pojoMap.put(location, pojo);
                    }
                } catch (IOException e) {
                    SimpleBedrockModel.LOGGER.error("Failed to load animation file: {}", path, e);
                }
            }, () -> SimpleBedrockModel.LOGGER.error("Not found animation file: {}", path));
        });
        return pojoMap;
    }

    @Override
    @ParametersAreNonnullByDefault
    protected void apply(Map<Identifier, BedrockAnimationFile> pojoMap, ResourceManager pResourceManager, ProfilerFiller pProfiler) {
        animationCache.clear();
        processors.forEach((location, processor) -> {
            BedrockAnimationFile pojo = pojoMap.get(location);
            if (pojo == null) {
                return;
            }
            Identifier modelKey = processor.modelKey();
            BedrockModel model = modelKey == null ? null : BedrockModelResourceSet.getInstance().getModel(modelKey);
            List<BedrockAnimation> animations = processor.converter().apply(pojo, model);
            if (animations != null) {
                animationCache.put(location, animations);
            }
        });
        // 通知所有监听重载的 listener
        Map<Identifier, List<BedrockAnimation>> animationMap = getAllAnimations();
        for (Consumer<Map<Identifier, List<BedrockAnimation>>> listener : listeners) {
            listener.accept(animationMap);
        }
    }

    public List<BedrockAnimation> getAnimations(Identifier location) {
        return animationCache.get(location);
    }

    @UnmodifiableView
    public Map<Identifier, List<BedrockAnimation>> getAllAnimations() {
        return Collections.unmodifiableMap(animationCache);
    }

    @Override
    public Identifier getFabricId() {
        return ID;
    }
}
