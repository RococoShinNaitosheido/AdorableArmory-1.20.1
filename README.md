# <span style="color:#FF6B9D">AdorableArmory-1.20.1</span>

### <span style="color:#4A90E2">版本与环境</span>
- **Minecraft 版本**：`1.20.1`
- **模组加载器**：`Forge`
- **光影兼容测试环境**：`Oculus` + `Embeddium`

### <span style="color:#FFB347">目前的内容</span>
- `AdorableArmory`（模组核心）
- `LolaBlock`（萝拉方块，带 Cosmic 星空渲染层）
- `薄暮恋刀`（目前仍是测试武器，未来会继续完善）
- `斯卡蕾特·萝拉·艾莉米娅` 刷怪蛋（Boss 刷怪蛋，带 `SKY_ITEM` 星空物品渲染层）
- `真魔之弓`（斯卡蕾特·萝拉·艾莉米娅的主要武器，带自定义描边与自定义附魔渲染）
- `真魔之箭`（真魔之弓的箭矢）
- `OrePerspectiveCore`（测试物品，目前功能是透视看见地下矿石，未来可继续完善...）
- 更多内容仍在慢慢制作中...

### <span style="color:#9B59B6">目前的实体</span>
- **Boss**：`斯卡蕾特·萝拉·艾莉米娅`（RococoShin 很喜欢的一位 Boss，正在努力设计中，目前还在缓慢制作...）
- 以及一些技术性实体，主要用于 Boss 的特效、攻击和渲染表现。

### <span style="color:#2ECC71">正在制作的特效和渲染</span>
- 一些用于 Boss 的特效渲染。
- Cosmic 星空渲染层。
- `SKY_ITEM` 星空物品渲染层。
- Item 描边渲染。
- 自定义附魔 glint 渲染。

### <span style="color:#1ABC9C">最近更新（2026/05/10）</span>
- 完成了 `Cosmic` 和 `SKY_ITEM` 渲染层与 `Oculus shaderpack` 的兼容处理。
- 修复了 Lola 方块、ScarletLoraAlysiaEggColor、ItemEntity、第一人称、第三人称、GUI、背包玩家模型窗口中的多处渲染问题。
- 修复了 Lola 方块破坏粒子的 Cosmic 渲染层。
- 修复了 Item 描边渲染在背包玩家模型窗口中的显示问题。
- 优化了自定义附魔 glint 渲染在不同 shaderpack 下的兼容表现。
- 整理了 Oculus 兼容相关代码结构，方便后续继续维护。
- 新增 `ShaderLayerItem` 接口，以后给任意 `Item` 添加 Cosmic / SKY_ITEM 渲染层会更方便。

### <span style="color:#3498DB">目前兼容部分</span>
- 描边渲染支持光影模组：`Oculus`、`Embeddium`
- Cosmic 渲染层支持真正开启 `Oculus shaderpack` 后的物品、方块、ItemEntity、手持、GUI 等场景。
- `SKY_ITEM` 渲染层支持真正开启 `Oculus shaderpack` 后的非 GUI、ItemEntity、玩家手持、背包玩家模型窗口等场景。
- 自定义附魔 glint 渲染已针对部分 shaderpack 的亮度和显示问题做兼容处理。

### <span style="color:#F39C12">开发者渲染接口</span>
以后如果想让一个 `Item` 开启 Cosmic 或 `SKY_ITEM` 渲染层，不需要再去 `AdorableArmory` 主类集中注册。

只需要让物品实现 `ShaderLayerItem`，例如：

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

`SKY_ITEM` 可以使用：

```java
ShaderLayerProperties.skyItem(
        ShaderLayerModelTransform.DEFAULT_ITEM,
        AdorableArmory.path("item/my_mask")
);
```

注意：仍然需要准备对应的 mask 贴图，例如：

```text
assets/adorablearmory/textures/item/my_mask.png
```

---

### <span style="color:#E74C3C">开发者</span>
> RococoShin 正在慢慢制作这个模组... QwQ
