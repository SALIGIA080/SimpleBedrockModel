package com.github.mcmodderanchor.simplebedrockmodel.v2.resource;

import com.github.mcmodderanchor.simplebedrockmodel.v1.resource.RawResourceLoader;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

public record BedrockAnimationEntry(
        RawResourceLoader rawLoader,
        @Nullable Identifier modelId,
        @Nullable BedrockAnimationFactory factory,
        boolean lazy,
        boolean createRuntimeAnimations
) {
}
