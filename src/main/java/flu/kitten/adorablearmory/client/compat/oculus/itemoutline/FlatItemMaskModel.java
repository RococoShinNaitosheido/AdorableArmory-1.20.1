package flu.kitten.adorablearmory.client.compat.oculus.itemoutline;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.BakedModelWrapper;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.*;

public final class FlatItemMaskModel extends BakedModelWrapper<BakedModel> {
    private static final List<BakedQuad> NO_QUADS = Collections.emptyList();
    private final Map<List<BakedQuad>, List<BakedQuad>> filteredQuadCache = Collections.synchronizedMap(new WeakHashMap<>());

    public FlatItemMaskModel(BakedModel originalModel) {
        super(originalModel);
    }

    @Override
    public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @NotNull RandomSource rand) {
        if (side != null && side != Direction.NORTH && side != Direction.SOUTH) {
            return NO_QUADS;
        }

        List<BakedQuad> original = this.originalModel.getQuads(state, side, rand);
        if (original.isEmpty()) {
            return original;
        }

        return filteredQuadCache.computeIfAbsent(original, FlatItemMaskModel::filterNorthSouthQuads);
    }

    private static @NotNull List<BakedQuad> filterNorthSouthQuads(List<BakedQuad> original) {
        int size = original.size();
        int kept = 0;

        for (BakedQuad bakedQuad : original) {
            Direction direction = bakedQuad.getDirection();
            if (direction == Direction.NORTH || direction == Direction.SOUTH) {
                kept++;
            }
        }

        if (kept == 0) {
            return NO_QUADS;
        }

        if (kept == size) {
            return original;
        }

        List<BakedQuad> filtered = new ArrayList<>(kept);
        for (BakedQuad quad : original) {
            Direction direction = quad.getDirection();
            if (direction == Direction.NORTH || direction == Direction.SOUTH) {
                filtered.add(quad);
            }
        }

        return Collections.unmodifiableList(filtered);
    }
}
