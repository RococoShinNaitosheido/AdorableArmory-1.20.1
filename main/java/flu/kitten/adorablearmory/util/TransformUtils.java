package flu.kitten.adorablearmory.util;

import com.google.common.collect.ImmutableMap;
import com.mojang.math.Transformation;
import flu.kitten.adorablearmory.api.client.model.PerspectiveModelState;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

public final class TransformUtils {
    public static final PerspectiveModelState IDENTITY = PerspectiveModelState.IDENTITY;
    public static final PerspectiveModelState DEFAULT_TOOL;
    public static final PerspectiveModelState DEFAULT_ITEM;
    public static final PerspectiveModelState DEFAULT_BLOCK;
    public static final PerspectiveModelState DEFAULT_BLOCK_ITEM;
    public static final double toad = 0.017453292519943;

    static {
        Map<ItemDisplayContext, Transformation> map = new HashMap<>();
        map.put(ItemDisplayContext.GROUND, create(0F, 2F, 0F, 0F, 0F, 0F, 0.5F));
        map.put(ItemDisplayContext.FIXED, create(0F, 0F, 0F, 0F, 180F, 0F, 1F));
        map.put(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, create(0F, 4F, 0.5F, 0F, -90F, 55, 0.85F));
        map.put(ItemDisplayContext.THIRD_PERSON_LEFT_HAND, create(0F, 4F, 0.5F, 0F, 90F, -55, 0.85F));
        map.put(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, create(1.13F, 3.2F, 1.13F, 0F, -90F, 25, 0.68F));
        map.put(ItemDisplayContext.FIRST_PERSON_LEFT_HAND, create(1.13F, 3.2F, 1.13F, 0F, 90F, -25, 0.68F));
        DEFAULT_TOOL = new PerspectiveModelState(ImmutableMap.copyOf(map));
    }

    static {
        Map<ItemDisplayContext, Transformation> map = new HashMap<>();
        map.put(ItemDisplayContext.GROUND, create(0F, 2F, 0F, 0F, 0F, 0F, 0.5F));
        map.put(ItemDisplayContext.FIXED, create(0F, 0F, 0F, 0F, 180F, 0F, 1F));
        map.put(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, create(0F, 3F, 1F, 0F, 0F, 0F, 0.55F));
        map.put(ItemDisplayContext.THIRD_PERSON_LEFT_HAND, create(0F, 3F, 1F, 0F, 0F, 0F, 0.55F));
        map.put(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, create(1.13F, 3.2F, 1.13F, 0F, -90F, 25F, 0.72F));
        map.put(ItemDisplayContext.FIRST_PERSON_LEFT_HAND, create(1.13F, 3.2F, 1.13F, 0F, -90F, 25F, 0.72F));
        DEFAULT_ITEM = new PerspectiveModelState(ImmutableMap.copyOf(map));
    }

    static {
        Map<ItemDisplayContext, Transformation> map = new HashMap<>();
        map.put(ItemDisplayContext.GROUND, create(0F, 0F, 0F, 0F, 0F, 0F, 1F));
        map.put(ItemDisplayContext.FIXED, create(0F, 0F, 0F, 0F, 0F, 0F, 1F));
        map.put(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, create(0F, 2.5F, 0F, 75F, 45F, 0F, 0.375F));
        map.put(ItemDisplayContext.THIRD_PERSON_LEFT_HAND, create(0F, 2.5F, 0F, 75F, 45F, 0F, 0.375F));
        map.put(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, create(0F, 0F, 0F, 0F, 45F, 0F, 0.4F));
        map.put(ItemDisplayContext.FIRST_PERSON_LEFT_HAND, create(0F, 0F, 0F, 0F, 225F, 0F, 0.4F));
        map.put(ItemDisplayContext.HEAD, create(0F, 0F, 0F, 0F, 0F, 0F, 1F));
        DEFAULT_BLOCK = new PerspectiveModelState(ImmutableMap.copyOf(map));
    }

    static {
        Map<ItemDisplayContext, Transformation> map = new HashMap<>();
        map.put(ItemDisplayContext.GROUND, create(0F, 3F, 0F, 0F, 0F, 0F, 0.25F));
        map.put(ItemDisplayContext.FIXED, create(0F, 0F, 0F, 0F, 0F, 0F, 0.5F));
        map.put(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, create(0F, 2.5F, 0F, 75F, 45F, 0F, 0.375F));
        map.put(ItemDisplayContext.THIRD_PERSON_LEFT_HAND, create(0F, 2.5F, 0F, 75F, 45F, 0F, 0.375F));
        map.put(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, create(0F, 0F, 0F, 0F, 45F, 0F, 0.4F));
        map.put(ItemDisplayContext.FIRST_PERSON_LEFT_HAND, create(0F, 0F, 0F, 0F, 225F, 0F, 0.4F));
        map.put(ItemDisplayContext.GUI, create(0F, 0F, 0F, 30F, 225F, 0F, 0.625F));
        map.put(ItemDisplayContext.HEAD, create(0F, 0F, 0F, 0F, 0F, 0F, 1F));
        DEFAULT_BLOCK_ITEM = new PerspectiveModelState(ImmutableMap.copyOf(map));
    }

    public static Transformation create(float tx, float ty, float tz, float rx, float ry, float rz, float s) {
        return create(new Vector3f(tx / 16, ty / 16, tz / 16), new Vector3f(rx, ry, rz), new Vector3f(s, s, s));
    }

    public static Transformation create(Vector3f transform, Vector3f rotation, Vector3f scale) {
        return new Transformation(transform, new Quaternionf().rotationXYZ((float) (rotation.x() * toad), (float) (rotation.y() * toad), (float) (rotation.z() * toad)), scale, null);
    }

    public static Transformation create(ItemTransform transform) {
        if (ItemTransform.NO_TRANSFORM.equals(transform)) return Transformation.identity();
        return create(transform.translation, transform.rotation, transform.scale);
    }

    public static ModelState stateFromItemTransforms(ItemTransforms itemTransforms) {
        if (itemTransforms == ItemTransforms.NO_TRANSFORMS) return IDENTITY;
        ImmutableMap.Builder<ItemDisplayContext, Transformation> map = ImmutableMap.builder();
        for (ItemDisplayContext value : ItemDisplayContext.values()) map.put(value, create(itemTransforms.getTransform(value)));
        return new PerspectiveModelState(map.build());
    }

    public static PerspectiveModelState getAppropriateModelState(ItemDisplayContext context, ModelType modelType) {
        if (modelType == ModelType.BLOCK) {
            return DEFAULT_BLOCK;
        } else if (modelType == ModelType.BLOCK_ITEM) {
            return DEFAULT_BLOCK_ITEM;
        } else if (modelType == ModelType.TOOL) {
            return DEFAULT_TOOL;
        }
        return DEFAULT_ITEM;
    }

    public enum ModelType {
        ITEM,
        TOOL,
        BLOCK,
        BLOCK_ITEM
    }
}