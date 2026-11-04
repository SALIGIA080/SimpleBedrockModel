package cn.sh1rocu.simplebedrockmodel.api.event;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.HumanoidArm;

public class RenderArmEvent extends BaseEvent implements ICancellableEvent {

    private final PoseStack poseStack;
    private final SubmitNodeCollector multiBufferSource;
    private final int packedLight;
    private final AbstractClientPlayer player;
    private final HumanoidArm arm;

    public static final Event<Callback> EVENT = EventFactory.createArrayBacked(Callback.class, callbacks -> event -> {
        for (final Callback callback : callbacks)
            callback.post(event);
    });

    public RenderArmEvent(PoseStack poseStack, SubmitNodeCollector multiBufferSource, int packedLight, AbstractClientPlayer player, HumanoidArm arm) {
        this.poseStack = poseStack;
        this.multiBufferSource = multiBufferSource;
        this.packedLight = packedLight;
        this.player = player;
        this.arm = arm;
    }

    public HumanoidArm getArm() {
        return arm;
    }

    public PoseStack getPoseStack() {
        return poseStack;
    }

    public SubmitNodeCollector getSubmitNodeCollector() {
        return multiBufferSource;
    }

    public int getPackedLight() {
        return packedLight;
    }

    public AbstractClientPlayer getPlayer() {
        return player;
    }

    public interface Callback {
        void post(RenderArmEvent event);
    }
}