package flu.kitten.adorablearmory.tooltip;

import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

// Common-side tooltip data.
// Client rendering is registered separately.
public record BulletLineTooltipComponent(Component text) implements TooltipComponent {
}
