package flu.kitten.adorablearmory.client.compat.oculus.itemoutline;

import net.minecraft.util.Mth;

public record ItemOutlineData(int rgb, int radiusPixels) {
    public ItemOutlineData {
        rgb &= 0xFFFFFF;
        radiusPixels = Mth.clamp(radiusPixels, 1, 8);
    }

    public int red() {
        return (rgb >> 16) & 0xFF;
    }

    public int green() {
        return (rgb >> 8) & 0xFF;
    }

    public int blue() {
        return rgb & 0xFF;
    }
}
