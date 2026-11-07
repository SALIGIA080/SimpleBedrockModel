# SimpleBedrockModel (Fabric)

> 简体中文 / English (bilingual)

An **unofficial Fabric port** of [SimpleBedrockModel](https://github.com/MCModderAnchor/SimpleBedrockModel) — a simple library for loading and rendering **Minecraft Bedrock Edition** entity models and animations in Java Edition.

这是 [SimpleBedrockModel](https://github.com/MCModderAnchor/SimpleBedrockModel) 的**非官方 Fabric 移植版**——一个用于在 Java 版中加载并渲染 **Minecraft 基岩版**实体模型与动画的轻量库。

This port targets **Minecraft 26.2** on the **Fabric** mod loader. The source
structure follows [Sh1roCu's Fabric port](https://github.com/Sh1roCu/SimpleBedrockModel-Fabric)
(1.20.1), adapted for the 26.2 rendering architecture.

本移植版面向 **Fabric** 模组加载器上的 **Minecraft 26.2**。源码结构沿用了
[Sh1roCu 的 Fabric 移植版](https://github.com/Sh1roCu/SimpleBedrockModel-Fabric)（1.20.1），
并针对 26.2 的渲染架构做了适配。

**26.2 port by [SALIGIA080](https://github.com/SALIGIA080)** (a personal hobby project).

**26.2 移植版作者：[SALIGIA080](https://github.com/SALIGIA080)**（个人兴趣项目）。

> ⚠️ Requires [Forge Config API Port](https://modrinth.com/mod/forge-config-api-port) at runtime.
> ⚠️ 运行时需要 [Forge Config API Port](https://modrinth.com/mod/forge-config-api-port)。

## Supported Features / 已支持特性

- ✅ **Bedrock Model** — full support for loading and rendering Bedrock Edition models
  **基岩模型** —— 完整支持加载与渲染基岩版模型
- ✅ **Bedrock Animation** — complex bone structures and transformations
  **基岩动画** —— 支持复杂的骨骼结构与变换
- ⌛ **Molang Support** — in progress (lexer / parser / runtime included)
  **Molang 支持** —— 进行中（词法/语法分析器与运行时已内置）
- ✅ **Animation Running** — a full-featured animation player
  **动画播放** —— 功能完整的动画播放器
- ✅ **Animation Blending** — blendspace, layered, and kinematic interpolation blending
  **动画混合** —— 支持混合空间、分层与运动学插值混合
- ✅ **Resource Pack Support** — load models and animations from resource packs
  **资源包支持** —— 可从资源包加载模型与动画
- ✅ **Particle Rendering (billboard / snowstorm)** — first-person muzzle-flash & smoke
  particles are correctly camera-facing and atlas-mapped on 26.2
  **粒子渲染（billboard / snowstorm）** —— 第一人称枪口火焰与烟雾粒子在 26.2 上正确面向相机、正确映射 atlas

> **枪口粒子修复（2026-11-06 ~ 11-07）**：修复第一人称枪口火焰「垂直竖立 / 粘连」与烟雾「错位 / 青色条」。
> 根因有三处，均已对齐 26.2 真实渲染语义：
> 1. `BillboardHelper` 的 billboard 轴改用摄像机 `getViewRotationMatrix()` 的**列向量**（火焰恒横向面向相机）；
> 2. `SnowStormParticle` 的贴图内 0~1 UV 线性映射到 atlas 内当前 sprite 区间（烟雾不再采到整张 atlas 大图）；
> 3. `ParticleTextureBridge` 的 atlas id 改用 `AtlasIds.PARTICLES`、sprite 名保留 `particle/` 前缀（不再 missing sprite / 崩溃）。
> 详见 [docs/API-MIGRATION.md](docs/API-MIGRATION.md)。

## Quick Start / 快速开始

### For Mod Developers / 模组开发者

Add SimpleBedrockModel as a dependency of your mod:

在你的模组中声明 SimpleBedrockModel 为依赖：

```gradle
repositories {
    maven { url = "https://api.modrinth.com/maven" }
}

dependencies {
    // The animation library (MAE) is already included in the jar (jar-in-jar),
    // but Modrinth Maven cannot handle transitive dependencies, so you must
    // declare it to pass compilation.
    // 动画库 (MAE) 已以 jar-in-jar 方式内嵌，但 Modrinth Maven 无法解析传递依赖，
    // 因此仍需声明它才能通过编译。
    compileOnly("com.maydaymemory:mae:1.1.4")
}
```

This library is intended to be embedded via **jar-in-jar** and ships both the
legacy `v1` API and the newer `v2` GPU-mesh rendering pipeline.

本库设计为通过 **jar-in-jar** 内嵌使用，同时提供旧版 `v1` API 与较新的 `v2` GPU 网格渲染管线。

### Render Tick Event / 渲染帧事件

`RenderTickEvent` supplies the type and the subscription only — the library does
**not** dispatch it from `Minecraft#runTick`. The host mod posts it once per frame
(`START` before rendering, `END` after); TaCZ does this in `GameRenderer#render`
at `HEAD` / `RETURN`, which matches the original Forge semantics.

`RenderTickEvent` 只提供事件类型与订阅注册，库内**不再**从 `Minecraft#runTick` 自行
触发。触发点由宿主模组在每帧渲染前后各 post 一次（TaCZ 在 `GameRenderer#render` 的
`HEAD` / `RETURN` 触发，与 Forge 原本的语义一致）。这样内嵌进 TaCZ 时每帧只有一次
触发；若库侧也触发一次，动画时钟会走两倍速、分划板会被画两遍。

## Usage Examples / 使用示例

### Loading a Bedrock Model / 加载基岩模型

```java
// GsonUtil provides a Gson instance pre-configured for Bedrock POJOs.
// GsonUtil 提供了一个为基岩 POJO 预先配置好的 Gson 实例。
InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
BedrockModelPOJO pojo = GsonUtil.GSON.fromJson(reader, BedrockModelPOJO.class);
BedrockModel model = new BedrockModel(pojo);
```

### Loading Bedrock Animations / 加载基岩动画

```java
// BoneIndexProvider resolves bone names to indices; implementations derive
// it from a BedrockModel's bone tree.
// BoneIndexProvider 将骨骼名称解析为索引；实现类从 BedrockModel 的骨骼树推导。
BoneIndexProvider indexProvider = ...;

InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
BedrockAnimationFile file = GsonUtil.GSON.fromJson(reader, BedrockAnimationFile.class);

// A single BedrockAnimationFile may contain many named animations.
// 单个 BedrockAnimationFile 可包含多个具名动画。
List<BedrockAnimation> animations = BedrockAnimation.createAnimation(file, indexProvider);

// Or create a single animation by name.
// 或按名称创建单个动画。
BedrockAnimation animation = BedrockAnimation.createAnimation("animation.name", animationPOJO, indexProvider);
```

## Building / 构建

Requires **JDK 25** and **Gradle 9.5.1**.

需要 **JDK 25** 与 **Gradle 9.5.1**。

```bash
./gradlew build        # Linux / macOS
gradlew.bat build      # Windows
```

The mod jar is written to `build/libs/`.

模组 jar 会输出到 `build/libs/`。

## API Migration Notes / API 迁移说明

See [docs/API-MIGRATION.md](docs/API-MIGRATION.md) for a table mapping the
upstream (1.20.1) Minecraft / Fabric / Blaze3D APIs to their Minecraft 26.2
equivalents, including the known bounded differences introduced by the 26.2
rendering architecture changes.

请参见 [docs/API-MIGRATION.md](docs/API-MIGRATION.md)，其中以对照表形式列出了上游（1.20.1）
Minecraft / Fabric / Blaze3D API 到 Minecraft 26.2 对应项的映射，包括 26.2 渲染架构变更带来的已知有界差异。

## Third-Party Notices / 第三方声明

This project embeds and/or depends on the following third-party works, each
retained under its original license:

本项目内嵌和/或依赖以下第三方成果，均保留其原始许可证：

| Dependency 依赖 | Purpose 用途 | License 许可证 |
|---|---|---|
| [mocha](https://github.com/unnamed/mocha) | Molang lexer / parser / runtime（Molang 词法/语法/运行时） | MIT (© 2021-2025 Unnamed Team) |
| [MaydayAnimationEngine](https://github.com/286799714/MaydayAnimationEngine) (MAE) | Animation keyframe base classes（动画关键帧基类） | included as jar-in-jar（jar-in-jar 内嵌） |
| [Particle Storm](https://github.com/westernat/ParticleStorm) | snowstorm particle implementation reference（snowstorm 粒子实现参考） | reference / inspiration（参考/灵感） |
| [Forge Config API Port](https://modrinth.com/mod/forge-config-api-port) | runtime config system（运行时配置系统） | external dependency（外部依赖） |

The `mocha` sources under `.../v1/molang/**` retain their original MIT
copyright headers.

`.../v1/molang/**` 下的 `mocha` 源码保留了其原始 MIT 版权声明。

## Acknowledgments / 致谢

This project would not exist without the following works:

本项目离不开以下成果：

- **[MCModderAnchor/SimpleBedrockModel](https://github.com/MCModderAnchor/SimpleBedrockModel)**
  — the original SimpleBedrockModel library. Sincere thanks to the whole team
  (TartaricAcid, MaydayMemory, xjqsh, MoePus, Hidomatn and contributors) for
  building and open-sourcing it.
  —— SimpleBedrockModel 原库。衷心感谢整个团队（TartaricAcid、MaydayMemory、xjqsh、MoePus、Hidomatn 及各位贡献者）的构建与开源。
- **[Sh1roCu/SimpleBedrockModel-Fabric](https://github.com/Sh1roCu/SimpleBedrockModel-Fabric)**
  — the first Fabric port; this project's source structure is based on it.
  —— 首个 Fabric 移植版；本项目的源码结构基于它。
- **[mocha](https://github.com/unnamed/mocha)** (© Unnamed Team) — Molang engine. —— Molang 引擎。
- **[MaydayAnimationEngine](https://github.com/286799714/MaydayAnimationEngine)** — animation infrastructure. —— 动画基础设施。
- **[Particle Storm](https://github.com/westernat/ParticleStorm)** — snowstorm particle reference. —— snowstorm 粒子参考。

## License / 许可证

[GNU LGPL v3](LICENSE)

This is a derivative work of [MCModderAnchor/SimpleBedrockModel](https://github.com/MCModderAnchor/SimpleBedrockModel) (LGPL-3.0),
itself based on [Sh1roCu/SimpleBedrockModel-Fabric](https://github.com/Sh1roCu/SimpleBedrockModel-Fabric).

本项目是 [MCModderAnchor/SimpleBedrockModel](https://github.com/MCModderAnchor/SimpleBedrockModel)（LGPL-3.0）的衍生作品，
其本身又基于 [Sh1roCu/SimpleBedrockModel-Fabric](https://github.com/Sh1roCu/SimpleBedrockModel-Fabric)。

## Disclaimer / 免责声明

This is a **personal, non-professional hobby project**. It was created to
support a [Timeless and Classics Zero (TACZ)](https://github.com/MCModderAnchor/TACZ)
port for a **friend's private server**.

这是一个**个人、非专业的兴趣项目**。它是为了支持 [Timeless and Classics Zero (TACZ)](https://github.com/MCModderAnchor/TACZ)
的移植而创建，用于**朋友的私人服务器**。

- The 26.2 port was produced largely through "wishful programming" — writing
  code against the intended behaviour and iterating against the runtime.
  26.2 移植版很大程度上是"许愿式编程"的产物——对着预期行为写代码，再针对运行时反复迭代。
- It is **not** affiliated with the upstream SimpleBedrockModel team, and is
  **not** intended as a drop-in replacement for the official Forge/NeoForge
  releases.
  它**并非**上游 SimpleBedrockModel 团队的官方作品，也**不**旨在作为官方 Forge/NeoForge 版本的替代品。
- Bugs, rough edges, and non-1:1 approximations are to be expected. Please be
  kind (多多包涵) — patches and feedback are welcome, but set expectations accordingly.
  难免存在 Bug、粗糙之处和非 1:1 的近似实现。请多多包涵——欢迎提交补丁与反馈，但请相应调整预期。

If you find it useful, great. If you find it broken, that's probably also true.

如果你觉得它有用，那很好。如果你觉得它有 Bug，那大概也是真的。
