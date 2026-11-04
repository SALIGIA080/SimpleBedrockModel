package com.github.mcmodderanchor.simplebedrockmodel.v2.client.command;

import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.world.StrategyOverride;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.world.WorldMeshGroupStats;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.world.WorldMeshRenderer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.ApiStatus;

import java.util.Locale;

/**
 * 库随包提供的客户端配置快捷入口，与 example 压测命令独立。
 */
@ApiStatus.Internal
@Environment(EnvType.CLIENT)
public final class WorldMeshCommands {
    private WorldMeshCommands() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> {
            var strategy = ClientCommands.literal("strategy");
            for (StrategyOverride value : StrategyOverride.values()) {
                strategy.then(ClientCommands.literal(value.name().toLowerCase(Locale.ROOT)).executes(context -> {
                    WorldMeshRenderer.setStrategy(value);
                    context.getSource().sendFeedback(Component.literal("Configured strategy=" + value));
                    for (WorldMeshGroupStats group : WorldMeshRenderer.groupStats()) {
                        String message = group.id() + ": " + group.effectiveStrategy() + " (" + group.policySource() + ") " + group.warnings();
                        context.getSource().sendFeedback(Component.literal(message));
                    }
                    return 1;
                }));
            }
            dispatcher.register(ClientCommands.literal("sbmrender").then(strategy)
                    .then(ClientCommands.literal("groups").executes(context -> {
                        for (WorldMeshGroupStats group : WorldMeshRenderer.groupStats()) {
                            context.getSource().sendFeedback(Component.literal(group.toString()));
                        }
                        return 1;
                    })));
        });
    }
}
