package cn.sh1rocu.simplebedrockmodel;

import com.github.mcmodderanchor.simplebedrockmodel.v1.network.NetworkHandler;
import com.github.mcmodderanchor.simplebedrockmodel.v1.resource.ReloadListenersRegister;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public class SimpleBedrockModel implements ModInitializer {
    @Override
    public void onInitialize() {
        com.github.mcmodderanchor.simplebedrockmodel.SimpleBedrockModel.setUp();

        // S2C payload 类型注册必须在服务端入口也做（服务端发送 ServerMessageSwapItem 需要类型注册）
        NetworkHandler.registerPayloads();

        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER) {
            ReloadListenersRegister.BedrockModelServerRegister.onRegisterReloadListener();
        }
    }
}
