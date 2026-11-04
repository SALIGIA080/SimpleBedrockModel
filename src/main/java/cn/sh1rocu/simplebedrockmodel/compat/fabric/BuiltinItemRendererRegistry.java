package cn.sh1rocu.simplebedrockmodel.compat.fabric;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Fabric API 的 BuiltinItemRendererRegistry 在 26.2 中被移除后的本地替代。
 * 提供一个简单的 Item → DynamicItemRenderer 注册表。
 */
public class BuiltinItemRendererRegistry {
    public static final BuiltinItemRendererRegistry INSTANCE = new BuiltinItemRendererRegistry();

    private final Map<Item, DynamicItemRenderer> renderers = new IdentityHashMap<>();

    private BuiltinItemRendererRegistry() {
    }

    public void register(Item item, DynamicItemRenderer renderer) {
        renderers.put(item, renderer);
    }

    public DynamicItemRenderer get(Item item) {
        return renderers.get(item);
    }

    /**
     * 26.2 中自定义物品渲染器的接口。
     * 替代旧的 BlockEntityWithoutLevelRenderer + DynamicItemRenderer 模式。
     */
    public interface DynamicItemRenderer {
        void render(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay);
    }
}
