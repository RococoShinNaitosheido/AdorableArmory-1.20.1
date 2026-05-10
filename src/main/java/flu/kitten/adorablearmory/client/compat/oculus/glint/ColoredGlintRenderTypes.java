package flu.kitten.adorablearmory.client.compat.oculus.glint;

import flu.kitten.adorablearmory.mixin.RenderTypeInvoker;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemDisplayContext;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;

public final class ColoredGlintRenderTypes {
    private static final Int2ObjectMap<RenderType> GLINT = new Int2ObjectOpenHashMap<>();
    private static final Int2ObjectMap<RenderType> GLINT_DIRECT = new Int2ObjectOpenHashMap<>();
    private static final Int2ObjectMap<RenderType> ENTITY_GLINT = new Int2ObjectOpenHashMap<>();
    private static final Int2ObjectMap<RenderType> ENTITY_GLINT_DIRECT = new Int2ObjectOpenHashMap<>();
    private static final Int2ObjectMap<RenderType> GLINT_TRANSLUCENT = new Int2ObjectOpenHashMap<>();
    private static final Map<RenderType, Int2ObjectMap<RenderType>> EXACT_GLINT_LIKE_CACHE = Collections.synchronizedMap(new IdentityHashMap<>());

    public static boolean isGlintLike(RenderType type) {
        if (type == RenderType.glint()) {
            return true;
        }
        if (type == RenderType.glintDirect()) {
            return true;
        }
        if (type == RenderType.entityGlint()) {
            return true;
        }
        if (type == RenderType.entityGlintDirect()) {
            return true;
        }
        if (type == RenderType.glintTranslucent()) {
            return true;
        }

        String typeName = String.valueOf(type).toLowerCase(Locale.ROOT);
        return typeName.contains("glint");
    }

    public static RenderType replaceIfGlint(RenderType type, int argb) {
        if (!isGlintLike(type)) {
            return type;
        }

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

        return wrapExactGlintLike(type, argb);
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

    public static RenderType manualImmediateGlintForContext(ItemDisplayContext context, int argb) {
        return switch (context) {
            case GROUND, FIXED, THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND, HEAD -> entityGlint(argb);
            default -> glintDirect(argb);
        };
    }

    private static RenderType tinted(RenderType base, Int2ObjectMap<RenderType> cache, int argb, String namePrefix) {
        RenderType cached = cache.get(argb);
        if (cached != null) {
            return cached;
        }

        boolean sortOnUpload = ((RenderTypeInvoker) base).sortOnUpload();
        RenderType wrapped = TrueDemonGlintRenderType.wrap(base, argb, namePrefix, sortOnUpload);
        cache.put(argb, wrapped);
        return wrapped;
    }

    private static RenderType wrapExactGlintLike(RenderType base, int argb) {
        Int2ObjectMap<RenderType> perTypeCache = EXACT_GLINT_LIKE_CACHE.computeIfAbsent(base, ignored -> new Int2ObjectOpenHashMap<>());

        RenderType cached = perTypeCache.get(argb);
        if (cached != null) {
            return cached;
        }

        synchronized (perTypeCache) {
            cached = perTypeCache.get(argb);
            if (cached != null) {
                return cached;
            }

            boolean sortOnUpload = ((RenderTypeInvoker) base).sortOnUpload();
            String namePrefix = buildExactTypePrefix(base);
            RenderType wrapped = TrueDemonGlintRenderType.wrap(base, argb, namePrefix, sortOnUpload);
            perTypeCache.put(argb, wrapped);
            return wrapped;
        }
    }

    private static String buildExactTypePrefix(RenderType base) {
        String typeName = String.valueOf(base).toLowerCase(Locale.ROOT);

        String family;
        if (typeName.contains("entity") && typeName.contains("direct")) {
            family = "tinted_exact_entity_glint_direct";
        } else if (typeName.contains("entity")) {
            family = "tinted_exact_entity_glint";
        } else if (typeName.contains("translucent")) {
            family = "tinted_exact_glint_translucent";
        } else if (typeName.contains("direct")) {
            family = "tinted_exact_glint_direct";
        } else {
            family = "tinted_exact_glint";
        }

        return family + "_" + Integer.toHexString(System.identityHashCode(base));
    }

    private ColoredGlintRenderTypes() {}
}
