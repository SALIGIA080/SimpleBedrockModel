package com.github.mcmodderanchor.simplebedrockmodel.v2.resource;

import com.github.mcmodderanchor.simplebedrockmodel.SimpleBedrockModel;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo.BedrockAnimationFile;
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
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

public class BedrockAnimationResources extends SimplePreparableReloadListener<Map<Identifier, Optional<BedrockAnimationFile>>>
        implements IdentifiableResourceReloadListener {
    private final Map<Identifier, BedrockAnimationEntry> processors;
    private final Map<Identifier, Optional<BedrockAnimationFile>> fileCache;
    @Nullable
    private ResourceManager resourceManager;

    public static BedrockAnimationResources INSTANCE;

    public static final Identifier ID = SimpleBedrockModel.modLoc("bedrock_anim_resources");

    public static BedrockAnimationResources getInstance() {
        return INSTANCE;
    }

    public BedrockAnimationResources(Map<Identifier, BedrockAnimationEntry> processors) {
        this.processors = Map.copyOf(processors);
        this.fileCache = Maps.newHashMap();
    }

    @Override
    @NotNull
    @ParametersAreNonnullByDefault
    protected Map<Identifier, Optional<BedrockAnimationFile>> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<Identifier, Optional<BedrockAnimationFile>> result = Maps.newHashMap();
        processors.forEach((location, processor) -> {
            if (processor.lazy()) {
                return;
            }
            result.put(location, loadAnimationFile(resourceManager, location, processor));
        });
        return result;
    }

    @Override
    @ParametersAreNonnullByDefault
    protected void apply(Map<Identifier, Optional<BedrockAnimationFile>> prepared, ResourceManager resourceManager, ProfilerFiller profiler) {
        this.resourceManager = resourceManager;
        fileCache.clear();
        fileCache.putAll(prepared);
    }

    @Nullable
    public synchronized BedrockAnimationFile getAnimationFile(Identifier location) {
        Optional<BedrockAnimationFile> cached = fileCache.get(location);
        if (cached != null) {
            return cached.orElse(null);
        }
        BedrockAnimationEntry processor = processors.get(location);
        if (processor == null) {
            SimpleBedrockModel.LOGGER.error("Not registered v2 animation: {}", location);
            return null;
        }
        if (resourceManager == null) {
            SimpleBedrockModel.LOGGER.error("Cannot lazy load v2 animation before resource reload is applied: {}", location);
            return null;
        }
        Optional<BedrockAnimationFile> file = loadAnimationFile(resourceManager, location, processor);
        fileCache.put(location, file);
        return file.orElse(null);
    }

    @Nullable
    public BedrockAnimationEntry getProcessor(Identifier location) {
        return processors.get(location);
    }

    public synchronized void clearLoaded(Identifier location) {
        fileCache.remove(location);
    }

    public synchronized void clearLoaded() {
        fileCache.clear();
    }

    @UnmodifiableView
    public Map<Identifier, Optional<BedrockAnimationFile>> getAllAnimationFiles() {
        return Collections.unmodifiableMap(fileCache);
    }

    private static Optional<BedrockAnimationFile> loadAnimationFile(ResourceManager resourceManager, Identifier location,
                                                                    BedrockAnimationEntry processor) {
        Identifier path = animationPath(location);
        return resourceManager.getResource(path).map(resource -> {
            try (InputStream stream = resource.open()) {
                return Optional.ofNullable(processor.rawLoader().load(stream, BedrockAnimationFile.class));
            } catch (IOException | RuntimeException e) {
                SimpleBedrockModel.LOGGER.error("Failed to load v2 animation file: {}", path, e);
                return Optional.<BedrockAnimationFile>empty();
            }
        }).orElseGet(() -> {
            SimpleBedrockModel.LOGGER.error("Not found v2 animation file: {}", path);
            return Optional.empty();
        });
    }

    private static Identifier animationPath(Identifier location) {
        return Identifier.fromNamespaceAndPath(location.getNamespace(), "animations/" + location.getPath() + ".json");
    }

    @Override
    public Identifier getFabricId() {
        return ID;
    }
}
