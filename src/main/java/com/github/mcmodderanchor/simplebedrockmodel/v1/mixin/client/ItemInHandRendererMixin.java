package com.github.mcmodderanchor.simplebedrockmodel.v1.mixin.client;

import cn.sh1rocu.simplebedrockmodel.mixin.client.FabricItemMixin;
import com.github.mcmodderanchor.simplebedrockmodel.v1.client.handler.FirstPersonRenderHandler;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ItemInHandRenderer.class, priority = 2000)
public class ItemInHandRendererMixin {

    @Shadow
    private float mainHandHeight;

    @Shadow
    private float oMainHandHeight;

    @Shadow
    private float offHandHeight;

    @Shadow
    private float oOffHandHeight;

    /**
     * 26.2 里 {@code tick()} 重新装备判定改走 private 方法
     * {@code shouldInstantlyReplaceVisibleItem(ItemStack, ItemStack)}，其内部用
     * {@code ItemStack.matchesIgnoringComponents(...)} 替代了旧 {@code ItemStack.matches}
     * （NBT 精确匹配）。枪械每开一发 NBT 即变，vanilla 会因此判定为不同物品而触发
     * 重装下沉动画，导致弹药数等 NBT 变化无法即时反映到第一人称渲染。
     *
     * <p>这里直接在 {@code shouldInstantlyReplaceVisibleItem} 的 HEAD 拦截：
     * 自定义物品按 NBT 容忍语义判定为同一物品（不重装），非自定义物品回退 vanilla 判定。</p>
     */
    @Inject(method = "shouldInstantlyReplaceVisibleItem", at = @At("HEAD"), cancellable = true)
    private void sbm$shouldInstantlyReplace(ItemStack a, ItemStack b, CallbackInfoReturnable<Boolean> cir) {
        if (FirstPersonRenderHandler.hasCustomRenderer(a) || FirstPersonRenderHandler.hasCustomRenderer(b)) {
            cir.setReturnValue(!FirstPersonRenderHandler.isSameHeldItem(a, b));
        }
    }

    /**
     * 收枪过渡期间钉住 vanilla 物品升降高度，屏蔽原版升降动画，让自定义收枪动画独占表现。
     * 按手分别处理：仅钉住正在过渡的那只手，另一只手保留 vanilla {@code tick()} 的正常推进。
     * 在 TAIL 覆盖最终结果——此时 reequip 的副作用已被上面的判定修正，
     * 不会污染缓存物品，故 TAIL 钉高度是安全的。
     */
    @Inject(method = "tick", at = @At("TAIL"))
    private void sbm$pinHeightDuringSheathe(CallbackInfo ci) {
        if (FirstPersonRenderHandler.shouldLockVanilla(InteractionHand.MAIN_HAND)) {
            this.mainHandHeight = 1.0F;
            this.oMainHandHeight = 1.0F;
        }
        if (FirstPersonRenderHandler.shouldLockVanilla(InteractionHand.OFF_HAND)) {
            this.offHandHeight = 1.0F;
            this.oOffHandHeight = 1.0F;
        }
    }
}
