package com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh;

import cn.sh1rocu.simplebedrockmodel.api.event.RegisterClientReloadListenersEvent;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.cache.StaticMeshCache;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.world.WorldMeshRenderer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.jetbrains.annotations.ApiStatus;

/**
 * 资源重载的 apply 阶段运行于客户端主线程；世界卸载监听由渲染器的注解订阅器处理。
 */
@ApiStatus.Internal
@Environment(EnvType.CLIENT)
public class MeshLifecycle {
    public MeshLifecycle() {
    }

    public static void registerReloadListener(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> {
            WorldMeshRenderer.clearCaches();
            StaticMeshCache.clear();
        });
    }
}
