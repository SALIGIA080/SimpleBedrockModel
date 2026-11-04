package com.github.mcmodderanchor.simplebedrockmodel.v1.resource;

import com.github.mcmodderanchor.simplebedrockmodel.v1.common.animation.BedrockAnimation;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.model.BedrockModel;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo.BedrockAnimationFile;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.function.BiFunction;

public record BedrockAnimationResourceProcessor(RawResourceLoader rawLoader,
                                                Identifier modelKey,
                                                BiFunction<BedrockAnimationFile, BedrockModel, List<BedrockAnimation>> converter) {
}
