package com.github.mcmodderanchor.simplebedrockmodel.v1.network.message;

import com.github.mcmodderanchor.simplebedrockmodel.SimpleBedrockModel;
import com.github.mcmodderanchor.simplebedrockmodel.v1.client.event.SwapItemWithOffHand;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 服务端 -> 客户端：请求客户端交换副手物品。
 * <p>
 * 26.2 迁移：旧的 {@code FabricPacket}/{@code PacketType} 已移除，改用 MC 原生
 * {@link CustomPacketPayload} + {@link StreamCodec}。本消息无负载，用 {@link StreamCodec#unit(Object)}。
 */
public record ServerMessageSwapItem() implements CustomPacketPayload {

    public static final Type<ServerMessageSwapItem> TYPE = new Type<>(SimpleBedrockModel.modLoc("swap_item"));

    public static final StreamCodec<FriendlyByteBuf, ServerMessageSwapItem> CODEC =
            StreamCodec.unit(new ServerMessageSwapItem());

    @Environment(EnvType.CLIENT)
    public static void handle(ServerMessageSwapItem packet, ClientPlayNetworking.Context context) {
        SwapItemWithOffHand.EVENT.invoker().post(new SwapItemWithOffHand());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
