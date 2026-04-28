package flu.kitten.adorablearmory.client.render;

import flu.kitten.adorablearmory.AdorableArmory;
import flu.kitten.adorablearmory.api.duck.IGlintColorProvider;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

public final class ItemShaderModCompat {
    private static boolean loggedCompatMode;
    private static final boolean OCULUS_LOADED = ModList.get().isLoaded("oculus");
    private static final boolean EMBEDDIUM_LOADED = ModList.get().isLoaded("embeddium");

    public static boolean isOculusEmbeddiumActive() {
        return OCULUS_LOADED && EMBEDDIUM_LOADED;
    }

    public static boolean shouldDeferFirstPersonOutlineComposite(ItemDisplayContext context) {
        return isOculusEmbeddiumActive() && (context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND || context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND);
    }

    public static boolean shouldForceDirectColoredGlintCompat(ItemDisplayContext context) {
        return false;
    }

    public static boolean shouldUseSafeColoredGlintOverlay(ItemDisplayContext context) {
        if (!isOculusEmbeddiumActive()) {
            return false;
        }

        return switch (context) {
            case GROUND, FIXED, THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND, HEAD -> true;
            default -> false;
        };
    }

    public static int resolveGlintColor(ItemStack stack, ItemDisplayContext context) {
        if (stack.isEmpty() || !stack.hasFoil()) {
            return -1;
        }

        if (!(stack.getItem() instanceof IGlintColorProvider provider)) {
            return -1;
        }

        return provider.getGlintColor(stack);
    }

    public static void logCompatModeOnce() {
        if (!isOculusEmbeddiumActive() || loggedCompatMode) {
            return;
        }

        loggedCompatMode = true;
        AdorableArmory.LOGGER.warn("[ItemRender] Oculus + Embeddium detected. Enabling SAFE colored glint overlay in world/fixed/third-person contexts and disabling direct glint RenderType replacement in those contexts.");
    }

    private ItemShaderModCompat() {}
}
