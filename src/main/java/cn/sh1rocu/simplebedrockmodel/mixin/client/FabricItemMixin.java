package cn.sh1rocu.simplebedrockmodel.mixin.client;

import com.github.mcmodderanchor.simplebedrockmodel.v1.client.handler.FirstPersonRenderHandler;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.fabricmc.fabric.api.item.v1.FabricItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FabricItem.class)
public interface FabricItemMixin {
    @ModifyReturnValue(
            remap = false,
            method = "allowComponentsUpdateAnimation",
            at = @At("RETURN")
    )
    private boolean sbm$shouldReequip(boolean original, Player player, InteractionHand hand, ItemStack cached, ItemStack current) {
        if (FirstPersonRenderHandler.hasCustomRenderer(cached) || FirstPersonRenderHandler.hasCustomRenderer(current)) {
            return !FirstPersonRenderHandler.isSameHeldItem(cached, current);
        }
        return original;
    }
}
