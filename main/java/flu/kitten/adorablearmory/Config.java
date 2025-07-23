package flu.kitten.adorablearmory;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class Config {

    private static final ForgeConfigSpec.Builder B = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.BooleanValue LOG_DIRT_BLOCK = B
            .comment("Whether to log the dirt block on common setup")
            .define("logDirtBlock", true);

    private static final ForgeConfigSpec.IntValue MAGIC_NUMBER = B
            .comment("A magic number")
            .defineInRange("magicNumber", 42, 0, Integer.MAX_VALUE);

    private static final ForgeConfigSpec.ConfigValue<String> MAGIC_INTRO = B
            .comment("Introduction for the magic number")
            .define("magicNumberIntroduction", "The magic number is... ");

    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> ITEM_STRINGS = B
            .comment("Items that will get extra logging / effects")
            .defineListAllowEmpty("items", List.of("minecraft:iron_ingot"), Config::isValidItemId);

    public static final ForgeConfigSpec SPEC = B.build();
    public static boolean logDirtBlock = true;
    public static int magicNumber = 42;
    public static String magicIntro = "The magic number is... ";
    public static Set<Item> items = Set.of();

    @SubscribeEvent
    public static void configLoad(final ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) return;
        logDirtBlock = LOG_DIRT_BLOCK.get();
        magicNumber = MAGIC_NUMBER.get();
        magicIntro = MAGIC_INTRO.get();
        items = ITEM_STRINGS.get().stream().map(id -> ForgeRegistries.ITEMS.getValue(new ResourceLocation(id))).collect(Collectors.toSet());
    }

    private static boolean isValidItemId(Object o) {
        if (!(o instanceof String id)) return false;
        return ForgeRegistries.ITEMS.containsKey(new ResourceLocation(id));
    }

    private Config() {}
}
