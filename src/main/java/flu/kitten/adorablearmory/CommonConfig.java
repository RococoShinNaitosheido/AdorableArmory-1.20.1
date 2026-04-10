package flu.kitten.adorablearmory;

import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.Set;

public class CommonConfig {
    public static ForgeConfigSpec SPEC;
    public static boolean logDirtBlock = true;
    public static int magicNumber = 42;
    public static String magicIntro = "The magic number is... ";
    public static Set<Item> items = Set.of();
    public static final ForgeConfigSpec.BooleanValue TRUE_DEMON_HITS_CREATIVE; // 是否允许创造玩家受到TRUE_DEMON_TYPE

    static {
        ForgeConfigSpec.Builder build = new ForgeConfigSpec.Builder();
        TRUE_DEMON_HITS_CREATIVE = build.comment("If true, players in creative mode can still take true-demon damage.", "If false (default), true-demon is stopped for creative players.").define("true_demon_hits_creative", false);
        SPEC = build.build();
    }

    private CommonConfig() {}
}
