package com.github.mcmodderanchor.simplebedrockmodel.v1.resource;

import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.GsonUtil;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class RawResourceLoaders {
    public static final RawResourceLoader CLIENT_ONLY_LOADER = new RawResourceLoader() {
        @Override
        public <T> T load(InputStream inputStream, Class<T> clazz) {
            if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
                try (InputStreamReader reader = new InputStreamReader(inputStream)) {
                    return GsonUtil.CLIENT_GSON.fromJson(reader, clazz);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else {
                return null;
            }
        }
    };

    public static final RawResourceLoader COMMON_LOADER = new RawResourceLoader() {
        @Override
        public <T> T load(InputStream inputStream, Class<T> clazz) {
            try (InputStreamReader reader = new InputStreamReader(inputStream)) {
                if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
                    return GsonUtil.CLIENT_GSON.fromJson(reader, clazz);
                } else {
                    return GsonUtil.SERVER_NORMAL_GSON.fromJson(reader, clazz);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    };

    public static final RawResourceLoader FULL_LOADER = new RawResourceLoader() {
        @Override
        public <T> T load(InputStream inputStream, Class<T> clazz) {
            try (InputStreamReader reader = new InputStreamReader(inputStream)) {
                return GsonUtil.CLIENT_GSON.fromJson(reader, clazz);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    };

    public static final RawResourceLoader ROOT_MOTION_READY_LOADER = new RawResourceLoader() {
        @Override
        public <T> T load(InputStream inputStream, Class<T> clazz) {
            try (InputStreamReader reader = new InputStreamReader(inputStream)) {
                if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
                    return GsonUtil.CLIENT_GSON.fromJson(reader, clazz);
                } else {
                    return GsonUtil.SERVER_GSON_FOR_ROOT_MOTION.fromJson(reader, clazz);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    };
}
