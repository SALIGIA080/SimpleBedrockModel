package com.github.mcmodderanchor.simplebedrockmodel.v1.client.renderer;

import com.github.mcmodderanchor.simplebedrockmodel.v1.common.model.BedrockBone;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.model.BedrockCube;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.model.BedrockMesh;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * 26.2 渲染快照：把可变 Bone 树的几何在「提取时刻」冻结成不可变的绘制命令列表，
 * 供 {@code SubmitNodeCollector.submitCustomGeometry} 的延迟回调安全消费。
 *
 * <p>旧渲染器直接往共享的 {@link PoseStack} / 静态 NORMALS 数组写数据并立即上传。
 * 26.2 里几何在提取之后才被消费，若保留对可变 Bone 的引用，后续的
 * {@code cleanAnimationTransform()} 或另一实体的提交会覆盖本帧的姿态。</p>
 */
public final class BedrockRenderSnapshot {
    /** 由 pose.normal() 矩阵派生的 6 个法向量（QUADS 每面一个）。 */
    private static final Vector3f[] NORMALS = new Vector3f[6];

    static {
        for (int i = 0; i < NORMALS.length; i++) {
            NORMALS[i] = new Vector3f();
        }
    }

    private final List<DrawCommand> drawCommands;

    private BedrockRenderSnapshot(List<DrawCommand> drawCommands) {
        this.drawCommands = List.copyOf(drawCommands);
    }

    /**
     * 在「提取时刻」遍历整个 bone 树，冻结所有可见几何的最终矩阵与参数。
     */
    public static BedrockRenderSnapshot capture(BedrockBone root,
                                                PoseStack rootPose,
                                                int light,
                                                int overlay,
                                                float red,
                                                float green,
                                                float blue,
                                                float alpha,
                                                boolean meshesPass) {
        List<DrawCommand> commands = new ArrayList<>();
        PoseStack working = copyOf(rootPose);
        capturePart(root, working, light, overlay, red, green, blue, alpha, meshesPass, commands);
        return new BedrockRenderSnapshot(commands);
    }

    private static void capturePart(BedrockBone bone,
                                    PoseStack poseStack,
                                    int inheritedLight,
                                    int overlay,
                                    float red,
                                    float green,
                                    float blue,
                                    float alpha,
                                    boolean meshesPass,
                                    List<DrawCommand> out) {
        if (!hasGeometry(bone, meshesPass)) {
            return;
        }
        if (!bone.visible || isScaleTooSmallToRender(bone)) {
            return;
        }

        int packedLight = bone.illuminated ? 15728880 : inheritedLight;
        poseStack.pushPose();
        bone.translateAndRotateAndScale(poseStack);
        PoseStack.Pose pose = poseStack.last();

        if (meshesPass) {
            if (!bone.meshes.isEmpty()) {
                out.add(new DrawCommand(pose, null, bone.meshes, packedLight, overlay, red, green, blue, alpha));
            }
        } else if (!bone.cubes.isEmpty()) {
            out.add(new DrawCommand(pose, bone.cubes, null, packedLight, overlay, red, green, blue, alpha));
        }

        for (BedrockBone child : bone.getChildren()) {
            if (hasGeometry(child, meshesPass)) {
                capturePart(child, poseStack, packedLight, overlay, red, green, blue, alpha, meshesPass, out);
            }
        }

        poseStack.popPose();
    }

    private static boolean hasGeometry(BedrockBone bone, boolean meshesPass) {
        return meshesPass ? bone.hasMeshesInTree() : bone.hasCubesInTree();
    }

    private static boolean isScaleTooSmallToRender(BedrockBone bone) {
        float xs = bone.xScale;
        float ys = bone.yScale;
        float zs = bone.zScale;
        boolean xNearZero = -1E-5F < xs && xs < 1E-5F;
        boolean yNearZero = -1E-5F < ys && ys < 1E-5F;
        boolean zNearZero = -1E-5F < zs && zs < 1E-5F;
        return (xNearZero && yNearZero) || (xNearZero && zNearZero) || (yNearZero && zNearZero);
    }

    public boolean isEmpty() {
        return drawCommands.isEmpty();
    }

    /** 延迟回调入口：把冻结的绘制命令按顺序写入 consumer。 */
    public void write(VertexConsumer consumer) {
        for (DrawCommand command : drawCommands) {
            PoseStack.Pose pose = command.pose;
            if (command.cubes != null) {
                // 由 normal 矩阵重新派生 NORMALS（避免共享静态数组被并发覆盖）
                fillNormals(pose.normal());
                for (BedrockCube cube : command.cubes) {
                    cube.compile(pose, NORMALS, consumer, command.light, command.overlay,
                            command.red, command.green, command.blue, command.alpha);
                }
            } else if (command.meshes != null) {
                for (BedrockMesh mesh : command.meshes) {
                    mesh.compileTriangles(pose, consumer, command.light, command.overlay,
                            command.red, command.green, command.blue, command.alpha);
                }
            }
        }
    }

    private static void fillNormals(Matrix3f normal) {
        NORMALS[0].set(-normal.m10, -normal.m11, -normal.m12);
        NORMALS[1].set(normal.m10, normal.m11, normal.m12);
        NORMALS[2].set(-normal.m20, -normal.m21, -normal.m22);
        NORMALS[3].set(normal.m20, normal.m21, normal.m22);
        NORMALS[4].set(-normal.m00, -normal.m01, -normal.m02);
        NORMALS[5].set(normal.m00, normal.m01, normal.m02);
    }

    private static PoseStack copyOf(PoseStack source) {
        PoseStack copy = new PoseStack();
        copy.last().pose().set(source.last().pose());
        copy.last().normal().set(source.last().normal());
        return copy;
    }

    /** 一条冻结的绘制命令：一个 bone 的一份几何（cube 或 mesh 二选一）+ 完整姿态。 */
    private record DrawCommand(PoseStack.Pose pose,
                               List<BedrockCube> cubes,
                               List<BedrockMesh> meshes,
                               int light,
                               int overlay,
                               float red,
                               float green,
                               float blue,
                               float alpha) {
        private DrawCommand {
            // 深拷贝 pose，避免源 PoseStack 后续被 pop 复用
            pose = pose.copy();
            cubes = cubes != null ? List.copyOf(cubes) : null;
            meshes = meshes != null ? List.copyOf(meshes) : null;
        }
    }
}
