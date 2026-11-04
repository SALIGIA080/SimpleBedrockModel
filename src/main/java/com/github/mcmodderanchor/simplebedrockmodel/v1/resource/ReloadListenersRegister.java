package com.github.mcmodderanchor.simplebedrockmodel.v1.resource;

import com.github.mcmodderanchor.simplebedrockmodel.v1.event.RegisterBedrockAnimationEvent;
import com.github.mcmodderanchor.simplebedrockmodel.v1.event.RegisterBedrockAnimationReloadListenerEvent;
import com.github.mcmodderanchor.simplebedrockmodel.v1.event.RegisterBedrockModelEvent;
import com.github.mcmodderanchor.simplebedrockmodel.v1.event.RegisterBedrockModelReloadListenerEvent;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.resource.ParticleDefinitionLoader;
import com.github.mcmodderanchor.simplebedrockmodel.v2.event.RegisterV2BedrockResourcesEvent;
import com.github.mcmodderanchor.simplebedrockmodel.v2.resource.BedrockAnimationResources;
import com.github.mcmodderanchor.simplebedrockmodel.v2.resource.BedrockModelResources;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.server.packs.PackType;

public class ReloadListenersRegister {
    @Environment(EnvType.CLIENT)
    public static class BedrockModelClientRegister {
        public static void onRegisterReloadListener() {
            var event1 = new RegisterBedrockModelEvent(EnvType.CLIENT);
            RegisterBedrockModelEvent.EVENT.invoker().post(event1);
            var event2 = new RegisterBedrockModelReloadListenerEvent();
            RegisterBedrockModelReloadListenerEvent.EVENT.invoker().post(event2);
            BedrockModelResourceSet.INSTANCE = new BedrockModelResourceSet(event1.getModelRegistry(), event2.getListeners());

            var event3 = new RegisterBedrockAnimationEvent(EnvType.CLIENT);
            RegisterBedrockAnimationEvent.EVENT.invoker().post(event3);
            var event4 = new RegisterBedrockAnimationReloadListenerEvent();
            RegisterBedrockAnimationReloadListenerEvent.EVENT.invoker().post(event4);
            BedrockAnimationResourceSet.INSTANCE = new BedrockAnimationResourceSet(event3.getAnimationRegistry(), event4.getListeners());


            var event5 = new RegisterV2BedrockResourcesEvent(EnvType.CLIENT);
            RegisterV2BedrockResourcesEvent.EVENT.invoker().post(event5);
            BedrockAnimationResources.INSTANCE = new BedrockAnimationResources(event5.getAnimationRegistry());
            BedrockModelResources.INSTANCE = new BedrockModelResources(event5.getModelRegistry(), event5.getReloadListeners());


            var registry = ResourceManagerHelper.get(PackType.CLIENT_RESOURCES);
            registry.registerReloadListener(BedrockModelResourceSet.INSTANCE);
            registry.registerReloadListener(BedrockAnimationResourceSet.INSTANCE);
            registry.registerReloadListener(BedrockAnimationResources.INSTANCE);
            registry.registerReloadListener(BedrockModelResources.INSTANCE);
            registry.registerReloadListener(ParticleDefinitionLoader.getInstance());
        }
    }

    @Environment(EnvType.SERVER)
    public static class BedrockModelServerRegister {
        public static void onRegisterReloadListener() {
            var event1 = new RegisterBedrockModelEvent(EnvType.SERVER);
            RegisterBedrockModelEvent.EVENT.invoker().post(event1);
            var event2 = new RegisterBedrockModelReloadListenerEvent();
            RegisterBedrockModelReloadListenerEvent.EVENT.invoker().post(event2);
            BedrockModelResourceSet.INSTANCE = new BedrockModelResourceSet(event1.getModelRegistry(), event2.getListeners());

            var event3 = new RegisterBedrockAnimationEvent(EnvType.SERVER);
            RegisterBedrockAnimationEvent.EVENT.invoker().post(event3);
            var event4 = new RegisterBedrockAnimationReloadListenerEvent();
            RegisterBedrockAnimationReloadListenerEvent.EVENT.invoker().post(event4);
            BedrockAnimationResourceSet.INSTANCE = new BedrockAnimationResourceSet(event3.getAnimationRegistry(), event4.getListeners());


            var event5 = new RegisterV2BedrockResourcesEvent(EnvType.SERVER);
            RegisterV2BedrockResourcesEvent.EVENT.invoker().post(event5);
            BedrockAnimationResources.INSTANCE = new BedrockAnimationResources(event5.getAnimationRegistry());
            BedrockModelResources.INSTANCE = new BedrockModelResources(event5.getModelRegistry(), event5.getReloadListeners());

            var registry = ResourceManagerHelper.get(PackType.SERVER_DATA);
            registry.registerReloadListener(BedrockModelResourceSet.INSTANCE);
            registry.registerReloadListener(BedrockAnimationResourceSet.INSTANCE);
            registry.registerReloadListener(BedrockAnimationResources.INSTANCE);
            registry.registerReloadListener(BedrockModelResources.INSTANCE);
        }
    }
}
