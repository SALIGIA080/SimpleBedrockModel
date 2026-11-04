package cn.sh1rocu.simplebedrockmodel.mixin.client;

import cn.sh1rocu.simplebedrockmodel.api.event.RegisterClientReloadListenersEvent;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.cache.StaticMeshCache;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.world.WorldMeshRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Shadow
    @Final
    private ReloadableResourceManager resourceManager;

    @Shadow
    @Nullable
    public ClientLevel level;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void sbm$onInit(CallbackInfo ci) {
        RegisterClientReloadListenersEvent.EVENT.invoker().post(new RegisterClientReloadListenersEvent(this.resourceManager));
    }

    /**
     * 注意：这里<b>不再</b>注入 runTick 去 post {@code RenderTickEvent}。
     *
     * <p>SimpleBedrockModel 是 TaCZ 的内嵌渲染库（jar-in-jar），不单独作为 mod 发布。
     * {@code RenderTickEvent} 的触发点由宿主 TaCZ 提供（{@code GameRendererMixin} 在
     * {@code GameRenderer#render} 的 HEAD/RETURN 各发一次），这与 Forge/NeoForge 的
     * {@code RenderTickEvent} 语义一致。</p>
     *
     * <p>若此处再注入一帧一次的 post，两处触发点会让所有订阅者（动画时钟
     * {@code ClientAnimationClockTicker}、第一人称动画、静态网格缓存、TaCZ 的分划板/
     * 改装插值等）每帧被调用两次 —— 直接表现为动画时钟走两倍速、分划板被画两遍。</p>
     *
     * <p>因此库侧必须保持"零触发"，只提供事件类型与订阅注册。</p>
     */
    @Inject(method = "setLevel", at = @At("HEAD"))
    private void sbm$setLevel(ClientLevel levelClient, CallbackInfo ci) {
        if (this.level != null) {
            WorldMeshRenderer.onLevelUnload(this.level);
            StaticMeshCache.onLevelUnload(this.level);
        }
    }

    @Inject(method = "clearClientLevel(Lnet/minecraft/client/gui/screens/Screen;)V", at = @At("HEAD"))
    private void sbm$clearLevel(Screen screen, CallbackInfo ci) {
        if (this.level != null) {
            WorldMeshRenderer.onLevelUnload(this.level);
            StaticMeshCache.onLevelUnload(this.level);
        }
    }
}
