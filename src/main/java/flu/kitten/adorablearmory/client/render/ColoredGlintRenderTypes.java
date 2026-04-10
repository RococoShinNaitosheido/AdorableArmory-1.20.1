package flu.kitten.adorablearmory.client.render;

import flu.kitten.adorablearmory.mixin.RenderTypeInvoker;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.renderer.RenderType;

public final class ColoredGlintRenderTypes {
    private ColoredGlintRenderTypes() {}

    private static final Int2ObjectMap<RenderType> GLINT = new Int2ObjectOpenHashMap<>();
    private static final Int2ObjectMap<RenderType> GLINT_DIRECT = new Int2ObjectOpenHashMap<>();
    private static final Int2ObjectMap<RenderType> ENTITY_GLINT = new Int2ObjectOpenHashMap<>();
    private static final Int2ObjectMap<RenderType> ENTITY_GLINT_DIRECT = new Int2ObjectOpenHashMap<>();
    private static final Int2ObjectMap<RenderType> GLINT_TRANSLUCENT = new Int2ObjectOpenHashMap<>();

    public static RenderType replaceIfGlint(RenderType type, int argb) {
        if (type == RenderType.glint()) {
            return glint(argb);
        }
        if (type == RenderType.glintDirect()) {
            return glintDirect(argb);
        }
        if (type == RenderType.entityGlint()) {
            return entityGlint(argb);
        }
        if (type == RenderType.entityGlintDirect()) {
            return entityGlintDirect(argb);
        }
        if (type == RenderType.glintTranslucent()) {
            return glintTranslucent(argb);
        }
        return type;
    }

    public static RenderType glint(int argb) {
        return tinted(RenderType.glint(), GLINT, argb, "tinted_glint");
    }

    public static RenderType glintDirect(int argb) {
        return tinted(RenderType.glintDirect(), GLINT_DIRECT, argb, "tinted_glint_direct");
    }

    public static RenderType entityGlint(int argb) {
        return tinted(RenderType.entityGlint(), ENTITY_GLINT, argb, "tinted_entity_glint");
    }

    public static RenderType entityGlintDirect(int argb) {
        return tinted(RenderType.entityGlintDirect(), ENTITY_GLINT_DIRECT, argb, "tinted_entity_glint_direct");
    }

    public static RenderType glintTranslucent(int argb) {
        return tinted(RenderType.glintTranslucent(), GLINT_TRANSLUCENT, argb, "tinted_glint_translucent");
    }

    private static RenderType tinted(RenderType base, Int2ObjectMap<RenderType> cache, int argb, String namePrefix) {
        return cache.computeIfAbsent(argb, c -> createTinted(base, c, namePrefix));
    }

    private static RenderType createTinted(RenderType type, int argb, String namePrefix) {
        boolean sortOnUpload = ((RenderTypeInvoker) type).sortOnUpload();
        return TrueDemonGlintRenderType.wrap(type, argb, namePrefix, sortOnUpload);
    }
}
