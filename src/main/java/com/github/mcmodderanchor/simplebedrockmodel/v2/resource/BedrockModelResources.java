package com.github.mcmodderanchor.simplebedrockmodel.v2.resource;

import com.github.mcmodderanchor.simplebedrockmodel.SimpleBedrockModel;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.BoneIndexProvider;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.animation.BedrockAnimation;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo.BedrockAnimationFile;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo.BedrockModelPOJO;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.baked.BakedBedrockModel;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.baked.BakerOptions;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.tree.TreeBedrockModel;
import com.google.common.collect.Maps;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import javax.annotation.ParametersAreNonnullByDefault;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.function.Consumer;

public class BedrockModelResources extends SimplePreparableReloadListener<Map<Identifier, Optional<BedrockModelPOJO>>>
        implements IdentifiableResourceReloadListener {
    private final Map<Identifier, BedrockModelEntry> processors;
    private final List<Consumer<Map<Identifier, BedrockModelResource>>> listeners;
    private final Map<Identifier, Optional<BedrockModelPOJO>> pojoCache;
    private final Map<Identifier, Optional<BedrockModelResource>> resourceCache;
    @Nullable
    private ResourceManager resourceManager;

    public static BedrockModelResources INSTANCE;

    public static final Identifier ID = SimpleBedrockModel.modLoc("bedrock_model_resources");

    public static BedrockModelResources getInstance() {
        return INSTANCE;
    }

    public BedrockModelResources(Map<Identifier, BedrockModelEntry> processors,
                                 List<Consumer<Map<Identifier, BedrockModelResource>>> listeners) {
        this.processors = Map.copyOf(processors);
        this.listeners = List.copyOf(listeners);
        this.pojoCache = Maps.newHashMap();
        this.resourceCache = Maps.newHashMap();
    }

    @Override
    @NotNull
    @ParametersAreNonnullByDefault
    protected Map<Identifier, Optional<BedrockModelPOJO>> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<Identifier, Optional<BedrockModelPOJO>> result = Maps.newHashMap();
        processors.forEach((location, processor) -> {
            if (processor.lazy()) {
                return;
            }
            result.put(location, loadModelPojo(resourceManager, processor));
        });
        return result;
    }

    @Override
    @ParametersAreNonnullByDefault
    protected void apply(Map<Identifier, Optional<BedrockModelPOJO>> prepared, ResourceManager resourceManager, ProfilerFiller profiler) {
        this.resourceManager = resourceManager;
        pojoCache.clear();
        resourceCache.clear();
        pojoCache.putAll(prepared);
        processors.forEach((location, processor) -> {
            if (processor.lazy()) {
                return;
            }
            Optional<BedrockModelPOJO> pojo = pojoCache.get(location);
            if (pojo == null || pojo.isEmpty()) {
                return;
            }
            resourceCache.put(location, createModelResource(location, processor, pojo.get()));
        });
        Map<Identifier, BedrockModelResource> resources = getAllResources();
        for (Consumer<Map<Identifier, BedrockModelResource>> listener : listeners) {
            listener.accept(resources);
        }
    }

    @Nullable
    public synchronized BedrockModelResource getResource(Identifier location) {
        Optional<BedrockModelResource> cached = resourceCache.get(location);
        if (cached != null) {
            return cached.orElse(null);
        }
        BedrockModelEntry processor = processors.get(location);
        if (processor == null) {
            SimpleBedrockModel.LOGGER.error("Not registered v2 model: {}", location);
            return null;
        }
        BedrockModelPOJO pojo = getModelPojo(location);
        if (pojo == null) {
            resourceCache.put(location, Optional.empty());
            return null;
        }
        Optional<BedrockModelResource> resource = createModelResource(location, processor, pojo);
        resourceCache.put(location, resource);
        return resource.orElse(null);
    }


    @Nullable
    public synchronized BakedBedrockModel getBakedModel(Identifier location) {
        BedrockModelResource resource = getResource(location);
        if (resource == null || resource.kind() != ModelType.BAKED) {
            return null;
        }
        return (BakedBedrockModel) resource.model();
    }

    @Nullable
    public synchronized TreeBedrockModel getTreeModel(Identifier location) {
        BedrockModelResource resource = getResource(location);
        if (resource == null || resource.kind() != ModelType.TREE) {
            return null;
        }
        return (TreeBedrockModel) resource.model();
    }

    @Nullable
    public synchronized List<BedrockAnimation> getAnimations(Identifier modelId, Identifier animationId) {
        BedrockModelResource resource = getResource(modelId);
        return resource == null ? null : resource.getAnimations(animationId);
    }

    @Nullable
    public synchronized BedrockModelPOJO getModelPojo(Identifier location) {
        Optional<BedrockModelPOJO> cached = pojoCache.get(location);
        if (cached != null) {
            return cached.orElse(null);
        }
        BedrockModelEntry processor = processors.get(location);
        if (processor == null) {
            SimpleBedrockModel.LOGGER.error("Not registered v2 model: {}", location);
            return null;
        }
        if (resourceManager == null) {
            SimpleBedrockModel.LOGGER.error("Cannot lazy load v2 model before resource reload is applied: {}", location);
            return null;
        }
        Optional<BedrockModelPOJO> pojo = loadModelPojo(resourceManager, processor);
        pojoCache.put(location, pojo);
        return pojo.orElse(null);
    }

    public synchronized void clearLoaded(Identifier location) {
        pojoCache.remove(location);
        resourceCache.remove(location);
    }

    public synchronized void clearLoaded() {
        pojoCache.clear();
        resourceCache.clear();
    }

    @UnmodifiableView
    public Map<Identifier, BedrockModelResource> getAllResources() {
        Map<Identifier, BedrockModelResource> result = new LinkedHashMap<>();
        for (Map.Entry<Identifier, Optional<BedrockModelResource>> entry : resourceCache.entrySet()) {
            entry.getValue().ifPresent(resource -> result.put(entry.getKey(), resource));
        }
        return Collections.unmodifiableMap(result);
    }

    @UnmodifiableView
    public Map<Identifier, BakedBedrockModel> getAllBakedModels() {
        Map<Identifier, BakedBedrockModel> result = new LinkedHashMap<>();
        for (Map.Entry<Identifier, BedrockModelResource> entry : getAllResources().entrySet()) {
            if (entry.getValue().kind() == ModelType.BAKED) {
                result.put(entry.getKey(), (BakedBedrockModel) entry.getValue().model());
            }
        }
        return Collections.unmodifiableMap(result);
    }

    @UnmodifiableView
    public Map<Identifier, TreeBedrockModel> getAllTreeModels() {
        Map<Identifier, TreeBedrockModel> result = new LinkedHashMap<>();
        for (Map.Entry<Identifier, BedrockModelResource> entry : getAllResources().entrySet()) {
            if (entry.getValue().kind() == ModelType.TREE) {
                result.put(entry.getKey(), (TreeBedrockModel) entry.getValue().model());
            }
        }
        return Collections.unmodifiableMap(result);
    }

    @UnmodifiableView
    public Map<Identifier, Optional<BedrockModelPOJO>> getModelPojos() {
        return Collections.unmodifiableMap(pojoCache);
    }

    private Optional<BedrockModelResource> createModelResource(Identifier location, BedrockModelEntry processor, BedrockModelPOJO pojo) {
        try {
            BoneIndexProvider model = switch (processor.kind()) {
                case BAKED -> createBakedModel(location, processor, pojo);
                case TREE -> TreeBedrockModel.bake(pojo);
            };
            Map<Identifier, List<BedrockAnimation>> animations = createAnimations(processor, model);
            return Optional.of(new BedrockModelResource(model, processor.kind(), animations));
        } catch (RuntimeException e) {
            SimpleBedrockModel.LOGGER.error("Failed to create v2 model resource: {}", location, e);
            return Optional.empty();
        }
    }

    private BakedBedrockModel createBakedModel(Identifier location, BedrockModelEntry processor, BedrockModelPOJO pojo) {
        List<BedrockAnimationFile> animationFiles = collectAnimationFiles(location, processor);
        BedrockModelBakeContext context = new BedrockModelBakeContext(location, pojo, animationFiles);
        BakerOptions options = processor.optionsFactory().apply(context);
        if (options == null) {
            options = BakerOptions.defaults();
        }
        return BakedBedrockModel.bake(pojo, options);
    }

    private List<BedrockAnimationFile> collectAnimationFiles(Identifier modelId, BedrockModelEntry processor) {
        if (processor.animationSourceIds().isEmpty()) {
            return List.of();
        }
        BedrockAnimationResources animationResourceSet = BedrockAnimationResources.getInstance();
        if (animationResourceSet == null) {
            SimpleBedrockModel.LOGGER.error("Cannot collect animation sources for v2 model {} before v2 animation resource set is initialized", modelId);
            return List.of();
        }
        ArrayList<BedrockAnimationFile> files = new ArrayList<>();
        for (Identifier animationId : processor.animationSourceIds()) {
            BedrockAnimationFile file = animationResourceSet.getAnimationFile(animationId);
            if (file != null) {
                files.add(file);
            }
        }
        return files;
    }

    private Map<Identifier, List<BedrockAnimation>> createAnimations(BedrockModelEntry modelProcessor, BoneIndexProvider model) {
        if (modelProcessor.animationSourceIds().isEmpty()) {
            return Map.of();
        }
        BedrockAnimationResources animationResourceSet = BedrockAnimationResources.getInstance();
        if (animationResourceSet == null) {
            return Map.of();
        }
        Map<Identifier, List<BedrockAnimation>> animations = new LinkedHashMap<>();
        for (Identifier animationId : modelProcessor.animationSourceIds()) {
            BedrockAnimationEntry animationProcessor = animationResourceSet.getProcessor(animationId);
            if (animationProcessor == null || !animationProcessor.createRuntimeAnimations() || animationProcessor.factory() == null) {
                continue;
            }
            BedrockAnimationFile file = animationResourceSet.getAnimationFile(animationId);
            if (file == null) {
                continue;
            }
            List<BedrockAnimation> created = animationProcessor.factory().create(file, model);
            if (created != null) {
                animations.put(animationId, created);
            }
        }
        return animations;
    }

    private static Optional<BedrockModelPOJO> loadModelPojo(ResourceManager resourceManager, BedrockModelEntry processor) {
        Identifier path = modelPath(processor.sourceId());
        return resourceManager.getResource(path).map(resource -> {
            try (InputStream stream = resource.open()) {
                return Optional.ofNullable(processor.rawLoader().load(stream, BedrockModelPOJO.class));
            } catch (IOException | RuntimeException e) {
                SimpleBedrockModel.LOGGER.error("Failed to load v2 model file: {}", path, e);
                return Optional.<BedrockModelPOJO>empty();
            }
        }).orElseGet(() -> {
            SimpleBedrockModel.LOGGER.error("Not found v2 model file: {}", path);
            return Optional.empty();
        });
    }

    private static Identifier modelPath(Identifier location) {
        return Identifier.fromNamespaceAndPath(location.getNamespace(), "models/bedrock/" + location.getPath() + ".json");
    }

    @Override
    public Identifier getFabricId() {
        return ID;
    }
}
