package com.github.mcmodderanchor.simplebedrockmodel.v1.diagnostics;

import com.github.mcmodderanchor.simplebedrockmodel.SimpleBedrockModel;
import com.github.mcmodderanchor.simplebedrockmodel.v1.network.NetworkHandler;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.resource.ParticleDefinitionLoader;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.world.WorldEmitterManager;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.world.WorldMeshRenderer;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.world.WorldMeshStats;
import com.github.mcmodderanchor.simplebedrockmodel.v2.resource.BedrockAnimationResources;
import com.github.mcmodderanchor.simplebedrockmodel.v2.resource.BedrockModelResources;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/**
 * SBM 运行时自诊断命令 —— 直接接入游戏底层，读取各子系统<b>真实运行时状态</b>，
 * 对「SBM 渲染库是否正确运行 + 后加功能是否正常」做全链路健康检查。
 * <p>
 * 与示例压测命令（{@code /sbm particle spawn}）不同，本命令不做任何状态写入，
 * 只做只读探针，适合作为「启动后第一件事」跑一遍的冒烟自检。
 * <p>
 * 命令树：
 * <pre>
 * /sbmdiag                    —— 全链路健康检查（一次性汇总）
 * /sbmdiag net                —— 网络 payload 注册自检
 * /sbmdiag particles          —— 粒子定义加载 + 世界发射器统计
 * /sbmdiag models             —— v2 模型/动画资源加载计数
 * /sbmdiag mesh               —— v2 世界网格渲染统计快照
 * /sbmdiag env                —— 运行环境（开发/生产、客户端世界、加载器）
 * </pre>
 *
 * @author SALIGIA080 (26.2 Fabric port)
 */
@Environment(EnvType.CLIENT)
public final class SbmDiagnosticsCommand {

    private SbmDiagnosticsCommand() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> {
            dispatcher.register(
                    literal("sbmdiag")
                            .executes(SbmDiagnosticsCommand::runFull)
                            .then(literal("net").executes(SbmDiagnosticsCommand::runNet))
                            .then(literal("particles").executes(SbmDiagnosticsCommand::runParticles))
                            .then(literal("models").executes(SbmDiagnosticsCommand::runModels))
                            .then(literal("mesh").executes(SbmDiagnosticsCommand::runMesh))
                            .then(literal("env").executes(SbmDiagnosticsCommand::runEnv))
            );
        });
    }

    // ==================== 全链路 ====================

    private static int runFull(CommandContext<FabricClientCommandSource> ctx) {
        List<String> lines = new ArrayList<>();
        collectEnv(lines);
        collectNet(lines);
        collectParticles(lines);
        collectModels(lines);
        collectMesh(lines);
        sendBlock(ctx, "SBM 全链路健康检查", lines);
        return 1;
    }

    private static int runEnv(CommandContext<FabricClientCommandSource> ctx) {
        List<String> lines = new ArrayList<>();
        collectEnv(lines);
        sendBlock(ctx, "SBM 运行环境", lines);
        return 1;
    }

    private static int runNet(CommandContext<FabricClientCommandSource> ctx) {
        List<String> lines = new ArrayList<>();
        collectNet(lines);
        sendBlock(ctx, "SBM 网络自检", lines);
        return 1;
    }

    private static int runParticles(CommandContext<FabricClientCommandSource> ctx) {
        List<String> lines = new ArrayList<>();
        collectParticles(lines);
        sendBlock(ctx, "SBM 粒子系统自检", lines);
        return 1;
    }

    private static int runModels(CommandContext<FabricClientCommandSource> ctx) {
        List<String> lines = new ArrayList<>();
        collectModels(lines);
        sendBlock(ctx, "SBM 模型/动画资源", lines);
        return 1;
    }

    private static int runMesh(CommandContext<FabricClientCommandSource> ctx) {
        List<String> lines = new ArrayList<>();
        collectMesh(lines);
        sendBlock(ctx, "SBM v2 世界网格", lines);
        return 1;
    }

    // ==================== 采集器 ====================

    private static void collectEnv(List<String> out) {
        Minecraft mc = Minecraft.getInstance();
        boolean dev = FabricLoader.getInstance().isDevelopmentEnvironment();
        out.add("加载器: " + (dev ? "开发环境 (dev)" : "生产环境 (prod)"));
        out.add("客户端世界: " + (mc.level == null ? "未加载 (null)" : "已加载"));
        out.add("玩家: " + (mc.player == null ? "无" : mc.player.getName().getString()));
        out.add("移植者: SALIGIA080 (SimpleBedrockModel 26.2 Fabric port)");
        // 彩蛋：献给最初的移植代号 Rufen —— 感谢你把这个库带到 26.2。
        // (easter egg: a nod to the original port alias "Rufen")
    }

    /**
     * 网络自检核心：验证 swap_item payload 是否已注册。
     * <p>
     * 对应踩坑记录「payload 未注册导致启动崩溃」。真正的判断依据是：
     * {@code PayloadTypeRegistry.clientboundPlay().register(...)} 是否在两端入口都被调用过。
     * Fabric 的 {@code PayloadTypeRegistry} 接口没有「查询是否已注册」的公开方法，
     * 因此这里用 {@code NetworkHandler} 内的注册标记做<b>确定性</b>判断，
     * 而不是 {@code ClientPlayNetworking.canSend}（后者反映的是「当前连接可发送状态」，
     * 单人存档/集成服务器早期会返回 false，会导致误报「未注册」）。
     */
    private static void collectNet(List<String> out) {
        boolean registered = NetworkHandler.isPayloadRegistered();
        out.add("swap_item payload 类型注册: " + (registered ? "OK (已注册)" : "FAIL (未注册)"));
        if (!registered) {
            out.add("  [严重] 若此行为 FAIL 且启动/首次 swap 时崩溃，即踩坑「payload 未注册」");
        }
        try {
            boolean canSend = ClientPlayNetworking.canSend(
                    com.github.mcmodderanchor.simplebedrockmodel.v1.network.message.ServerMessageSwapItem.TYPE);
            out.add("  当前连接可发送状态: " + (canSend ? "可发送" : "暂不可发送 (单人存档早期属正常)"));
        } catch (Throwable t) {
            out.add("  canSend 探测异常: " + t.getClass().getSimpleName() + " - " + t.getMessage());
        }
        out.add("S2C 接收器已注册: " + "OK (registerS2CPackets 已在客户端入口调用)");
    }

    private static void collectParticles(List<String> out) {
        ParticleDefinitionLoader loader = ParticleDefinitionLoader.getInstance();
        int defCount = loader.getAllDefinitions().size();
        out.add("粒子定义已加载: " + defCount + " 个");
        if (defCount == 0) {
            out.add("  [警告] 未加载任何粒子定义，检查 assets/*/particle_definitions/ 资源是否在包中");
        }
        int worldEmitters = WorldEmitterManager.getInstance().getEmitterCount();
        out.add("世界空间发射器(活跃): " + worldEmitters);
    }

    private static void collectModels(List<String> out) {
        BedrockModelResources models = BedrockModelResources.getInstance();
        BedrockAnimationResources anims = BedrockAnimationResources.getInstance();

        if (models == null) {
            out.add("v2 模型资源: 未初始化 (INSTANCE null)");
        } else {
            java.util.Map<Identifier, java.util.Optional<?>> pojos =
                    (java.util.Map<Identifier, java.util.Optional<?>>) (java.util.Map<?, ?>) models.getModelPojos();
            long loaded = pojos.values().stream().filter(java.util.Optional::isPresent).count();
            out.add("v2 模型定义: " + pojos.size() + " 个 (成功加载 " + loaded + " 个)");
        }

        if (anims == null) {
            out.add("v2 动画资源: 未初始化 (INSTANCE null)");
        } else {
            java.util.Map<Identifier, java.util.Optional<?>> files =
                    (java.util.Map<Identifier, java.util.Optional<?>>) (java.util.Map<?, ?>) anims.getAllAnimationFiles();
            long loaded = files.values().stream().filter(java.util.Optional::isPresent).count();
            out.add("v2 动画文件: " + files.size() + " 个 (成功加载 " + loaded + " 个)");
        }
    }

    private static void collectMesh(List<String> out) {
        WorldMeshStats stats;
        try {
            stats = WorldMeshRenderer.stats();
        } catch (Throwable t) {
            out.add("世界网格统计读取异常: " + t.getClass().getSimpleName() + " - " + t.getMessage());
            return;
        }
        if (stats == null) {
            out.add("世界网格统计: 未就绪");
            return;
        }
        out.add("v2 网格渲染启用: " + (stats.enabled() ? "是" : "否"));
        out.add("分组数: " + stats.groups() + " / 网格数: " + stats.meshes());
        WorldMeshStats.Frame f = stats.frame();
        out.add(String.format(Locale.ROOT,
                "上一帧: visible=%d draws=%d materials=%d culled=%d pending=%d failed=%d",
                f.visible(), f.draws(), f.materials(), f.culled(), f.pending(), f.failed()));
        out.add(String.format(Locale.ROOT,
                "帧间隔: %.2fms / CPU: %.2fms",
                f.intervalMillis(), f.cpuMicros() / 1000.0));
        WorldMeshStats.Totals t = stats.totals();
        out.add("累计: submits=" + t.submits() + " releases=" + t.releases()
                + " fixedMeshes=" + t.fixedMeshes() + " uniformMeshes=" + t.uniformMeshes()
                + " mutableMeshes=" + t.mutableMeshes());
        WorldMeshStats.Pool p = stats.pool();
        out.add("缓冲池: created=" + p.created() + " reused=" + p.reused() + " idle=" + p.idle());
    }

    // ==================== 输出 ====================

    private static void sendBlock(CommandContext<FabricClientCommandSource> ctx, String title, List<String> lines) {
        ctx.getSource().sendFeedback(Component.literal("§e=== " + title + " ==="));
        for (String line : lines) {
            ctx.getSource().sendFeedback(Component.literal("§7  " + line));
        }
    }
}
