package flu.kitten.adorablearmory.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import flu.kitten.adorablearmory.client.shader.AdorableArmoryShaders;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;
import org.jetbrains.annotations.NotNull;

public enum CosmicParticleType implements ParticleRenderType {

    INSTANCE;

    @Override
    public void begin(BufferBuilder builder, @NotNull TextureManager manager) {
        System.out.println("[DEBUG] CosmicParticleType.begin() called. Shader is: " + (AdorableArmoryShaders.cosmicShader == null ? "NULL" : "LOADED"));

        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);

        RenderSystem.setShader(() -> AdorableArmoryShaders.cosmicShader);
        RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_BLOCKS);

        Minecraft.getInstance().gameRenderer.lightTexture().turnOnLightLayer();
        AdorableArmoryShaders.uploadParticleUnity();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
    }

    @Override
    public void end(Tesselator tessellate) {
        tessellate.end();
        Minecraft.getInstance().gameRenderer.lightTexture().turnOffLightLayer();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
    }

    @Override public String toString() {
        return "adorablearmory:cosmic_particle";
    }
}

