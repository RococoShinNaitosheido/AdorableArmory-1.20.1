package flu.kitten.adorablearmory.client.render;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.renderer.RenderType;

public final class OculusSafeColoredGlintOverlayTypes {
    private static final Int2ObjectMap<RenderType> CACHE = new Int2ObjectOpenHashMap<>();

    public static RenderType coloredItemGlint(int argb) {
        int normalized = withOpaqueAlpha(argb);

        synchronized (CACHE) {
            RenderType cached = CACHE.get(normalized);
            if (cached != null) {
                return cached;
            }

            RenderType created = TrueDemonGlintRenderType.wrap(TrueDemonGlintRenderTypes.itemGlintDirect(), normalized, "oculus_safe_overlay_glint", false);
            CACHE.put(normalized, created);
            return created;
        }
    }

    private static int withOpaqueAlpha(int argb) {
        return (argb & 0xFF000000) == 0 ? (argb | 0xFF000000) : argb;
    }

    private OculusSafeColoredGlintOverlayTypes() {}
}
