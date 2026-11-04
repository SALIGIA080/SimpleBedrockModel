package com.github.mcmodderanchor.simplebedrockmodel.v1.network;

import com.github.mcmodderanchor.simplebedrockmodel.v1.network.message.ServerMessageSwapItem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public class NetworkHandler {

    /** 注册标记：{@code registerPayloads()} 被调用后置 true，供诊断命令做确定性判断。 */
    private static volatile boolean payloadsRegistered = false;

    /**
     * 注册所有 payload 类型到 PayloadTypeRegistry。
     * <p>
     * 必须在服务端与客户端两端公共入口都调用（服务端发送 S2C 消息、客户端接收都需要类型注册），
     * 否则 {@code registerGlobalReceiver} 会抛「no payload type has been registered」。
     * 对应 TACZ 侧 {@code NetworkHandler.registerPayloads()}（踩坑记录：payload 未注册导致启动崩溃）。
     */
    public static void registerPayloads() {
        PayloadTypeRegistry.clientboundPlay().register(ServerMessageSwapItem.TYPE, ServerMessageSwapItem.CODEC);
        payloadsRegistered = true;
    }

    /** 诊断用：payload 类型是否已注册（{@code registerPayloads()} 是否被调用过）。 */
    public static boolean isPayloadRegistered() {
        return payloadsRegistered;
    }

    @Environment(EnvType.CLIENT)
    public static void registerS2CPackets() {
        ClientPlayNetworking.registerGlobalReceiver(ServerMessageSwapItem.TYPE, ServerMessageSwapItem::handle);
    }

    public static void sendToClientPlayer(CustomPacketPayload message, ServerPlayer player) {
        ServerPlayNetworking.send(player, message);
    }

    /**
     * 发送给所有监听此实体的玩家
     */
    public static void sendToTrackingEntityAndSelf(Entity centerEntity, CustomPacketPayload message) {
        sendToTrackingEntity(message, centerEntity);

        if (centerEntity instanceof ServerPlayer serverPlayer) {
            sendToClientPlayer(message, serverPlayer);
        }
    }

    public static void sendToAllPlayers(CustomPacketPayload message, MinecraftServer server) {
        for (ServerPlayer player : PlayerLookup.all(server)) {
            sendToClientPlayer(message, player);
        }
    }

    public static void sendToTrackingEntity(CustomPacketPayload message, final Entity centerEntity) {
        for (ServerPlayer serverPlayer : PlayerLookup.tracking(centerEntity)) {
            sendToClientPlayer(message, serverPlayer);
        }
    }

    public static void sendToDimension(CustomPacketPayload message, final Entity centerEntity) {
        if (centerEntity.level() instanceof ServerLevel serverLevel) {
            for (ServerPlayer player : PlayerLookup.level(serverLevel)) {
                sendToClientPlayer(message, player);
            }
        }
    }
}
