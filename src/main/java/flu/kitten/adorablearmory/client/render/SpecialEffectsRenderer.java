package flu.kitten.adorablearmory.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import flu.kitten.adorablearmory.register.AdorableArmoryRegister;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class SpecialEffectsRenderer {

    public static boolean decorationEnabled = true;
    private final Map<String, Float> shapeRotationSpeed = new HashMap<>();
    private final Random random = new Random();
    private final Tesselator tesselator = Tesselator.getInstance();
    private final BufferBuilder bufferBuilder = tesselator.getBuilder();

    private float getRotationSpeed(String key, float min, float max) {
        return shapeRotationSpeed.computeIfAbsent(key, k -> {
            float dir = random.nextBoolean() ? 1F : -1F; // Random direction
            return dir * (min + random.nextFloat() * (max - min));
        });
    }

    @SubscribeEvent
    @OnlyIn(Dist.CLIENT)
    public void renderLevelStage(RenderLevelStageEvent event) {
        // Only render during translucent stage for proper blending
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;

        if (player == null || mc.level == null || !decorationEnabled) {
            return;
        }

        ItemStack heldItem = player.getMainHandItem();
        if (heldItem.isEmpty()) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        float partialTicks = event.getPartialTick();

        // Calculate interpolated positions (same as original)
        Vec3 cameraPos = event.getCamera().getPosition();
        double px = player.xOld + (player.getX() - player.xOld) * partialTicks;
        double py = player.yOld + (player.getY() - player.yOld) * partialTicks;
        double pz = player.zOld + (player.getZ() - player.zOld) * partialTicks;

        // Position in front of player (8 blocks forward, 2.5 blocks up)
        Vec3 lookVec = player.getViewVector(partialTicks).scale(8.0);
        double x = px + lookVec.x;
        double y = py + lookVec.y + 2.5;
        double z = pz + lookVec.z;

        // Start rendering
        poseStack.pushPose();
        poseStack.translate(x - cameraPos.x, y - cameraPos.y, z - cameraPos.z);

        // Apply base rotations and scaling (same as original)
        long time = System.currentTimeMillis();
        float angle = (time % 3600) / 10F;
        poseStack.mulPose(Axis.XP.rotationDegrees(angle));
        poseStack.mulPose(Axis.YP.rotationDegrees(angle));
        poseStack.scale(7F, 7F, 7F);

        // Setup rendering state
        setupRenderState();

        // Calculate rainbow color (same HSV logic as original)
        float hue = ((time % 3000L) / 3000f);
        int rgb = Color.HSBtoRGB(hue, 1.0f, 1.0f);
        float r = ((rgb >> 16) & 0xFF) / 255F;
        float g = ((rgb >> 8) & 0xFF) / 255F;
        float b = (rgb & 0xFF) / 255F;
        RenderSystem.setShaderColor(r, g, b, 1.0F);

        // Render effects based on item type
        if (isSpecialItem(heldItem)) {
            renderSpecialEffects(poseStack, time);
        } else {
            renderNormalEffects(poseStack, time);
        }

        // Render the held item at center
        renderHeldItem(poseStack, heldItem);

        // Cleanup and restore state
        restoreRenderState();
        poseStack.popPose();
    }

    private final Map<String, Vector3f> tumbleAxes = new HashMap<>();

    private Vector3f getTumbleAxis(String key) {
        return tumbleAxes.computeIfAbsent(key, k -> {
            double theta = random.nextDouble() * Math.PI * 2;
            double phi = Math.acos(2 * random.nextDouble() - 1);
            float x = (float)(Math.sin(phi) * Math.cos(theta));
            float y = (float)(Math.sin(phi) * Math.sin(theta));
            float z = (float)Math.cos(phi);
            return new Vector3f(x, y, z);
        });
    }

    private void renderSpecialEffects(PoseStack poseStack, long time) {
        float t = time % 10000L;

        poseStack.pushPose();
        Vector3f axis = getTumbleAxis("pentagon");
        float angle = t * getRotationSpeed("pentagon", 0.05f, 0.15f);
        float r1 = (float)(angle * (Math.PI / 180.0));
        Quaternionf quaternion = new Quaternionf().rotationAxis(r1, axis.x(), axis.y(), axis.z());
        poseStack.mulPose(quaternion);
        drawPolygon(poseStack, 5, 1.2);
        poseStack.popPose();

        // Square (rotating)
        poseStack.pushPose();
        Vector3f tumbleAxis = getTumbleAxis("square");
        float square = t * getRotationSpeed("square", 0.05f, 0.15f);
        float r2 = (float)(square * (Math.PI / 180.0));
        Quaternionf quaternion1 = new Quaternionf().rotationAxis(r2, tumbleAxis.x(), tumbleAxis.y(), tumbleAxis.z());
        poseStack.mulPose(quaternion1);
        drawPolygon(poseStack, 4, 1.0);
        poseStack.popPose();

        // Animated stars (4 layers with different scales and offsets)
        for (int i = 0; i < 4; i++) {
            poseStack.pushPose();
            float scale = 0.8f - i * 0.08f;
            float offset = (float) Math.sin(time / 300.0 + i) * 0.05f;
            poseStack.mulPose(Axis.YP.rotationDegrees(t * getRotationSpeed("star" + i, 0.05f, 0.2f)));
            poseStack.translate(offset, 0, -offset);
            drawStar(poseStack, 5, scale, scale * 0.55f);
            poseStack.popPose();
        }

        // Circle (with sine wave rotation)
        poseStack.pushPose();
        float circleOffset = (float) Math.sin(time / 300.0) * 2F;
        poseStack.mulPose(Axis.YP.rotationDegrees(circleOffset));
        drawPolygon(poseStack, 100, 0.6);
        poseStack.popPose();

        // Triangle (rotating)
        poseStack.pushPose();
        Vector3f triangle = getTumbleAxis("triangle");
        float squ = t * getRotationSpeed("triangle", 0.05f, 0.15f);
        float r3 = (float)(squ * (Math.PI / 180.0));
        Quaternionf quaternion2 = new Quaternionf().rotationAxis(r3, triangle.x(), triangle.y(), triangle.z());
        poseStack.mulPose(quaternion2);
        drawPolygon(poseStack, 3, 0.4);
        poseStack.popPose();
    }

    private void renderNormalEffects(PoseStack poseStack, long time) {
        float t = time % 10000L;

        // Square (faster rotation)
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(t * getRotationSpeed("normal_square", 0.2f, 0.7f)));
        drawPolygon(poseStack, 4, 1.0);
        poseStack.popPose();

        // Circle (sine wave rotation, larger radius)
        poseStack.pushPose();
        float offset = (float) Math.sin(time / 400.0) * 1.5F;
        poseStack.mulPose(Axis.YP.rotationDegrees(offset));
        drawPolygon(poseStack, 100, 0.7);
        poseStack.popPose();

        // Triangle (faster rotation)
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(t * getRotationSpeed("normal_triangle", 0.2f, 1.0f)));
        drawPolygon(poseStack, 3, 0.4);
        poseStack.popPose();
    }

    private void drawPolygon(PoseStack poseStack, int sides, double radius) {
        Matrix4f matrix = poseStack.last().pose();
        RenderSystem.setShader(GameRenderer::getPositionShader);

        bufferBuilder.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION);

        // Draw all vertices + close the loop
        for (int i = 0; i <= sides; i++) {
            double angle = 2 * Math.PI * i / sides;
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            bufferBuilder.vertex(matrix, (float) x, 0.02f, (float) z).endVertex();
        }

        tesselator.end();
    }

    private void drawStar(PoseStack poseStack, int points, double outerRadius, double innerRadius) {
        Matrix4f matrix = poseStack.last().pose();
        RenderSystem.setShader(GameRenderer::getPositionShader);

        bufferBuilder.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION);

        // Draw all vertices + close the loop
        for (int i = 0; i <= points * 2; i++) {
            double angle = Math.PI * i / points;
            double radius = (i % 2 == 0) ? outerRadius : innerRadius;
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            bufferBuilder.vertex(matrix, (float) x, 0.02f, (float) z).endVertex();
        }

        tesselator.end();
    }

    private void renderHeldItem(PoseStack poseStack, ItemStack itemStack) {
        poseStack.pushPose();

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        float scale = (1.0f / 7.0f) * 10.0f;
        poseStack.scale(scale, scale, scale);

        // Get item renderer and buffer source
        Minecraft mc = Minecraft.getInstance();
        ItemRenderer itemRenderer = mc.getItemRenderer();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        itemRenderer.renderStatic(itemStack, ItemDisplayContext.GROUND, 15728880, OverlayTexture.NO_OVERLAY, poseStack, bufferSource, mc.level, 0);

        bufferSource.endBatch();
        poseStack.popPose();
    }

    // RENDER STATE MANAGEMENT
    private void setupRenderState() {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.lineWidth(2.0F); // Same line width as original
    }

    private void restoreRenderState() {
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.lineWidth(1.0F);
    }

    // ITEM TYPE DETECTION
    // Determine if this is a "special" item that gets enhanced effects.
    // Replace this logic with your actual item checks.
    private boolean isSpecialItem(ItemStack stack) {
        // Example implementations:
        return stack.getItem() == AdorableArmoryRegister.SOFT_LIGHT_END_LOVE.get();
        // Option 2: Check item properties
        // return itemStack.getItem() instanceof SwordItem && itemStack.isEnchanted();

        // Option 3: Check NBT tags
        // return itemStack.hasTag() && itemStack.getTag().getBoolean("special_effects");

        // Option 4: Check registry name
        // ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(itemStack.getItem());
        // return itemId != null && itemId.toString().contains("your_mod:special_");
    }

    // REGISTRATION
    // Register this renderer to the Forge event bus.
    // Call this in your mod's client setup event.
    public static void register() {
        MinecraftForge.EVENT_BUS.register(new SpecialEffectsRenderer());
    }

    // UTILITY METHODS
    // Toggle effects on/off (useful for commands or config)
    public static void toggleEffects() {
        decorationEnabled = !decorationEnabled;
    }

    // Get current effects status
    public static boolean isEnabled() {
        return decorationEnabled;
    }
}
