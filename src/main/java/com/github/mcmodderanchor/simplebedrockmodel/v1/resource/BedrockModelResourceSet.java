package com.github.mcmodderanchor.simplebedrockmodel.v1.resource;

import com.github.mcmodderanchor.simplebedrockmodel.SimpleBedrockModel;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.model.BedrockModel;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo.BedrockModelPOJO;
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
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class BedrockModelResourceSet extends SimplePreparableReloadListener<Map<Identifier, BedrockModelPOJO>> implements IdentifiableResourceReloadListener {
    private final Map<Identifier, BedrockModelResourceProcessor> processors;
    private final Map<Identifier, BedrockModel> modelCache;
    private final List<Consumer<Map<Identifier, BedrockModel>>> listeners;

    public static final Identifier ID = SimpleBedrockModel.modLoc("bedrock_model_resource_set");

    static BedrockModelResourceSet INSTANCE;

    public static BedrockModelResourceSet getInstance() {
        return INSTANCE;
    }

    BedrockModelResourceSet(Map<Identifier, BedrockModelResourceProcessor> processors,
                            List<Consumer<Map<Identifier, BedrockModel>>> listeners) {
        this.processors = processors;
        this.listeners = listeners;
        this.modelCache = Maps.newHashMap();
    }

    @Override
    @NotNull
    @ParametersAreNonnullByDefault
    protected Map<Identifier, BedrockModelPOJO> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<Identifier, BedrockModelPOJO> pojoMap = Maps.newHashMap();
        processors.forEach((location, processor) -> {
            // 将 ID 转换成实际模型文件路径：<namespace>:models/bedrock/<path>.json
            Identifier path = Identifier.fromNamespaceAndPath(location.getNamespace(), "models/bedrock/" + location.getPath() + ".json");
            resourceManager.getResource(path).ifPresentOrElse(resource -> {
                try (InputStream stream = resource.open()) {
                    BedrockModelPOJO pojo = processor.rawLoader().load(stream, BedrockModelPOJO.class);
                    if (pojo != null) {
                        pojoMap.put(location, pojo);
                    }
                } catch (IOException e) {
                    SimpleBedrockModel.LOGGER.error("Failed to load model file: {}", path, e);
                }
            }, () -> SimpleBedrockModel.LOGGER.error("Not found model file: {}", path));
        });
        return pojoMap;
    }

    @Override
    @ParametersAreNonnullByDefault
    protected void apply(Map<Identifier, BedrockModelPOJO> pojoMap, ResourceManager pResourceManager, ProfilerFiller pProfiler) {
        modelCache.clear();
        processors.forEach((location, processor) -> {
            BedrockModelPOJO pojo = pojoMap.get(location);
            if (pojo == null) {
                return;
            }
            BedrockModel model = processor.converter().apply(pojo);
            if (model != null) {
                modelCache.put(location, model);
            }
        });
        // 通知所有监听重载的 listener
        Map<Identifier, BedrockModel> modelMap = getAllModels();
        for (Consumer<Map<Identifier, BedrockModel>> listener : listeners) {
            listener.accept(modelMap);
        }
    }

    public BedrockModel getModel(Identifier location) {
        return modelCache.get(location);
    }

    @UnmodifiableView
    public Map<Identifier, BedrockModel> getAllModels() {
        return Collections.unmodifiableMap(modelCache);
    }

    @Override
    public Identifier getFabricId() {
        return ID;
    }
}
