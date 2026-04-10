package flu.kitten.adorablearmory.tooltip;

import com.mojang.blaze3d.vertex.PoseStack;
import flu.kitten.adorablearmory.AdorableArmory;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

public final class BulletLineClientTooltipComponent implements ClientTooltipComponent {

    private static final ResourceLocation ICON = new ResourceLocation(AdorableArmory.MODID, "textures/gui/tooltip/true_demon_text_bullet.png");
    private static final int ICON_TEX_W = 16;
    private static final int ICON_TEX_H = 16;
    // “显示出来”的尺寸
    private static final int ICON_DRAW_W = 14;
    private static final int ICON_DRAW_H = 14;
    private static final int ROW_H = 10; // 每行高度(>= ICON_DRAW_H)
    // 文字起始位置 - 图标占位宽度 + GAP
    private static final int ICON_ADVANCE_W = ICON_DRAW_W;
    private static final int GAP = -2;
    private static final int ICON_X_OFFSET = -2; // -2←
    private static final int ICON_Y_OFFSET = 4; // +5↓
    private final Component text;

    public BulletLineClientTooltipComponent(BulletLineTooltipComponent data) {
        this.text = data.text();
    }

    @Override
    public int getHeight() {
        return ROW_H;
    }

    @Override
    public int getWidth(Font font) {
        return ICON_ADVANCE_W + GAP + font.width(this.text);
    }

    @Override
    public void renderText(Font font, int x, int y, @NotNull Matrix4f matrix, @NotNull MultiBufferSource.BufferSource buffer) {
        int textY = y + (ROW_H - font.lineHeight) / 2;
        font.drawInBatch(this.text, (float) (x + ICON_ADVANCE_W + GAP), (float) textY, 0xFFFFFFFF, true, matrix, buffer, Font.DisplayMode.NORMAL, 0, 15728880);
    }

    @Override
    public void renderImage(@NotNull Font font, int x, int y, GuiGraphics graphics) {
        int iconX = x + ICON_X_OFFSET;
        int iconY = y + ICON_Y_OFFSET;

        float sx = ICON_DRAW_W / (float) ICON_TEX_W;
        float sy = ICON_DRAW_H / (float) ICON_TEX_H;

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(iconX, iconY, 0);
        pose.scale(sx, sy, 1.0f);

        graphics.blit(ICON, 0, 0, 0, 0, ICON_TEX_W, ICON_TEX_H, ICON_TEX_W, ICON_TEX_H);

        pose.popPose();
    }
}
