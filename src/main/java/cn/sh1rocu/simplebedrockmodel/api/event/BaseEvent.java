package cn.sh1rocu.simplebedrockmodel.api.event;

import com.github.mcmodderanchor.simplebedrockmodel.SimpleBedrockModel;
import net.minecraft.resources.Identifier;

public class BaseEvent {
    public static final Identifier HIGHEST = SimpleBedrockModel.modLoc("highest");
    public static final Identifier HIGH = SimpleBedrockModel.modLoc("high");
    public static final Identifier LOW = SimpleBedrockModel.modLoc("low");
    public static final Identifier LOWEST = SimpleBedrockModel.modLoc("lowest");

    protected boolean isCanceled = false;
}