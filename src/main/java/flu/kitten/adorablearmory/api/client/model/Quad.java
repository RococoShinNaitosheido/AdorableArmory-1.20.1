package flu.kitten.adorablearmory.api.client.model;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import flu.kitten.adorablearmory.util.*;
import net.minecraft.core.Direction;

public class Quad implements IVertexProducer, IVertexConsumer {
    private final Vector3 v1 = new Vector3();
    private final Vector3 v2 = new Vector3();
    private final Vector3 t = new Vector3();
    public CachedFormat format;
    public int tintIndex = -1;
    public Direction orientation;
    public boolean diffuseLighting = true;
    public TextureAtlasSprite sprite;
    public Vertex[] vertices = new Vertex[4];
    public boolean full;
    private int vertexIndex = 0;

    public Quad() {
    }

    @Override
    public VertexFormat getVertexFormat() {
        return format.format;
    }

    @Override
    public void setQuadTint(int tint) {
        tintIndex = tint;
    }

    @Override
    public void setQuadOrientation(Direction orientation) {
        this.orientation = orientation;
    }

    @Override
    public void setApplyDiffuseLighting(boolean diffuse) {
        diffuseLighting = diffuse;
    }

    @Override
    public void setTexture(TextureAtlasSprite texture) {
        sprite = texture;
    }

    @Override
    public void put(int element, float... data) {
        if (full) {
            throw new RuntimeException("Unable to add data when full.");
        }
        Vertex v = vertices[vertexIndex];
        if (v == null) {
            v = new Vertex(format);
            vertices[vertexIndex] = v;
        }
        System.arraycopy(data, 0, v.raw[element], 0, data.length);
        if (element == (format.elementCount - 1)) {
            vertexIndex++;
            if (vertexIndex == 4) {
                vertexIndex = 0;
                full = true;
                if (orientation == null) {
                    calculateOrientation(false);
                }
            }
        }
    }

    @Override
    public void put(Quad quad) {
        copyFrom(quad);
    }

    @Override
    public void pipe(IVertexConsumer consumer) {
        if (consumer != null) {
            consumer.put(this);
        } else {
            consumer.setQuadTint(tintIndex);
            consumer.setQuadOrientation(orientation);
            consumer.setApplyDiffuseLighting(diffuseLighting);
            consumer.setTexture(sprite);
            for (Vertex v : vertices) {
                for (int e = 0; e < format.elementCount; e++) {
                    consumer.put(e, v.raw[e]);
                }
            }
        }
    }

    public void calculateOrientation(boolean setNormal) {
        v1.set(vertices[3].vec).subtract(t.set(vertices[1].vec));
        v2.set(vertices[2].vec).subtract(t.set(vertices[0].vec));
        Vector3 normal = v2.crossProduct(v1).normalize();

        if (format.hasNormal && setNormal) {
            for (Vertex vertex : vertices) {
                vertex.normal[0] = (float) normal.x;
                vertex.normal[1] = (float) normal.y;
                vertex.normal[2] = (float) normal.z;
                vertex.normal[3] = 0;
            }
        }
        orientation = Direction.getNearest(normal.x, normal.y, normal.z);
    }

    public void copyFrom(Quad quad) {
        tintIndex = quad.tintIndex;
        orientation = quad.orientation;
        diffuseLighting = quad.diffuseLighting;
        sprite = quad.sprite;
        full = quad.full;
        for (int v = 0; v < 4; v++) {
            for (int e = 0; e < format.elementCount; e++) {
                System.arraycopy(quad.vertices[v].raw[e], 0, vertices[v].raw[e], 0, 4);
            }
        }
    }

    public void reset(CachedFormat format) {
        this.format = format;
        tintIndex = -1;
        orientation = null;
        diffuseLighting = true;
        sprite = null;
        for (int i = 0; i < vertices.length; i++) {
            Vertex v = vertices[i];
            if (v == null) {
                vertices[i] = v = new Vertex(format);
            }
            v.reset(format);
        }
        vertexIndex = 0;
        full = false;
    }

    public BakedQuad bake() {
        int[] packedData = new int[format.format.getVertexSize()];
        for (int v = 0; v < 4; v++) {
            for (int e = 0; e < format.elementCount; e++) {
                VertexUtils.pack(vertices[v].raw[e], packedData, format.format, v, e);
            }
        }

        return makeQuad(packedData);
    }

    private BakedQuad makeQuad(int[] packedData) {
        if (format.format != DefaultVertexFormat.BLOCK) {
            throw new IllegalStateException("Unable to bake this quad to the specified format. " + format.format);
        }
        return new BakedQuad(packedData, tintIndex, orientation, sprite, diffuseLighting);
    }

    public static class Vertex {

        public CachedFormat format;
        public float[][] raw;
        public float[] vec;
        public float[] normal;
        public float[] color;
        public float[] uv;
        public float[] overlay;
        public float[] lightmap;

        public Vertex(CachedFormat format) {
            this.format = format;
            raw = new float[format.elementCount][4];
            preProcess();
        }

        public void preProcess() {
            if (format.hasPosition) {
                vec = raw[format.positionIndex];
            }
            if (format.hasNormal) {
                normal = raw[format.normalIndex];
            }
            if (format.hasColor) {
                color = raw[format.colorIndex];
            }
            if (format.hasUV) {
                uv = raw[format.uvIndex];
            }
            if (format.hasOverlay) {
                overlay = raw[format.overlayIndex];
            }
            if (format.hasLightMap) {
                lightmap = raw[format.lightMapIndex];
            }
        }

        public void reset(CachedFormat format) {
            if (!this.format.equals(format) && format.elementCount > raw.length) {
                raw = new float[format.elementCount][4];
            }
            this.format = format;

            vec = null;
            normal = null;
            color = null;
            uv = null;
            lightmap = null;

            preProcess();
        }
    }
}
