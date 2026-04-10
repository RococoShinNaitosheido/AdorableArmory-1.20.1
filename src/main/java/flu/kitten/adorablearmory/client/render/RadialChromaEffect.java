package flu.kitten.adorablearmory.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import flu.kitten.adorablearmory.AdorableArmory;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.lang.reflect.Field;
import java.util.List;

@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE, modid = AdorableArmory.MODID)
public class RadialChromaEffect {

    private static PostChain chain;
    public static float SHIFT = 1.8f;
    public static float RADIAL = 8f;
    public static float VIG_WIDTH = 0.18f;
    public static float VIG_STRENGTH = 0.90f;
    public static float VIG_PULSE = 0.32f;
    private static int lastW = -1, lastH = -1;
    private static final long RISE_TO_PEAK_NS = 1_600_000_000L;
    private static final long RELEASE_NS = 1_400_000_000L;
    private static final float FREEZE_THRESHOLD = 0.998f;
    public static boolean wantActive = false;
    private enum State { IDLE, RISING, FROZEN, RELEASING }
    private static State state = State.IDLE;
    private static long stateStartNs = -1L;
    private static long startGameTick = 0L;

    public static void trigger() {
        long now = Util.getNanos();
        Minecraft minecraft = Minecraft.getInstance();

        if (state == State.FROZEN) {
            state = State.RELEASING;
            stateStartNs = now;
            wantActive = true;
            return;
        }

        if (minecraft.level != null) {
            startGameTick = minecraft.level.getGameTime();
        } else {
            startGameTick = 0L;
        }

        state = State.RISING;
        stateStartNs = now;
        wantActive = true;
    }

    public static String debugState() {
        return state.name();
    }

    private static float smooths(float x) {
        return x * x * (3.0f - 2.0f * x);
    }

    private static void stopAndDispose() {
        wantActive = false;
        state = State.IDLE;
        stateStartNs = -1L;
        dispose();
    }

    public static float currentAmount() {
        if (!wantActive) return 0f;

        long now = Util.getNanos();
        if (stateStartNs < 0L) stateStartNs = now;
        long t = now - stateStartNs;

        return switch (state) {
            case IDLE -> 0f;

            case RISING -> {
                float x = (float) t / (float) RISE_TO_PEAK_NS;
                x = Mth.clamp(x, 0f, 1f);
                float amount = smooths(x);

                if (amount >= FREEZE_THRESHOLD || x >= 1f) {
                    state = State.FROZEN;
                    stateStartNs = now;
                    yield 1.0f;
                }
                yield amount;
            }

            case FROZEN -> 1.0f;

            case RELEASING -> {
                float x = (float) t / (float) RELEASE_NS;
                x = Mth.clamp(x, 0f, 1f);
                float amount = 1.0f - smooths(x);

                if (x >= 1f || amount <= 0.0001f) {
                    stopAndDispose();
                    yield 0f;
                }
                yield amount;
            }
        };
    }

    public static void ensureInit() {
        if (chain != null) return;
        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget main = minecraft.getMainRenderTarget();
        try {
            chain = new PostChain(minecraft.getTextureManager(), minecraft.getResourceManager(), main, new ResourceLocation(AdorableArmory.MODID, "shaders/post/chromab.json"));
            chain.resize(main.width, main.height);
            lastW = main.width;
            lastH = main.height;
        } catch (Exception e) {
            e.printStackTrace();
            chain = null;
        }
    }

    public static void ensureResized() {
        if (chain == null) return;
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        if (main.width != lastW || main.height != lastH) {
            chain.resize(main.width, main.height);
            lastW = main.width;
            lastH = main.height;
        }
    }

    public static void dispose() {
        if (chain != null) {
            chain.close();
            chain = null;
            lastW = lastH = -1;
        }
    }

    public static PostChain getChain() {
        return chain;
    }

    static final class ChromaUniforms {
        private static Field passesField;

        @SuppressWarnings("unchecked")
        public static List<PostPass> getPasses(PostChain chain) {
            try {
                if (passesField == null) {
                    passesField = ObfuscationReflectionHelper.findField(PostChain.class, "f_110009_");
                }
                passesField.setAccessible(true);
                return (List<PostPass>) passesField.get(chain);
            } catch (Throwable t) {
                t.printStackTrace();
                return List.of();
            }
        }

        public static void setChromaUniforms(PostChain chain, float shift, float radial, float amount, float timeSec, float vigWidth, float vigStrength, float vigPulse) {
            for (PostPass postPass : getPasses(chain)) {
                var shader = postPass.getEffect();

                var uShiftX = shader.getUniform("ShiftX");
                var uShiftY = shader.getUniform("ShiftY");
                var uRadial = shader.getUniform("Radial");
                var uAmount = shader.getUniform("Amount");

                var uTime = shader.getUniform("ChromaTime");

                var uVw = shader.getUniform("VigWidth");
                var uVs = shader.getUniform("VigStrength");
                var uVp = shader.getUniform("VigPulse");

                if (uShiftX != null) uShiftX.set(shift);
                if (uShiftY != null) uShiftY.set(shift);
                if (uRadial != null) uRadial.set(radial);
                if (uAmount != null) uAmount.set(amount);

                if (uTime != null) uTime.set(timeSec);

                if (uVw != null) uVw.set(vigWidth);
                if (uVs != null) uVs.set(vigStrength);
                if (uVp != null) uVp.set(vigPulse);
            }
        }
    }

    public static void renderPostInGuiStage(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            stopAndDispose();
            return;
        }

        float amount = currentAmount();
        if (!wantActive || amount <= 0.0001f) return;

        ensureInit();
        ensureResized();
        if (chain == null) return;

        long nowTick = mc.level.getGameTime();
        float timeSec = ((nowTick - startGameTick) + partialTick) / 20;
        if (timeSec < 0) timeSec = 0;

        ChromaUniforms.setChromaUniforms(chain, SHIFT, RADIAL, amount, timeSec, VIG_WIDTH, VIG_STRENGTH, VIG_PULSE);

        beginPostGui();
        chain.process(partialTick);
        mc.getMainRenderTarget().bindWrite(true);
        endPostGui();
    }

    private static void beginPostGui() {
        RenderSystem.disableScissor();
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
    }

    private static void endPostGui() {
        RenderSystem.disableScissor();
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
    }
}
