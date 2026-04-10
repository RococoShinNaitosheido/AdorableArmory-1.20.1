/*
package flu.kitten.adorablearmory.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.awt.*;

public class ClientHealthHud {
    public static final IGuiOverlay ACTUAL_HEALTH_HUD = (ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) -> {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;

        if (player == null || player.isSpectator()) {
            return;
        }

        float currentHealth = player.getHealth();
        float maxHealth = player.getMaxHealth();

        String healthText = String.format("HP: %.1f / %.1f", currentHealth, maxHealth);

        int x = 10;
        int y = 10;

        guiGraphics.drawString(mc.font, healthText, x, y, Color.RED.getRGB(), false);
    };
}
*/
