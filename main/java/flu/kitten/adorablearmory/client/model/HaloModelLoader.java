package flu.kitten.adorablearmory.client.model;

import com.google.gson.*;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import flu.kitten.adorablearmory.api.client.model.*;
import flu.kitten.adorablearmory.util.TransformUtils;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.*;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraftforge.client.RenderTypeGroup;
import net.minecraftforge.client.model.geometry.IGeometryBakingContext;
import net.minecraftforge.client.model.geometry.IGeometryLoader;
import net.minecraftforge.client.model.geometry.IUnbakedGeometry;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

@SuppressWarnings("unused")
public class HaloModelLoader implements IGeometryLoader<HaloModelLoader.HaloItemModelGeometry> {

    public static final HaloModelLoader INSTANCE = new HaloModelLoader();

    @Override
    public HaloItemModelGeometry read(JsonObject modelContents, JsonDeserializationContext deserializationContext) throws JsonParseException {
        final JsonObject halo = modelContents.getAsJsonObject("halo");
        if (halo == null) {
            throw new IllegalStateException("Missing 'halo' object.");
        }
        final IntArrayList layerColors = new IntArrayList();
        final JsonArray layerColorsArr = modelContents.getAsJsonArray("layerColors");
        if (layerColorsArr != null) {
            for (final JsonElement jsonElement : layerColorsArr) {
                layerColors.add(jsonElement.getAsInt());
            }
        }
        final String texture = GsonHelper.getAsString(halo, "texture");
        final int color = GsonHelper.getAsInt(halo, "color");
        final int size = GsonHelper.getAsInt(halo, "size");
        final boolean pulse = GsonHelper.getAsBoolean(halo, "pulse");

        final boolean pulseInWorld = GsonHelper.getAsBoolean(halo, "pulseInWorld", true); // Default to true for backward compatibility
        final ModelType type = detectModelType(modelContents, halo);

        final JsonObject clean = modelContents.getAsJsonObject();
        clean.remove("halo");
        clean.remove("loader");
        final BlockModel baseModel = deserializationContext.deserialize(clean, BlockModel.class);
        return new HaloItemModelGeometry(baseModel, layerColors, texture, color, size, pulse, pulseInWorld, type);
    }

    private ModelType detectModelType(JsonObject modelContents, JsonObject halo) {
        if (halo.has("type")) {
            String type = GsonHelper.getAsString(halo, "type").toLowerCase();
            return switch (type) {
                case "block" -> ModelType.BLOCK;
                case "block_item" -> ModelType.BLOCK_ITEM;
                case "tool" -> ModelType.TOOL;
                default -> ModelType.ITEM;
            };
        }

        if (modelContents.has("parent")) {
            String parent = GsonHelper.getAsString(modelContents, "parent").toLowerCase();
            if (parent.contains("block/") || parent.contains("blocks/")) {
                return ModelType.BLOCK_ITEM;
            }
        }
        return ModelType.ITEM;
    }

    public static class HaloItemModelGeometry implements IUnbakedGeometry<HaloItemModelGeometry> {
        private static final ConcurrentMap<Pair<VertexFormat, VertexFormat>, int[]> formatMaps = new ConcurrentHashMap<>();
        private static final int[] DEFAULT_MAPPING = generateMapping(DefaultVertexFormat.BLOCK, DefaultVertexFormat.BLOCK);
        private final BlockModel baseModel;
        private final IntList layerColors;
        private final String texture;
        private final int color;
        private final int size;
        private final boolean pulse;
        private final boolean pulseInWorld;
        private final ModelType type;

        public HaloItemModelGeometry(final BlockModel baseModel, final IntList layerColors, final String texture, final int color, final int size, final boolean pulse, final boolean pulseInWorld, final ModelType type) {
            this.baseModel = baseModel;
            this.layerColors = layerColors;
            this.texture = texture;
            this.color = color;
            this.size = size;
            this.pulse = pulse;
            this.type = type;
            this.pulseInWorld = pulseInWorld;
        }

        private static BakedModel tintLayers(final BakedModel model, final IntList layerColors) {
            if (layerColors.isEmpty()) {
                return model;
            }
            final Map<Direction, List<BakedQuad>> faceQuads = new HashMap<>();
            for (final Direction face : Direction.values()) {
                faceQuads.put(face, transformQuads(model.getQuads(null, face, RandomSource.create()), layerColors));
            }
            final List<BakedQuad> uncalled = transformQuads(model.getQuads(null, null, RandomSource.create()), layerColors);
            return new SimpleBakedModel(uncalled, faceQuads, model.useAmbientOcclusion(), model.usesBlockLight(), model.isGui3d(), model.getParticleIcon(), model.getTransforms(), ItemOverrides.EMPTY, RenderTypeGroup.EMPTY);
        }

        static List<BakedQuad> transformQuads(final List<BakedQuad> quads, final IntList layerColors) {
            final ArrayList<BakedQuad> newQuads = new ArrayList<>(quads.size());
            for (final BakedQuad quad : quads) {
                newQuads.add(transformQuad(quad, layerColors));
            }
            return newQuads;
        }

        public static int[] mapFormats(final VertexFormat from, final VertexFormat to) {
            if (from.equals(DefaultVertexFormat.BLOCK) && to.equals(DefaultVertexFormat.BLOCK)) {
                return HaloItemModelGeometry.DEFAULT_MAPPING;
            }
            return HaloItemModelGeometry.formatMaps.computeIfAbsent(Pair.of(from, to), pair -> generateMapping(pair.getLeft(), pair.getRight()));
        }

        protected static void unpack(final int[] from, final float[] to, final VertexFormat formatFrom, final int v, final int e) {
            final int length = Math.min(4, to.length);
            final VertexFormatElement element = formatFrom.getElements().get(e);
            final int vertexStart = v * formatFrom.getVertexSize() + formatFrom.getOffset(e);
            final int count = element.getElementCount();
            final VertexFormatElement.Type type = element.getType();
            final VertexFormatElement.Usage usage = element.getUsage();
            final int size = type.getSize();
            final int mask = (256 << 8 * (size - 1)) - 1;
            for (int i = 0; i < length; ++i) {
                if (i < count) {
                    final int pos = vertexStart + size * i;
                    final int index = pos >> 2;
                    final int offset = pos & 0x3;
                    int bits = from[index];
                    bits >>>= offset * 8;
                    if ((pos + size - 1) / 4 != index) {
                        bits |= from[index + 1] << (4 - offset) * 8;
                    }
                    bits &= mask;
                    if (type == VertexFormatElement.Type.FLOAT) {
                        to[i] = Float.intBitsToFloat(bits);
                    } else if (type == VertexFormatElement.Type.UBYTE || type == VertexFormatElement.Type.USHORT) {
                        to[i] = bits / (float) mask;
                    } else if (type == VertexFormatElement.Type.UINT) {
                        to[i] = (float) (((long) bits & 0xFFFFFFFFL) / 4.294967295E9);
                    } else if (type == VertexFormatElement.Type.BYTE) {
                        to[i] = (byte) bits / (float) (mask >> 1);
                    } else if (type == VertexFormatElement.Type.SHORT) {
                        to[i] = (short) bits / (float) (mask >> 1);
                    } else if (type == VertexFormatElement.Type.INT) {
                        to[i] = (float) (((long) bits & 0xFFFFFFFFL) / 2.147483647E9);
                    }
                } else {
                    to[i] = ((i == 3 && usage == VertexFormatElement.Usage.POSITION) ? 1.0f : 0.0f);
                }
            }
        }

        private static int[] generateMapping(final VertexFormat from, final VertexFormat to) {
            final int fromCount = from.getElements().size();
            final int toCount = to.getElements().size();
            final int[] eMap = new int[fromCount];
            for (int e = 0; e < fromCount; ++e) {
                final VertexFormatElement expected = from.getElements().get(e);
                int e2;
                for (e2 = 0; e2 < toCount; ++e2) {
                    final VertexFormatElement current = to.getElements().get(e2);
                    if (expected.getUsage() == current.getUsage() && expected.getIndex() == current.getIndex()) {
                        break;
                    }
                }
                eMap[e] = e2;
            }
            return eMap;
        }

        public static void putBakedQuad(final IVertexConsumer consumer, final BakedQuad quad) {
            consumer.setTexture(quad.getSprite());
            consumer.setQuadOrientation(quad.getDirection());
            if (quad.isTinted()) {
                consumer.setQuadTint(quad.getTintIndex());
            }
            consumer.setApplyDiffuseLighting(quad.isShade());
            final float[] data = new float[4];
            final VertexFormat formatFrom = consumer.getVertexFormat();
            final VertexFormat formatTo = DefaultVertexFormat.BLOCK;
            final int countFrom = formatFrom.getElements().size();
            final int countTo = formatTo.getElements().size();
            final int[] eMap = mapFormats(formatFrom, formatTo);
            for (int v = 0; v < 4; ++v) {
                for (int e = 0; e < countFrom; ++e) {
                    if (eMap[e] != countTo) {
                        unpack(quad.getVertices(), data, formatTo, v, eMap[e]);
                        consumer.put(e, data);
                    } else {
                        consumer.put(e);
                    }
                }
            }
        }

        static BakedQuad transformQuad(final BakedQuad quad, final IntList layerColors) {
            final int tintIndex = quad.getTintIndex();
            if (tintIndex == -1 || tintIndex >= layerColors.size()) {
                return quad;
            }
            final int tint = layerColors.getInt(tintIndex);
            if (tint == -1) {
                return quad;
            }
            final Quad newQuad = new Quad();
            newQuad.reset(CachedFormat.BLOCK);
            putBakedQuad(newQuad, quad);
            final float r = (tint >> 16 & 255) / 255.0f;
            final float g = (tint >> 8 & 255) / 255.0f;
            final float b = (tint & 255) / 255.0f;
            for (final Quad.Vertex v : newQuad.vertices) {
                v.color[0] *= r;
                v.color[1] *= g;
                v.color[2] *= b;
            }
            newQuad.tintIndex = -1;
            return newQuad.bake();
        }

        @Override
        public BakedModel bake(final IGeometryBakingContext owner, final ModelBaker bakery, final Function<Material, TextureAtlasSprite> spriteGetter, final ModelState modelTransform, final ItemOverrides overrides, final ResourceLocation modelLocation) {
            final BakedModel bakedBaseModel = this.baseModel.bake(bakery, this.baseModel, spriteGetter, modelTransform, modelLocation, false);
            Material particleLocation;
            try {
                particleLocation = this.baseModel.getMaterial(this.texture);
            } catch (Exception e) {
                String texturePath = this.texture;
                if (texturePath.startsWith("#")) {
                    texturePath = (this.baseModel.textureMap.get(texturePath.substring(1))).toString();
                    if (texturePath == null) {
                        throw new RuntimeException("Failed to resolve texture reference: " + this.texture);
                    }
                }
                particleLocation = new Material(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS, new ResourceLocation(texturePath));
            }

            TextureAtlasSprite particle = spriteGetter.apply(particleLocation);
            return createHaloModel(bakedBaseModel, particle, modelLocation);
        }

        @SuppressWarnings("unused")
        private BakedModel createHaloModel(BakedModel bakedBaseModel, TextureAtlasSprite particle, ResourceLocation modelLocation) {
            BakedModel tintedModel = tintLayers(bakedBaseModel, layerColors);
            if (this.type == ModelType.BLOCK) {
                return new blockHaloBakedModel(tintedModel, particle, this.color, this.size, this.pulse, this.pulseInWorld);
            } else if (this.type == ModelType.BLOCK_ITEM) {
                return new BlockItemHaloBakedModel(tintedModel, particle, this.color, this.size, this.pulse, this.pulseInWorld);
            } else if (this.type == ModelType.TOOL) {
                return new ToolHaloBakedModel(tintedModel, particle, this.color, this.size, this.pulse, this.pulseInWorld);
            }
            return new HaloBakedModel(tintedModel, particle, this.color, this.size, this.pulse, this.pulseInWorld);
        }

        @Override
        public void resolveParents(Function<ResourceLocation, UnbakedModel> modelGetter, IGeometryBakingContext context) {
            this.baseModel.resolveParents(modelGetter);
        }
    }

    public static class BlockHaloBakedModel extends HaloBakedModel {
        public BlockHaloBakedModel(BakedModel wrapped, TextureAtlasSprite sprite, int color, int size, boolean pulse) {
            super(wrapped, sprite, color, size, pulse);
        }
        @Override
        public PerspectiveModelState getModelState() {
            return TransformUtils.DEFAULT_BLOCK;
        }
    }

    public static class blockHaloBakedModel extends HaloBakedModel {
        public blockHaloBakedModel(BakedModel wrapped, TextureAtlasSprite sprite, int color, int size, boolean pulse, boolean pulseInWorld) {
            super(wrapped, sprite, color, size, pulse, pulseInWorld);
        }
        @Override
        public PerspectiveModelState getModelState() {
            return TransformUtils.DEFAULT_BLOCK;
        }
    }

    public static class BlockItemHaloBakedModel extends HaloBakedModel {
        public BlockItemHaloBakedModel(BakedModel wrapped, TextureAtlasSprite sprite, int color, int size, boolean pulse, boolean pulseInWorld) {
            super(wrapped, sprite, color, size, pulse, pulseInWorld);
        }

        @Override
        public PerspectiveModelState getModelState() {
            return TransformUtils.DEFAULT_BLOCK_ITEM;
        }
    }

    public static class ToolHaloBakedModel extends HaloBakedModel {
        public ToolHaloBakedModel(BakedModel wrapped, TextureAtlasSprite sprite, int color, int size, boolean pulse, boolean pulseInWorld) {
            super(wrapped, sprite, color, size, pulse, pulseInWorld);
        }

        @Override
        public PerspectiveModelState getModelState() {
            return TransformUtils.DEFAULT_TOOL;
        }
    }

    private enum ModelType {
        ITEM,
        TOOL,
        BLOCK,
        BLOCK_ITEM
    }
}
