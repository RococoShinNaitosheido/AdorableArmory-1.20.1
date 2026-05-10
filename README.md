# AdorableArmory-1.20.1

AdorableArmory 是一个基于 **Minecraft Forge 1.20.1** 开发的模组项目，目前主要围绕角色、武器、方块、Boss 相关效果，以及自定义渲染系统进行开发。

## 版本与环境

- Minecraft：`1.20.1`
- Mod Loader：`Forge`
- 客户端光影兼容测试：`Oculus` + `Embeddium`

## 当前内容

- `LolaBlock`：带有 Cosmic 星空渲染层的方块。
- `ScarletLoraAlysiaEggColor`：带有 `SKY_ITEM` 星空物品渲染层的 Boss 刷怪蛋。
- `TrueDemonBowItem`：带有自定义描边与自定义附魔 glint 渲染的武器。
- `TrueDemonArrowItem`：TrueDemonBowItem 的箭矢物品。
- `OrePerspectiveCore`：测试物品，用于透视显示地下矿物。
- Boss 与技能特效系统仍在持续开发中。

## 光影兼容

目前已针对 **Oculus + Embeddium** 环境进行了渲染兼容处理，并重点测试了真正开启 shaderpack 后的表现。

已兼容和修复的渲染部分包括：

- Cosmic 渲染层在物品、方块、第一人称、第三人称、ItemEntity、GUI 等场景下的显示。
- `SKY_ITEM` 渲染层在非 GUI、ItemEntity、玩家手持、背包玩家模型窗口等场景下的显示。
- Lola 方块破坏粒子的 Cosmic 渲染层。
- 物品描边渲染在普通视角与背包玩家模型窗口中的显示。
- 自定义附魔 glint 渲染在 Oculus shaderpack 下的兼容显示。
- 针对部分 shaderpack 可能出现的延迟渲染、深度、混合和批处理问题进行了兼容处理。

相关兼容代码已整理到：

- `client/compat/oculus`
- `client/compat/oculus/itemoutline`
- `client/compat/oculus/glint`

## Shader Layer 接口

现在给任意 `Item` 添加 `Cosmic` 或 `SKY_ITEM` 渲染层时，不再需要在主类中集中注册，也不需要必须在模型 JSON 中写 `loader: adorablearmory:cosmic`。

只需要让物品实现 `ShaderLayerItem`：

```java
public class MyItem extends Item implements ShaderLayerItem {
    private static final ShaderLayerProperties SHADER_LAYER =
            ShaderLayerProperties.cosmic(
                    ShaderLayerModelTransform.DEFAULT_ITEM,
                    AdorableArmory.path("item/my_mask")
            );

    @Override
    public ShaderLayerProperties getShaderLayer(ItemStack stack) {
        return SHADER_LAYER;
    }
}
```

`SKY_ITEM` 示例：

```java
ShaderLayerProperties.skyItem(
        ShaderLayerModelTransform.DEFAULT_ITEM,
        AdorableArmory.path("item/my_mask")
);
```

仍然需要提供对应的 mask 贴图，例如：

```text
assets/adorablearmory/textures/item/my_mask.png
```

## 最近更新

### 2026/05/10

- 完成 Cosmic / SKY_ITEM 渲染层与 Oculus shaderpack 的兼容处理。
- 修复 Lola 方块、ScarletLoraAlysiaEggColor、ItemEntity、第一人称、第三人称、GUI、背包玩家模型窗口中的多处渲染问题。
- 修复物品描边渲染与自定义附魔 glint 渲染在 Oculus + Embeddium 下的兼容问题。
- 新增 `ShaderLayerItem` 接口，方便开发者为任意 `Item` 一键启用 Cosmic 或 SKY_ITEM 渲染层。

## 开发者

RococoShin
