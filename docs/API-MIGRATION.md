# SimpleBedrockModel API Migration: 1.20.1 → Minecraft 26.2

> 本表记录将 [SimpleBedrockModel](https://github.com/MCModderAnchor/SimpleBedrockModel)（上游基线 2.6.1，Fabric 移植基于 [Sh1roCu](https://github.com/Sh1roCu/SimpleBedrockModel-Fabric)）迁移到 **Minecraft 26.2** 时，所涉及的 Minecraft / Fabric / Blaze3D API 变化与对应处理方式。
>
> 目标加载器：**Fabric**。26.1 起 Minecraft 不再混淆，故无 mappings 依赖。

---

## 〇、与上游版本的关系（同步说明）

本移植追踪上游 NeoForge 官方 **2.6.2**（版本号 `2.6.2+mc26.2`）。上游 2.6.2 相对 2.6.1 的改动，在 26.2 下的处理如实说明：

| 上游 2.6.2 改动 | 内容 | 26.2 处理 |
|---|---|---|
| `MeshIntegerAttributes.LightState` | 世界网格**运行时替换 UV2 实例光照**（`glVertexAttribDivisor` + 参数 buffer） | ⚠️ **等价降级**：26.2 `RenderPipeline.withVertexBinding` 静态声明、`VertexBuffer` 类已移除（改为 `GpuBuffer`），无运行时改 UV2 属性来源钩子。以「CPU 顶点副本补丁 + `CommandEncoder.writeToBuffer` 整段回写」实现等价实例光照（语义等价，非 1:1，精确逐实例读取能力损失） |
| `StaticMeshCache` / `MeshIntegerAttributes` 删除 `glGetError` 检查 | 规避 AMD 驱动 `glGetError` 原生访问违规 | ✅ 天然适用：26.2 重写后走 `GpuBuffer` 同步上传，本无 `glGetError` 调用点 |
| `accesstransformer.cfg` 新增 `VertexBuffer.vertexBufferId`/`arrayObjectId` | NeoForge AT 暴露字段 | ❌ 不适用：26.2 无 `VertexBuffer`；Fabric 用 accesswidener |

> 结论：上游 2.6.2 的实质改动只有「运行时实例光照」一项，它在 26.2 架构下无法 1:1 移植，
> 本移植以「CPU 补丁 + 整段回写」等价降级覆盖（见 §五 有界差异）。其余改动在 26.2 下天然满足或不存在。

---

## 一、纯改名 / 改包（机械替换）

| 旧（1.20.1） | 新（26.2） | 说明 |
|---|---|---|
| `net.minecraft.resources.ResourceLocation` | `net.minecraft.resources.Identifier` | 类改名 |
| `net.minecraft.client.renderer.RenderType` | `net.minecraft.client.renderer.rendertype.RenderType` | 改包；另拆出 `PreparedRenderType` |
| `net.minecraft.client.renderer.RenderStateShard` | `net.minecraft.client.renderer.RenderPipeline` | 类改名 |
| `net.minecraft.client.renderer.VertexConsumer` | `com.mojang.blaze3d.vertex.VertexConsumer` | 改包到 Blaze3D |
| `net.minecraft.client.renderer.RenderSystem` | `com.mojang.blaze3d.systems.RenderSystem` | 改包到 Blaze3D |

## 二、已移除，需重写替代

| 旧（1.20.1） | 26.2 状态 | 替代方案 |
|---|---|---|
| `net.minecraft.client.renderer.MultiBufferSource` | 已移除 | `SubmitNodeCollector` / `PreparedRenderType` 体系 |
| `net.minecraft.client.renderer.chunk.ChunkRenderDispatcher` | 已移除 | collector 架构 |
| `net.minecraft.client.renderer.LightTexture` | 已移除 | 光照走新管线 / `BindGroupLayout` |
| `Tesselator` / `VertexMultiConsumer` | 已移除 | `BufferBuilder` / `SheetedDecalTextureGenerator` |
| `DefaultVertexFormat.EMPTY` | 已移除 | `null` / 空数组 |

## 三、渲染架构范式变化（需人工重写）

| 旧范式（即时渲染） | 新范式（26.2 提交式渲染） |
|---|---|
| `MultiBufferSource` + `VertexConsumer` 即时写入 | `SubmitNodeCollector` 提交自定义几何 |
| `consumer.vertex(x,y,z).color(..).uv(..).endVertex()` | `collector.submitCustomGeometry(pose, renderType, (pose, consumer) -> {...})` |
| 手动管线 `RenderStateShard` 组合 | `RenderPipeline.builder()` 链式声明 + `RenderPipelines.register(...)` |

**v1 渲染链路**：已整体迁移至 `SubmitNodeCollector` 范式。
**v2 GPU 子系统**：见下文"有界差异"。

## 四、RenderPipeline 关键签名对照

| 26.1.x | 26.2 |
|---|---|
| `withVertexFormat(...)` | `withVertexBinding(int index, VertexFormat format)` |
| 在 `VertexFormat` 内给 `Mode` | 单独 `withPrimitiveTopology(PrimitiveTopology)` |
| `withUniform(...)` / `withSampler(...)` | `withBindGroupLayout(...)` |
| `withColorTargetState(...)` | 需 int 索引；另有 `withUnusedColorTargetState(int)` |

- `VertexFormatElement` 改为 record：`(String name, int offset, GpuFormat format)`
- 枚举外移：`VertexFormat.IndexType` → `com.mojang.blaze3d.IndexType`；`VertexFormat.Mode` → `PrimitiveTopology`
- `drawIndexed` 签名增至 5 参：`drawIndexed(indexCount, instanceCount, firstIndex, baseVertex, firstInstance)`
- 深度比较反转：`LESS_THAN_OR_EQUAL` → `GREATER_THAN_OR_EQUAL`

## 五、有界差异（因 26.2 API 变化产生的降级，如实记录）

> 以下差异是 **26.2 渲染 API 架构变更**导致的、无法 1:1 等价实现的部分，非实现遗漏。均已在源码注释中标注。

| 功能 | 上游做法（1.20.1） | 26.2 移植做法 | 差异原因 |
|---|---|---|---|
| v2 实例光照 | `MeshIntegerAttributes` 运行时改 UV2 属性来源（`glVertexAttribIPointer` divisor） | CPU 顶点副本补丁 + `CommandEncoder.writeToBuffer` 整段回写 | `RenderPipeline.withVertexBinding` 静态声明，`VertexBuffer` 已移除（改 `GpuBuffer`），无运行时改 UV2 来源钩子 |
| 屏幕渲染事件 | Forge `ScreenEvent` 逐帧回调 | Fabric「屏幕打开即活跃」保守等价 | Fabric 无 ScreenEvent 等价物 |
| 纹理复制（blit） | 逐像素 `copyTextureToTexture` | `RenderTarget.blitAndBlendToTexture(...)` | 26.2 blit 本质为全屏三角形，非逐像素等价 |

> ⚠️ 提示：上表第 1、2 项为架构降级；第 3 项为语义等价近似。均为已知边界，社区复用时请留意。

## 六、第三方依赖

| 依赖 | 用途 | 许可 | 处理 |
|---|---|---|---|
| [mocha](https://github.com/unnamed/mocha) | Molang 词法/语法/运行时 | MIT | 源码内嵌（`v1/molang/**`），保留原版权头 |
| [MaydayAnimationEngine](https://github.com/286799714/MaydayAnimationEngine)（MAE） | 动画关键帧基类（`BaseKeyframe`） | — | jar-in-jar 嵌入 |
| [Particle Storm](https://github.com/westernat/ParticleStorm) | snowstorm 粒子实现灵感来源 | — | 实现参考（`v1/particle/**`） |
| [Forge Config API Port](https://modrinth.com/mod/forge-config-api-port) | 配置系统运行时依赖 | — | 外部依赖 |

---

> 本表为迁移说明，非上游 API 的完整文档。上游 API 的权威说明请以 [MCModderAnchor/SimpleBedrockModel](https://github.com/MCModderAnchor/SimpleBedrockModel) 为准。
