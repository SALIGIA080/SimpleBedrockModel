package com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.serialize;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import net.minecraft.resources.Identifier;

import java.lang.reflect.Type;

/**
 * 26.2 迁移：原 1.20.1 的 {@code net.minecraft.resources.ResourceLocation.Serializer}
 * 在 26.2 中已随 {@code ResourceLocation -> Identifier} 改名而消失（26.2 的
 * {@link Identifier} 不再内置 {@code Serializer} 内部类）。
 * <p>
 * 因此这里提供一个等价的 {@code Identifier} Gson 序列化器，替代原
 * {@code new ResourceLocation.Serializer()} 的用法。
 */
@SuppressWarnings("ALL")
public class IdentifierSerializer implements JsonSerializer<Identifier>, JsonDeserializer<Identifier> {
    @Override
    public JsonElement serialize(Identifier src, Type typeOfSrc, JsonSerializationContext context) {
        return new JsonPrimitive(src.toString());
    }

    @Override
    public Identifier deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        return Identifier.tryParse(json.getAsString());
    }
}
