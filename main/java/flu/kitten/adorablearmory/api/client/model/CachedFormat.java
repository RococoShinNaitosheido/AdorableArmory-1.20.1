package flu.kitten.adorablearmory.api.client.model;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CachedFormat {
    public static final CachedFormat BLOCK = new CachedFormat(DefaultVertexFormat.BLOCK);
    private static final Map<VertexFormat, CachedFormat> formatCache = new ConcurrentHashMap<>();
    public final VertexFormat format;
    public final boolean hasPosition;
    public final boolean hasNormal;
    public final boolean hasColor;
    public final boolean hasUV;
    public final boolean hasOverlay;
    public final boolean hasLightMap;
    public final int positionIndex;
    public final int normalIndex;
    public final int colorIndex;
    public final int uvIndex;
    public final int overlayIndex;
    public final int lightMapIndex;
    public final int elementCount;

    /**
     * Caches the vertex format element indexes for efficiency.
     *
     * @param format The format.
     */
    private CachedFormat(VertexFormat format) {
        this.format = format;
        List<VertexFormatElement> elements = format.getElements();
        elementCount = elements.size();

        boolean hasPosition = false;
        boolean hasNormal = false;
        boolean hasColor = false;
        boolean hasUV = false;
        boolean hasOverlay = false;
        boolean hasLightMap = false;

        int positionIndex = -1;
        int normalIndex = -1;
        int colorIndex = -1;
        int uvIndex = -1;
        int overlayIndex = -1;
        int lightMapIndex = -1;

        for (int i = 0; i < elementCount; i++) {
            VertexFormatElement element = elements.get(i);
            switch (element.getUsage()) {
                case POSITION:
                    if (hasPosition) {
                        throw new IllegalStateException("Found 2 position elements..");
                    }
                    hasPosition = true;
                    positionIndex = i;
                    break;
                case NORMAL:
                    if (hasNormal) {
                        throw new IllegalStateException("Found 2 normal elements..");
                    }
                    hasNormal = true;
                    normalIndex = i;
                    break;
                case COLOR:
                    if (hasColor) {
                        throw new IllegalStateException("Found 2 color elements..");
                    }
                    hasColor = true;
                    colorIndex = i;
                    break;
                case UV:
                    switch (element.getIndex()) {
                        case 0 -> {
                            if (hasUV) {
                                throw new IllegalStateException("Found 2 UV elements..");
                            }
                            hasUV = true;
                            uvIndex = i;
                        }
                        case 1 -> {
                            if (hasOverlay) {
                                throw new IllegalStateException("Found 2 Overlay elements..");
                            }
                            hasOverlay = true;
                            overlayIndex = i;
                        }
                        case 2 -> {
                            if (hasLightMap) {
                                throw new IllegalStateException("Found 2 LightMap elements..");
                            }
                            hasLightMap = true;
                            lightMapIndex = i;
                        }
                    }
                    break;
            }
        }

        this.hasPosition = hasPosition;
        this.hasNormal = hasNormal;
        this.hasColor = hasColor;
        this.hasUV = hasUV;
        this.hasOverlay = hasOverlay;
        this.hasLightMap = hasLightMap;

        this.positionIndex = positionIndex;
        this.normalIndex = normalIndex;
        this.colorIndex = colorIndex;
        this.uvIndex = uvIndex;
        this.overlayIndex = overlayIndex;
        this.lightMapIndex = lightMapIndex;
    }

    /**
     * Lookup or create the CachedFormat for a given VertexFormat.
     *
     * @param format The format to lookup.
     * @return The CachedFormat.
     */
    public static CachedFormat lookup(VertexFormat format) {
        if (format == DefaultVertexFormat.BLOCK) {
            return BLOCK;
        }
        return formatCache.computeIfAbsent(format, CachedFormat::new);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof CachedFormat other)) {
            return false;
        }
        return other.elementCount == elementCount &&
                other.positionIndex == positionIndex &&
                other.normalIndex == normalIndex &&
                other.colorIndex == colorIndex &&
                other.uvIndex == uvIndex &&
                other.lightMapIndex == lightMapIndex;
    }

    @Override
    public int hashCode() {
        int result = 1;
        result = 31 * result + elementCount;
        result = 31 * result + positionIndex;
        result = 31 * result + normalIndex;
        result = 31 * result + colorIndex;
        result = 31 * result + uvIndex;
        result = 31 * result + lightMapIndex;
        return result;
    }
}
