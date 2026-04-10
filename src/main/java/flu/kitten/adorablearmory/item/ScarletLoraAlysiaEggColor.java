package flu.kitten.adorablearmory.item;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.common.ForgeSpawnEggItem;

import java.util.function.Supplier;

public class ScarletLoraAlysiaEggColor extends ForgeSpawnEggItem {
    public ScarletLoraAlysiaEggColor(Supplier<? extends EntityType<? extends Mob>> type, int backgroundColor, int highlightColor, Properties props) {
        super(type, 0, 0, props);
    }

    @Override
    public int getColor(int tintIndex) {
        double time = (System.currentTimeMillis() % 8000L) / 8000.0;
        float hue = (float) time;
        int base = Mth.hsvToRgb(hue, 0.5F, 0.5F) & 0xFFFFFF;
        int dot = Mth.hsvToRgb((hue + 0.15F) % 1.0F, 1.0F, 1.0F) & 0xFFFFFF;
        return tintIndex == 0 ? base : dot;
    }
}
