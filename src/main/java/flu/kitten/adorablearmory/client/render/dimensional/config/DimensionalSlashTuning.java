package flu.kitten.adorablearmory.client.render.dimensional.config;

public final class DimensionalSlashTuning {

    public static final class Quick {
        public static final class WorldSlash {
            public static final float WIDTH_SCALE = 1.6f; // 世界空间全部斩击宽度总倍率；想整体变细/变粗优先调这里。
            public static final float ALPHA_SCALE = 1.0f; // 世界空间全部斩击透明度总倍率；想整体更亮/更淡优先调这里。
            public static final float FINE_LINE_SCALE = 0.70f; // 世界空间极细斩线和杂乱细线存在感倍率；想减少繁琐细线优先调这里。
            public static final float BLOOM_INTENSITY_SCALE = 0.80f; // 世界空间泛光亮度总倍率；想整体 bloom 更炸/更收敛优先调这里。
            public static final float BLOOM_RADIUS_SCALE = 0.80f; // 世界空间泛光扩散范围总倍率；想光晕更大/更紧优先调这里。

            private WorldSlash() {}
        }

        public static final class ScreenFx {
            public static final float UOM_SCALE = 1.0f; // 尾端黑白闪、反色、径向爆光整体强度总倍率。
            public static final float RADIAL_CHROMA_SCALE = 1.0f; // 尾端径向色散整体强度总倍率。

            private ScreenFx() {}
        }

        public static final class ScreenGlass {
            public static final float COUNT_SCALE = 1.2f; // 屏幕玻璃碎片数量倍率，性能不够时优先降低。
            public static final float EXPLOSION_SCALE = 1.0f; // 屏幕玻璃碎片从中心爆开的速度总倍率。
            public static final float GRAVITY_SCALE = 1.0f; // 屏幕玻璃碎片爆开后下落重力总倍率。
            public static final float TUMBLE_SCALE = 1.0f; // 屏幕玻璃碎片 3D 翻滚角速度总倍率。
            public static final float THICKNESS_SCALE = 1.0f; // 屏幕玻璃碎片厚度总倍率；想让碎片更像实体玻璃优先调这里。

            private ScreenGlass() {}
        }

        public static final float WORLD_SLASH_WIDTH_SCALE = WorldSlash.WIDTH_SCALE;
        public static final float WORLD_SLASH_ALPHA_SCALE = WorldSlash.ALPHA_SCALE;
        public static final float WORLD_FINE_LINE_SCALE = WorldSlash.FINE_LINE_SCALE;
        public static final float WORLD_BLOOM_INTENSITY_SCALE = WorldSlash.BLOOM_INTENSITY_SCALE;
        public static final float WORLD_BLOOM_RADIUS_SCALE = WorldSlash.BLOOM_RADIUS_SCALE;
        public static final float UOM_SCREEN_FX_SCALE = ScreenFx.UOM_SCALE;
        public static final float RADIAL_CHROMA_SCALE = ScreenFx.RADIAL_CHROMA_SCALE;
        public static final float GLASS_SHARD_COUNT_SCALE = ScreenGlass.COUNT_SCALE;
        public static final float GLASS_SHARD_EXPLOSION_SCALE = ScreenGlass.EXPLOSION_SCALE;
        public static final float GLASS_SHARD_GRAVITY_SCALE = ScreenGlass.GRAVITY_SCALE;
        public static final float GLASS_SHARD_TUMBLE_SCALE = ScreenGlass.TUMBLE_SCALE;
        public static final float GLASS_SHARD_THICKNESS_SCALE = ScreenGlass.THICKNESS_SCALE;

        private Quick() {}
    }

    public static final class WorldSlash {
        public static final class CommandDefaults {
            public static final int DEFAULT_SLASHES = 7; // 指令未指定时默认生成的世界空间斩击数量。
            public static final float DEFAULT_LENGTH = 9.0f; // 指令未指定时默认每条斩击的长度。
            public static final float DEFAULT_RADIUS = 3.2f; // 指令未指定时斩击围绕玩家散布的默认半径。
            public static final int MIN_SLASHES = 1; // 指令允许的最少斩击数量。
            public static final int MAX_SLASHES = 14; // 指令允许的最大斩击数量。
            public static final float MIN_LENGTH = 2.0f; // 指令允许的最短斩击长度。
            public static final float MAX_LENGTH = 128.0f; // 指令允许的最长斩击长度。
            public static final float MIN_RADIUS = 0.0f; // 指令允许的最小散布半径。
            public static final float MAX_RADIUS = 24.0f; // 指令允许的最大散布半径。

            private CommandDefaults() {}
        }

        public static final class Timeline {
            public static final int LINE_REVEAL_TICKS = 1; // 单条斩击从无到完整显示所需时间，单位 tick。
            public static final int LINE_DELAY_TICKS = 0; // 相邻两条斩击依次出现的间隔，单位 tick。
            public static final int LINE_HOLD_TICKS = 15; // 单条斩击完全显示后保持不消失的时间，单位 tick。
            public static final int LINE_COLLAPSE_TICKS = 10; // 旧收缩动画保留参数，当前主体斩线最终统一淡出。
            public static final int LINE_COLLAPSE_FADE_TICKS = 8; // 旧收缩动画保留参数，当前主体斩线最终统一淡出。
            public static final int FINAL_BURST_DELAY_TICKS = 10; // 全部世界斩显示完成后触发最终爆发的延迟，单位 tick。
            public static final int FINAL_FADE_TICKS = 10; // 屏幕碎裂触发后世界斩完全淡出的时间，单位 tick。

            private Timeline() {}
        }

        public static final class Distribution {
            public static final float LINE_YAW_JITTER = 1.32f; // 斩击水平角度随机偏移幅度，越大交错越乱。
            public static final float LINE_PITCH_JITTER = 1.0f; // 斩击上下倾斜随机偏移幅度，越大立体感越强。
            public static final float LINE_SIDE_OFFSET_MULTIPLIER = 1.25f; // 斩击相对玩家的横向散布倍率。
            public static final float LINE_UP_OFFSET_MULTIPLIER = 0.75f; // 斩击相对玩家的上下散布倍率。
            public static final float LINE_LENGTH_MIN_MULTIPLIER = 0.78f; // 随机斩击长度的基础倍率。
            public static final float LINE_LENGTH_RANDOM_MULTIPLIER = 0.38f; // 随机斩击长度的额外浮动倍率。
            public static final float LINE_WIDTH_MIN = 0.18f; // 每条斩击基础宽度的最小值。
            public static final float LINE_WIDTH_RANDOM = 0.12f; // 每条斩击基础宽度的随机增加量。
            public static final int MAX_FRAGMENTS = 100; // 世界空间碎片/粒子残片允许保留的最大数量。

            private Distribution() {}
        }

        public static final class Palette {
            public static final int OUTER_COLOR_PRIMARY = 0xFF2DF2; // 主紫粉色外光颜色。
            public static final int OUTER_COLOR_ALT_A = 0xC928FF; // 备用外光颜色A。
            public static final int OUTER_COLOR_ALT_B = 0xFF5DFF; // 备用外光颜色B。
            public static final int CORE_COLOR_PRIMARY = 0xFFFFFF; // 主白色核心刀光颜色。
            public static final int CORE_COLOR_ALT = 0xFFE5FF; // 备用偏粉白核心/边线颜色。
            public static final float OUTER_COLOR_PRIMARY_CHANCE = 0.58f; // 使用主外光颜色的概率。
            public static final float CORE_COLOR_ALT_CHANCE = 0.32f; // 使用备用核心颜色的概率。

            private Palette() {}
        }

        public static final class Overall {
            public static final float FLICKER_BASE = 0.96f; // 世界斩亮度闪烁的基础亮度。
            public static final float FLICKER_RANDOM = 0.035f; // 世界斩亮度闪烁的浮动幅度。
            public static final float FLICKER_SPEED = 1.70f; // 世界斩亮度闪烁速度。

            private Overall() {}
        }

        public static final class PurpleLayer {
            public static final float WIDTH = 0.50f; // 主斩紫色第二层宽度倍率。
            public static final float ALPHA = 0.55f; // 主斩紫色第二层透明度。
            public static final float SOFT_EDGE = 0.80f; // 主斩紫色第二层宽度方向渐隐比例。

            private PurpleLayer() {}
        }

        public static final class WhiteCore {
            public static final float WIDTH = 0.132f; // 主斩白色核心宽度倍率。
            public static final float ALPHA = 0.94f; // 主斩白色核心透明度。
            public static final float SOFT_EDGE = 0.90f; // 主斩白色核心宽度方向渐隐比例。

            private WhiteCore() {}
        }

        public static final class Shape {
            public static final int TAPER_SEGMENTS = 20; // 斩击锥形刀光的细分段数，越高越平滑但越耗性能。
            public static final float TAPER_POWER = 2.45f; // 斩击两端尖锐程度，越大两端越尖。
            public static final float TAPER_BELLY_MIN = 0.42f; // 斩击中段鼓起的保底宽度比例。
            public static final float TAPER_MID_WIDTH_BOOST = 1.60f; // 斩击头-中-尾曲线中段额外加宽倍率。
            public static final float TAPER_MID_WIDTH_POWER = 1.10f; // 中段加宽覆盖范围，越小越向两侧铺开。
            public static final float ALPHA_POWER = 1.12f; // 斩击两端 alpha 淡出曲线强度。
            public static final float ALPHA_MULTIPLIER = 1.02f; // 斩击整体 alpha 放大倍率。

            private Shape() {}
        }

        public static final class Needle {
            public static final float PROGRESS_START = 0.22f; // 主斩显示到该进度后开始绘制延伸细斩。
            public static final float EXTEND_BASE = 0.12f; // 延伸细斩的基础延伸长度倍率。
            public static final float EXTEND_PROGRESS = 0.08f; // 延伸细斩随显示进度增加的额外长度倍率。
            public static final float START_BACK = 0.72f; // 延伸细斩向起点后方延伸的比例。
            public static final float END_FORWARD = 1.0f; // 延伸细斩向终点前方延伸的比例。
            public static final float ALPHA_BASE = 0.24f; // 延伸细斩基础透明度。
            public static final float ALPHA_PROGRESS = 0.22f; // 延伸细斩随显示进度增加的透明度。
            public static final float OUTER_WIDTH = 0.20f; // 延伸细斩外光宽度倍率。
            public static final float OUTER_ALPHA = 0.52f; // 延伸细斩外光透明度。
            public static final float CORE_WIDTH = 0.032f; // 延伸细斩白芯宽度倍率。
            public static final float CORE_ALPHA = 0.62f; // 延伸细斩白芯透明度。

            private Needle() {}
        }

        public static final class FineNeedle {
            public static final float OUTER_WIDTH = 0.050f; // 极细附加斩线外光宽度倍率。
            public static final float OUTER_ALPHA = 0.30f; // 极细附加斩线外光透明度。
            public static final float CORE_WIDTH = 0.018f; // 极细附加斩线白芯宽度倍率。
            public static final float CORE_ALPHA = 0.26f; // 极细附加斩线白芯透明度。
            public static final float SIDE_BASE = 1.15f; // 极细附加斩线侧向偏移基础倍率。
            public static final float SIDE_RANDOM = 0.95f; // 极细附加斩线侧向偏移随机倍率。
            public static final float START_BASE = 0.18f; // 极细附加斩线起点后拉基础倍率。
            public static final float START_RANDOM = 0.25f; // 极细附加斩线起点后拉随机倍率。
            public static final float END_BASE = 0.24f; // 极细附加斩线终点前伸基础倍率。
            public static final float END_PROGRESS = 0.30f; // 极细附加斩线终点随进度前伸倍率。
            public static final float END_SIDE_PULL = -0.22f; // 极细附加斩线终点侧向拉扯方向和强度。

            private FineNeedle() {}
        }

        public static final class Hairline {
            public static final float PROGRESS_START = 0.48f; // 主斩显示到该进度后开始绘制杂乱细线。
            public static final int MIN_COUNT = 1; // 每条主斩至少生成的杂乱细线数量。
            public static final int EXTRA_COUNT = 0; // 触发额外生成时增加的杂乱细线数量。
            public static final int EXTRA_CHANCE_MODULO = 3; // 控制额外杂乱细线出现频率的取模值。
            public static final float ANGLE_SPREAD = 1.65f; // 杂乱细线角度散布范围。
            public static final float INDEX_ANGLE_OFFSET = 0.34f; // 多条杂乱细线之间的角度错开量。
            public static final float NORMAL_MIX = 0.78f; // 杂乱细线向斩面法线方向混合的强度。
            public static final float SIDE_MIX = 0.46f; // 杂乱细线向侧向混合的强度。
            public static final float LENGTH_MIN = 0.42f; // 杂乱细线长度的基础倍率。
            public static final float LENGTH_RANDOM = 0.58f; // 杂乱细线长度的随机增加倍率。
            public static final float REVEAL_START = 0.24f; // 杂乱细线开始显现的进度。
            public static final float REVEAL_PORTION = 0.76f; // 杂乱细线从无到完整显示占用的进度区间。
            public static final float OFFSET_NORMAL = 0.18f; // 杂乱细线中心点沿法线偏移幅度。
            public static final float OFFSET_SIDE = 0.15f; // 杂乱细线中心点沿侧向偏移幅度。
            public static final float ALPHA_MIN = 0.06f; // 杂乱细线透明度基础值。
            public static final float ALPHA_RANDOM = 0.12f; // 杂乱细线透明度随机增加值。
            public static final float OUTER_WIDTH_MIN = 0.075f; // 杂乱细线外光宽度基础值。
            public static final float OUTER_WIDTH_RANDOM = 0.060f; // 杂乱细线外光宽度随机增加值。
            public static final float CORE_WIDTH_MIN = 0.018f; // 杂乱细线白芯宽度基础值。
            public static final float CORE_WIDTH_RANDOM = 0.015f; // 杂乱细线白芯宽度随机增加值。
            public static final float CORE_ALPHA = 0.42f; // 杂乱细线白芯透明度。

            private Hairline() {}
        }

        public static final class WorldGlassShards {
            public static final boolean ENABLED = true; // 是否在最终爆发时生成世界空间玻璃碎片。
            public static final int COUNT = 168; // 最终爆发生成的世界空间碎片数量，越高越震撼也越耗性能。
            public static final int LIFETIME_TICKS = 60; // 世界空间碎片持续时间，单位 tick。
            public static final float SPAWN_RADIUS = 1.32f; // 碎片初始散布半径，基于最终爆发中心。
            public static final float SIZE_MIN = 0.18f; // 单片世界碎片最小尺寸。
            public static final float SIZE_MAX = 0.82f; // 单片世界碎片最大尺寸。
            public static final float EXPLOSION_SPEED = 0.18f; // 碎片从中心爆开的基础速度，单位约为方块/tick。
            public static final float EXPLOSION_RANDOM = 0.24f; // 碎片爆开速度随机增量。
            public static final float UPWARD_BIAS = 0.06f; // 碎片初始向上的速度补偿。
            public static final float GRAVITY = 0.0065f; // 碎片爆开后的下坠重力。
            public static final float TUMBLE_SPEED = 0.17f; // 碎片 3D 翻滚速度。
            public static final float ALPHA = 0.88f; // 世界空间碎片整体透明度。
            public static final float ALPHA_FADE_START = 0.60f; // 世界空间碎片生命周期到该比例后，只通过 alpha 平滑淡出到 0。
            public static final float REFRACTION = 7.0f; // 世界空间碎片采样当前画面的折射强度。
            public static final float EDGE_HIGHLIGHT = 0.72f; // 世界空间碎片边缘玻璃高光强度。
            public static final float THICKNESS = 0.075f; // 世界空间碎片的实体挤出厚度。
            public static final float MIRROR_STRENGTH = 0.78f; // 世界空间碎片实时屏幕空间反射强度。
            public static final float BLOOM_WIDTH = 0.055f; // 世界空间玻璃碎片写入 bloom mask 的边缘宽度。
            public static final float BLOOM_ALPHA = 0.52f; // 世界空间玻璃碎片边缘泛光强度。
            public static final float MOTION_BLUR_TICKS = 2.0f; // 世界空间碎片移动拖尾回看时间，单位 tick。
            public static final float MOTION_BLUR_ALPHA = 0.32f; // 世界空间碎片移动拖尾强度。
            public static final float CRACK_LIGHT_LENGTH = 0.42f; // 世界空间碎片裂缝透光向外散射长度。
            public static final float CRACK_LIGHT_WIDTH = 0.075f; // 世界空间碎片裂缝透光散射宽度。
            public static final float CRACK_LIGHT_ALPHA = 0.58f; // 世界空间碎片裂缝透光强度。

            private WorldGlassShards() {}
        }

        public static final class Bloom {
            public static final boolean ENABLED = true; // 是否启用世界斩后处理泛光。
            public static final int BLUR_ITERATIONS = 4; // 泛光模糊迭代次数，越高越柔也越耗性能。
            public static final float MASK_WIDTH = 0.62f; // 写入泛光遮罩的主斩宽度倍率。
            public static final float MASK_ALPHA = 0.62f; // 写入泛光遮罩的主斩强度。
            public static final float MASK_NEEDLE_WIDTH = 0.22f; // 写入泛光遮罩的延伸细斩宽度倍率。
            public static final float MASK_HAIRLINE_WIDTH = 0.10f; // 写入泛光遮罩的杂乱细线宽度倍率。
            public static final float BLUR_RADIUS = 3f; // 泛光扩散半径。
            public static final float INTENSITY = 1.032f; // 泛光亮度强度。
            public static final float COMPOSITE_ALPHA = 0.50f; // 泛光叠回主画面的透明度。

            private Bloom() {}
        }

        public static final int DEFAULT_SLASHES = CommandDefaults.DEFAULT_SLASHES;
        public static final float DEFAULT_LENGTH = CommandDefaults.DEFAULT_LENGTH;
        public static final float DEFAULT_RADIUS = CommandDefaults.DEFAULT_RADIUS;
        public static final int MIN_SLASHES = CommandDefaults.MIN_SLASHES;
        public static final int LINE_REVEAL_TICKS = Timeline.LINE_REVEAL_TICKS;
        public static final int LINE_DELAY_TICKS = Timeline.LINE_DELAY_TICKS;
        public static final int LINE_HOLD_TICKS = Timeline.LINE_HOLD_TICKS;
        public static final int LINE_COLLAPSE_TICKS = Timeline.LINE_COLLAPSE_TICKS;
        public static final int LINE_COLLAPSE_FADE_TICKS = Timeline.LINE_COLLAPSE_FADE_TICKS;
        public static final int FINAL_BURST_DELAY_TICKS = Timeline.FINAL_BURST_DELAY_TICKS;
        public static final int FINAL_FADE_TICKS = Timeline.FINAL_FADE_TICKS;
        public static final int MAX_SLASHES = CommandDefaults.MAX_SLASHES;
        public static final int MAX_FRAGMENTS = Distribution.MAX_FRAGMENTS;
        public static final int TAPER_SEGMENTS = Shape.TAPER_SEGMENTS;
        public static final float MIN_LENGTH = CommandDefaults.MIN_LENGTH;
        public static final float MAX_LENGTH = CommandDefaults.MAX_LENGTH;
        public static final float MIN_RADIUS = CommandDefaults.MIN_RADIUS;
        public static final float MAX_RADIUS = CommandDefaults.MAX_RADIUS;
        public static final float LINE_YAW_JITTER = Distribution.LINE_YAW_JITTER;
        public static final float LINE_PITCH_JITTER = Distribution.LINE_PITCH_JITTER;
        public static final float LINE_SIDE_OFFSET_MULTIPLIER = Distribution.LINE_SIDE_OFFSET_MULTIPLIER;
        public static final float LINE_UP_OFFSET_MULTIPLIER = Distribution.LINE_UP_OFFSET_MULTIPLIER;
        public static final float LINE_LENGTH_MIN_MULTIPLIER = Distribution.LINE_LENGTH_MIN_MULTIPLIER;
        public static final float LINE_LENGTH_RANDOM_MULTIPLIER = Distribution.LINE_LENGTH_RANDOM_MULTIPLIER;
        public static final float LINE_WIDTH_MIN = Distribution.LINE_WIDTH_MIN;
        public static final float LINE_WIDTH_RANDOM = Distribution.LINE_WIDTH_RANDOM;
        public static final boolean WORLD_SHARDS_ENABLED = WorldGlassShards.ENABLED;
        public static final int WORLD_SHARD_COUNT = WorldGlassShards.COUNT;
        public static final int WORLD_SHARD_LIFETIME_TICKS = WorldGlassShards.LIFETIME_TICKS;
        public static final float WORLD_SHARD_SPAWN_RADIUS = WorldGlassShards.SPAWN_RADIUS;
        public static final float WORLD_SHARD_SIZE_MIN = WorldGlassShards.SIZE_MIN;
        public static final float WORLD_SHARD_SIZE_MAX = WorldGlassShards.SIZE_MAX;
        public static final float WORLD_SHARD_EXPLOSION_SPEED = WorldGlassShards.EXPLOSION_SPEED;
        public static final float WORLD_SHARD_EXPLOSION_RANDOM = WorldGlassShards.EXPLOSION_RANDOM;
        public static final float WORLD_SHARD_UPWARD_BIAS = WorldGlassShards.UPWARD_BIAS;
        public static final float WORLD_SHARD_GRAVITY = WorldGlassShards.GRAVITY;
        public static final float WORLD_SHARD_TUMBLE_SPEED = WorldGlassShards.TUMBLE_SPEED;
        public static final float WORLD_SHARD_ALPHA = WorldGlassShards.ALPHA;
        public static final float WORLD_SHARD_ALPHA_FADE_START = WorldGlassShards.ALPHA_FADE_START;
        public static final float WORLD_SHARD_REFRACTION = WorldGlassShards.REFRACTION;
        public static final float WORLD_SHARD_EDGE_HIGHLIGHT = WorldGlassShards.EDGE_HIGHLIGHT;
        public static final float WORLD_SHARD_THICKNESS = WorldGlassShards.THICKNESS;
        public static final float WORLD_SHARD_MIRROR_STRENGTH = WorldGlassShards.MIRROR_STRENGTH;
        public static final float WORLD_SHARD_BLOOM_WIDTH = WorldGlassShards.BLOOM_WIDTH;
        public static final float WORLD_SHARD_BLOOM_ALPHA = WorldGlassShards.BLOOM_ALPHA;
        public static final float WORLD_SHARD_MOTION_BLUR_TICKS = WorldGlassShards.MOTION_BLUR_TICKS;
        public static final float WORLD_SHARD_MOTION_BLUR_ALPHA = WorldGlassShards.MOTION_BLUR_ALPHA;
        public static final float WORLD_SHARD_CRACK_LIGHT_LENGTH = WorldGlassShards.CRACK_LIGHT_LENGTH;
        public static final float WORLD_SHARD_CRACK_LIGHT_WIDTH = WorldGlassShards.CRACK_LIGHT_WIDTH;
        public static final float WORLD_SHARD_CRACK_LIGHT_ALPHA = WorldGlassShards.CRACK_LIGHT_ALPHA;
        public static final int OUTER_COLOR_PRIMARY = Palette.OUTER_COLOR_PRIMARY;
        public static final int OUTER_COLOR_ALT_A = Palette.OUTER_COLOR_ALT_A;
        public static final int OUTER_COLOR_ALT_B = Palette.OUTER_COLOR_ALT_B;
        public static final int CORE_COLOR_PRIMARY = Palette.CORE_COLOR_PRIMARY;
        public static final int CORE_COLOR_ALT = Palette.CORE_COLOR_ALT;
        public static final float OUTER_COLOR_PRIMARY_CHANCE = Palette.OUTER_COLOR_PRIMARY_CHANCE;
        public static final float CORE_COLOR_ALT_CHANCE = Palette.CORE_COLOR_ALT_CHANCE;
        public static final float FLICKER_BASE = Overall.FLICKER_BASE;
        public static final float FLICKER_RANDOM = Overall.FLICKER_RANDOM;
        public static final float FLICKER_SPEED = Overall.FLICKER_SPEED;
        public static final float MAIN_OUTER_WIDTH = PurpleLayer.WIDTH;
        public static final float MAIN_OUTER_ALPHA = PurpleLayer.ALPHA;
        public static final float MAIN_OUTER_SOFT_EDGE = PurpleLayer.SOFT_EDGE;
        public static final float MAIN_CORE_WIDTH = WhiteCore.WIDTH;
        public static final float MAIN_CORE_ALPHA = WhiteCore.ALPHA;
        public static final float MAIN_CORE_SOFT_EDGE = WhiteCore.SOFT_EDGE;
        public static final float NEEDLE_PROGRESS_START = Needle.PROGRESS_START;
        public static final float NEEDLE_EXTEND_BASE = Needle.EXTEND_BASE;
        public static final float NEEDLE_EXTEND_PROGRESS = Needle.EXTEND_PROGRESS;
        public static final float NEEDLE_START_BACK = Needle.START_BACK;
        public static final float NEEDLE_END_FORWARD = Needle.END_FORWARD;
        public static final float NEEDLE_ALPHA_BASE = Needle.ALPHA_BASE;
        public static final float NEEDLE_ALPHA_PROGRESS = Needle.ALPHA_PROGRESS;
        public static final float NEEDLE_OUTER_WIDTH = Needle.OUTER_WIDTH;
        public static final float NEEDLE_OUTER_ALPHA = Needle.OUTER_ALPHA;
        public static final float NEEDLE_CORE_WIDTH = Needle.CORE_WIDTH;
        public static final float NEEDLE_CORE_ALPHA = Needle.CORE_ALPHA;
        public static final float NEEDLE_FINE_OUTER_WIDTH = FineNeedle.OUTER_WIDTH;
        public static final float NEEDLE_FINE_OUTER_ALPHA = FineNeedle.OUTER_ALPHA;
        public static final float NEEDLE_FINE_CORE_WIDTH = FineNeedle.CORE_WIDTH;
        public static final float NEEDLE_FINE_CORE_ALPHA = FineNeedle.CORE_ALPHA;
        public static final float NEEDLE_FINE_SIDE_BASE = FineNeedle.SIDE_BASE;
        public static final float NEEDLE_FINE_SIDE_RANDOM = FineNeedle.SIDE_RANDOM;
        public static final float NEEDLE_FINE_START_BASE = FineNeedle.START_BASE;
        public static final float NEEDLE_FINE_START_RANDOM = FineNeedle.START_RANDOM;
        public static final float NEEDLE_FINE_END_BASE = FineNeedle.END_BASE;
        public static final float NEEDLE_FINE_END_PROGRESS = FineNeedle.END_PROGRESS;
        public static final float NEEDLE_FINE_END_SIDE_PULL = FineNeedle.END_SIDE_PULL;
        public static final float HAIRLINE_PROGRESS_START = Hairline.PROGRESS_START;
        public static final int HAIRLINE_MIN_COUNT = Hairline.MIN_COUNT;
        public static final int HAIRLINE_EXTRA_COUNT = Hairline.EXTRA_COUNT;
        public static final int HAIRLINE_EXTRA_CHANCE_MODULO = Hairline.EXTRA_CHANCE_MODULO;
        public static final float HAIRLINE_ANGLE_SPREAD = Hairline.ANGLE_SPREAD;
        public static final float HAIRLINE_INDEX_ANGLE_OFFSET = Hairline.INDEX_ANGLE_OFFSET;
        public static final float HAIRLINE_NORMAL_MIX = Hairline.NORMAL_MIX;
        public static final float HAIRLINE_SIDE_MIX = Hairline.SIDE_MIX;
        public static final float HAIRLINE_LENGTH_MIN = Hairline.LENGTH_MIN;
        public static final float HAIRLINE_LENGTH_RANDOM = Hairline.LENGTH_RANDOM;
        public static final float HAIRLINE_REVEAL_START = Hairline.REVEAL_START;
        public static final float HAIRLINE_REVEAL_PORTION = Hairline.REVEAL_PORTION;
        public static final float HAIRLINE_OFFSET_NORMAL = Hairline.OFFSET_NORMAL;
        public static final float HAIRLINE_OFFSET_SIDE = Hairline.OFFSET_SIDE;
        public static final float HAIRLINE_ALPHA_MIN = Hairline.ALPHA_MIN;
        public static final float HAIRLINE_ALPHA_RANDOM = Hairline.ALPHA_RANDOM;
        public static final float HAIRLINE_OUTER_WIDTH_MIN = Hairline.OUTER_WIDTH_MIN;
        public static final float HAIRLINE_OUTER_WIDTH_RANDOM = Hairline.OUTER_WIDTH_RANDOM;
        public static final float HAIRLINE_CORE_WIDTH_MIN = Hairline.CORE_WIDTH_MIN;
        public static final float HAIRLINE_CORE_WIDTH_RANDOM = Hairline.CORE_WIDTH_RANDOM;
        public static final float HAIRLINE_CORE_ALPHA = Hairline.CORE_ALPHA;
        public static final float TAPER_POWER = Shape.TAPER_POWER;
        public static final float TAPER_BELLY_MIN = Shape.TAPER_BELLY_MIN;
        public static final float TAPER_MID_WIDTH_BOOST = Shape.TAPER_MID_WIDTH_BOOST;
        public static final float TAPER_MID_WIDTH_POWER = Shape.TAPER_MID_WIDTH_POWER;
        public static final float ALPHA_POWER = Shape.ALPHA_POWER;
        public static final float ALPHA_MULTIPLIER = Shape.ALPHA_MULTIPLIER;
        public static final boolean BLOOM_ENABLED = Bloom.ENABLED;
        public static final int BLOOM_BLUR_ITERATIONS = Bloom.BLUR_ITERATIONS;
        public static final float BLOOM_MASK_WIDTH = Bloom.MASK_WIDTH;
        public static final float BLOOM_MASK_ALPHA = Bloom.MASK_ALPHA;
        public static final float BLOOM_MASK_NEEDLE_WIDTH = Bloom.MASK_NEEDLE_WIDTH;
        public static final float BLOOM_MASK_HAIRLINE_WIDTH = Bloom.MASK_HAIRLINE_WIDTH;
        public static final float BLOOM_BLUR_RADIUS = Bloom.BLUR_RADIUS;
        public static final float BLOOM_INTENSITY = Bloom.INTENSITY;
        public static final float BLOOM_COMPOSITE_ALPHA = Bloom.COMPOSITE_ALPHA;

        private WorldSlash() {}
    }

    public static final class ScreenBreak {
        public static final class Timeline {
            public static final int DURATION_TICKS = 50; // 屏幕碎裂和尾端 screen-fx 的核心时间轴，单位 tick。
            public static final int SHARD_MAX_LIFETIME_TICKS = 96; // 屏幕玻璃碎片允许保留的最长时间，避免还没淡出就被清理。
            public static final float SHARD_FADE_START_TICKS = 52.0f; // 屏幕玻璃碎片开始整体淡出的时间点。
            public static final float SHARD_FADE_TICKS = 22.0f; // 屏幕玻璃碎片 alpha 淡出持续时间。
            public static final float SHARD_CLEAR_MARGIN_PIXELS = 180.0f; // 碎片离开屏幕多少像素后允许提前清理。

            private Timeline() {}
        }

        public static final class GlassShards {
            public static final int CELLS = 164; // CPU 生成的 Voronoi 玻璃碎片数量。
            public static final float REFRACTION_STRENGTH = 3.50f; // 玻璃碎片 shader 的折射/色散强度。
            public static final float EDGE_VISIBILITY = 0.48f; // 玻璃碎片边缘高光可见度。
            public static final float MIRROR_STRENGTH = 1.50f; // 屏幕玻璃碎片实时屏幕空间反射强度。
            public static final float REVEAL_SPREAD_TICKS = 10.0f; // 屏幕碎片从中心向两侧显现的同步窗口，越小越贴合尾端 screen-fx 峰值。
            public static final float LAUNCH_START_TICKS = 20.0f; // 裂纹分阶段扩展完成后，屏幕碎片开始飞散的基础时间点，单位 tick。
            public static final float LAUNCH_SPREAD_TICKS = 2.5f; // 屏幕碎片破开的同步窗口，单位 tick。
            public static final float GRAVITY = 7.2f; // 碎片爆开后自由落体的重力强度。
            public static final float DEPTH_SPEED = 1.20f; // 碎片向屏幕前后弹出的速度倍率。
            public static final float PERSPECTIVE_FOCAL = 900.0f; // 碎片 3D 透视焦距，越小透视越夸张。
            public static final float TUMBLE_SPEED = 1.0f; // 碎片 3D 翻滚角速度倍率。
            public static final float THICKNESS_PIXELS = 5.0f; // 碎片挤出的屏幕空间厚度，单位近似像素。

            private GlassShards() {}
        }

        public static final class GlassLighting {
            public static final float BLOOM_PIXELS = 2.0f; // 屏幕玻璃碎片尾端泛光边缘宽度，单位近似像素。
            public static final float BLOOM_ALPHA = 0.40f; // 屏幕玻璃碎片尾端泛光强度。
            public static final float MOTION_BLUR_TICKS = 2.0f; // 屏幕碎片移动拖尾回看时间，单位 tick。
            public static final float MOTION_BLUR_ALPHA = 0.12f; // 屏幕碎片移动拖尾强度。
            public static final float CRACK_LIGHT_LENGTH_PIXELS = 5.0f; // 屏幕碎片裂缝透光向外散射长度，单位近似像素。
            public static final float CRACK_LIGHT_WIDTH_PIXELS = 5.0f; // 屏幕碎片裂缝透光散射宽度，单位近似像素。
            public static final float CRACK_LIGHT_ALPHA = 1.0001f; // 屏幕碎片裂缝透光强度。

            private GlassLighting() {}
        }

        public static final class ScreenFx {
            public static final float UOM_DURATION_TICKS = 40.0f; // 尾端 UOM/radial screen-fx 持续时间，单位 tick。
            public static final float UOM_RATE = 20.0f; // 尾端反色、黑白、白闪的闪烁频率。
            public static final float UOM_STRENGTH = 1.24f; // 尾端 screen-fx 总强度。
            public static final float UOM_EDGE_WEIGHT = 0.85f; // 尾端 screen-fx 边缘权重，越大越集中在屏幕边缘。
            public static final float UOM_CONTRAST = 2.55f; // 尾端画面对比度增强强度。
            public static final float UOM_CONTRACTION = 0.115f; // 尾端画面向中心吸附/收缩强度。
            public static final float UOM_RADIAL_BLUR = 0.520f; // 尾端径向拖拽模糊长度，是光束拉伸感的主参数。
            public static final float UOM_BLUR_THRESHOLD = 0.32f; // 参与径向光束的亮度阈值，越低越多像素参与。
            public static final float UOM_BEAM_INTENSITY = 1.70f; // 径向光束 additive 叠加强度。
            public static final float UOM_THRESHOLD_STRENGTH = 1.8f; // Threshold 风格黑白硬化叠加强度。
            public static final float UOM_CENTER_FLASH = 2.15f; // 尾端中心爆白强度。
            public static final float UOM_INVERT_STROBE = 1.80f; // 尾端反色快闪强度。
            public static final float UOM_MONO_STROBE = 1.80f; // 尾端黑白阈值快闪强度。
            public static final float UOM_WHITE_FLASH = 0.56f; // 尾端纯白瞬闪强度。
            public static final float UOM_VIGNETTE_STRENGTH = 0.32f; // 尾端黑晕影强度，白天会额外补偿亮度。

            private ScreenFx() {}
        }

        public static final class RadialChroma {
            public static final float DURATION_TICKS = 45.0f; // 尾端径向 RGB 色散持续时间，单位 tick。
            public static final float STRENGTH = 9.5f; // 尾端径向 RGB 色散强度。
            public static final float PULL = 0.018f; // 尾端径向画面向中心拉扯的 UV 偏移强度。
            public static final float EDGE_START = 0.10f; // 色散从中心多远开始出现。
            public static final float EDGE_END = 0.90f; // 色散到多远达到最大。

            private RadialChroma() {}
        }

        public static final int DURATION_TICKS = Timeline.DURATION_TICKS;
        public static final int SHARD_MAX_LIFETIME_TICKS = Timeline.SHARD_MAX_LIFETIME_TICKS;
        public static final float SHARD_FADE_START_TICKS = Timeline.SHARD_FADE_START_TICKS;
        public static final float SHARD_FADE_TICKS = Timeline.SHARD_FADE_TICKS;
        public static final float SHARD_CLEAR_MARGIN_PIXELS = Timeline.SHARD_CLEAR_MARGIN_PIXELS;
        public static final int SHARD_CELLS = GlassShards.CELLS;
        public static final float REFRACTION_STRENGTH = GlassShards.REFRACTION_STRENGTH;
        public static final float EDGE_VISIBILITY = GlassShards.EDGE_VISIBILITY;
        public static final float SHARD_MIRROR_STRENGTH = GlassShards.MIRROR_STRENGTH;
        public static final float SHARD_REVEAL_SPREAD_TICKS = GlassShards.REVEAL_SPREAD_TICKS;
        public static final float SHARD_LAUNCH_START_TICKS = GlassShards.LAUNCH_START_TICKS;
        public static final float SHARD_LAUNCH_SPREAD_TICKS = GlassShards.LAUNCH_SPREAD_TICKS;
        public static final float SHARD_GRAVITY = GlassShards.GRAVITY;
        public static final float SHARD_DEPTH_SPEED = GlassShards.DEPTH_SPEED;
        public static final float SHARD_PERSPECTIVE_FOCAL = GlassShards.PERSPECTIVE_FOCAL;
        public static final float SHARD_TUMBLE_SPEED = GlassShards.TUMBLE_SPEED;
        public static final float SHARD_THICKNESS_PIXELS = GlassShards.THICKNESS_PIXELS;
        public static final float SHARD_BLOOM_PIXELS = GlassLighting.BLOOM_PIXELS;
        public static final float SHARD_BLOOM_ALPHA = GlassLighting.BLOOM_ALPHA;
        public static final float SHARD_MOTION_BLUR_TICKS = GlassLighting.MOTION_BLUR_TICKS;
        public static final float SHARD_MOTION_BLUR_ALPHA = GlassLighting.MOTION_BLUR_ALPHA;
        public static final float SHARD_CRACK_LIGHT_LENGTH_PIXELS = GlassLighting.CRACK_LIGHT_LENGTH_PIXELS;
        public static final float SHARD_CRACK_LIGHT_WIDTH_PIXELS = GlassLighting.CRACK_LIGHT_WIDTH_PIXELS;
        public static final float SHARD_CRACK_LIGHT_ALPHA = GlassLighting.CRACK_LIGHT_ALPHA;
        public static final float UOM_DURATION_TICKS = ScreenFx.UOM_DURATION_TICKS;
        public static final float UOM_RATE = ScreenFx.UOM_RATE;
        public static final float UOM_STRENGTH = ScreenFx.UOM_STRENGTH;
        public static final float UOM_EDGE_WEIGHT = ScreenFx.UOM_EDGE_WEIGHT;
        public static final float UOM_CONTRAST = ScreenFx.UOM_CONTRAST;
        public static final float UOM_CONTRACTION = ScreenFx.UOM_CONTRACTION;
        public static final float UOM_RADIAL_BLUR = ScreenFx.UOM_RADIAL_BLUR;
        public static final float UOM_BLUR_THRESHOLD = ScreenFx.UOM_BLUR_THRESHOLD;
        public static final float UOM_BEAM_INTENSITY = ScreenFx.UOM_BEAM_INTENSITY;
        public static final float UOM_THRESHOLD_STRENGTH = ScreenFx.UOM_THRESHOLD_STRENGTH;
        public static final float UOM_CENTER_FLASH = ScreenFx.UOM_CENTER_FLASH;
        public static final float UOM_INVERT_STROBE = ScreenFx.UOM_INVERT_STROBE;
        public static final float UOM_MONO_STROBE = ScreenFx.UOM_MONO_STROBE;
        public static final float UOM_WHITE_FLASH = ScreenFx.UOM_WHITE_FLASH;
        public static final float UOM_VIGNETTE_STRENGTH = ScreenFx.UOM_VIGNETTE_STRENGTH;
        public static final float RADIAL_CHROMA_DURATION_TICKS = RadialChroma.DURATION_TICKS;
        public static final float RADIAL_CHROMA_STRENGTH = RadialChroma.STRENGTH;
        public static final float RADIAL_CHROMA_PULL = RadialChroma.PULL;
        public static final float RADIAL_CHROMA_EDGE_START = RadialChroma.EDGE_START;
        public static final float RADIAL_CHROMA_EDGE_END = RadialChroma.EDGE_END;

        private ScreenBreak() {}
    }

    private DimensionalSlashTuning() {}
}
