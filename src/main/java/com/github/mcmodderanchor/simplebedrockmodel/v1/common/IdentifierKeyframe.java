package com.github.mcmodderanchor.simplebedrockmodel.v1.common;

import com.maydaymemory.mae.basic.BaseKeyframe;
import net.minecraft.resources.Identifier;

public class IdentifierKeyframe extends BaseKeyframe<Identifier> {
    private final Identifier identifier;

    public IdentifierKeyframe(float timeS, Identifier identifier) {
        super(timeS);
        this.identifier = identifier;
    }

    @Override
    public Identifier getValue() {
        return identifier;
    }
}
