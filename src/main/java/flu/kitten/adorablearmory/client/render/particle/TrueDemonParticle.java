package flu.kitten.adorablearmory.client.render.particle;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import flu.kitten.adorablearmory.AdorableArmory;
import flu.kitten.adorablearmory.client.AdorableArmoryClient;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.awt.*;

public class TrueDemonParticle extends TextureSheetParticle {

    private final float baseSize;

    protected TrueDemonParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
        super(level, x, y, z);
        this.lifetime = 50;

        this.baseSize = 0.35f;
        this.quadSize = 0.0f;

        this.gravity = 0.0f;
        this.xd = vx * 0.056;
        this.yd = vy * 0.056;
        this.zd = vz * 0.056;

        this.setParticleColor(new Color(255, 43, 226, 255));
    }

    @Override
    public void tick() {
        super.tick();

        float time = (float) this.age / (float) this.lifetime;

        float s = Mth.sin((float)Math.PI * time);
        float sizeCurve = s * s;
        float minStart = 0.0f;
        this.quadSize = this.baseSize * Mth.clamp(sizeCurve, minStart, 1.0f);

        this.alpha = s * s;
    }

    @Override
    protected int getLightColor(float partialTicks) {
        return LightTexture.FULL_BRIGHT;
    }

    @Override
    public void render(@NotNull VertexConsumer buffer, @NotNull Camera camera, float partialTicks) {
        Vec3 camPos = camera.getPosition();
        float x = (float) (Mth.lerp(partialTicks, this.xo, this.x) - camPos.x());
        float y = (float) (Mth.lerp(partialTicks, this.yo, this.y) - camPos.y());
        float z = (float) (Mth.lerp(partialTicks, this.zo, this.z) - camPos.z());

        Quaternionf rotation = camera.rotation();
        Vector3f[] corners = new Vector3f[]{new Vector3f(-1.0F, -1.0F, 0.0F), new Vector3f(-1.0F,  1.0F, 0.0F), new Vector3f( 1.0F,  1.0F, 0.0F), new Vector3f( 1.0F, -1.0F, 0.0F)};

        float scale = this.getQuadSize(partialTicks);
        for (int i = 0; i < 4; ++i) {
            corners[i].rotate(rotation).mul(scale).add(x, y, z);
        }

        int light = this.getLightColor(partialTicks);

        buffer.vertex(corners[0].x(), corners[0].y(), corners[0].z()).uv(0.0F, 1.0F).color(this.rCol, this.gCol, this.bCol, this.alpha).uv2(light).endVertex();
        buffer.vertex(corners[1].x(), corners[1].y(), corners[1].z()).uv(0.0F, 0.0F).color(this.rCol, this.gCol, this.bCol, this.alpha).uv2(light).endVertex();
        buffer.vertex(corners[2].x(), corners[2].y(), corners[2].z()).uv(1.0F, 0.0F).color(this.rCol, this.gCol, this.bCol, this.alpha).uv2(light).endVertex();
        buffer.vertex(corners[3].x(), corners[3].y(), corners[3].z()).uv(1.0F, 1.0F).color(this.rCol, this.gCol, this.bCol, this.alpha).uv2(light).endVertex();
    }

    @Override
    public @NotNull ParticleRenderType getRenderType() {
        return TRUE_DEMON_GLOW_PARTICLE;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {

        public Provider(SpriteSet spriteSet) {}

        @Override
        public Particle createParticle(@NotNull SimpleParticleType type, @NotNull ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new TrueDemonParticle(level, x, y, z, vx, vy, vz);
        }
    }

    private static final ParticleRenderType TRUE_DEMON_GLOW_PARTICLE = new ParticleRenderType() {
        @Override
        public void begin(@NotNull BufferBuilder builder, @NotNull TextureManager textureManager) {
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            RenderSystem.disableCull();
            RenderSystem.depthMask(false);

            RenderSystem.applyModelViewMatrix();

            ShaderInstance shader = AdorableArmoryClient.getTrueDemonParticleShader();
            if (shader != null) {
                RenderSystem.setShader(() -> shader);
            } else {
                AdorableArmory.LOGGER.warn("True Demon Particle shader is null!"); // test
            }
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        @Override
        public void end(Tesselator tessellate) {
            BufferUploader.drawWithShader(tessellate.getBuilder().end());
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            RenderSystem.applyModelViewMatrix();
        }

        @Override
        public String toString() {
            return "true_demon_type";
        }
    };

    public void setParticleColor(Color color) {
        this.rCol = color.getRed() / 255.0f;
        this.gCol = color.getGreen() / 255.0f;
        this.bCol = color.getBlue() / 255.0f;
        this.alpha = color.getAlpha() / 255.0f;
    }
}
