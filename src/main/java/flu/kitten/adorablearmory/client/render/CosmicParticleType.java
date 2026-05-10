package flu.kitten.adorablearmory.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import flu.kitten.adorablearmory.AdorableArmory;
import flu.kitten.adorablearmory.client.shader.AdorableArmoryShaders;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;
import org.jetbrains.annotations.NotNull;

public enum CosmicParticleType implements ParticleRenderType {

    INSTANCE;
    private boolean fallbackToTerrainSheet;
    private boolean warnedMissingShader;

    @Override
    public void begin(@NotNull BufferBuilder builder, @NotNull TextureManager manager) {
        ShaderInstance shader = AdorableArmoryShaders.cosmicParticleShader;
        if (shader == null || !AdorableArmoryShaders.uploadParticleUnity()) {
            fallbackToTerrainSheet = true;
            if (!warnedMissingShader) {
                AdorableArmory.LOGGER.warn("Cosmic particle shader is not ready; rendering Lola block particles with the vanilla terrain sheet.");
                warnedMissingShader = true;
            }
            ParticleRenderType.TERRAIN_SHEET.begin(builder, manager);
            return;
        }

        fallbackToTerrainSheet = false;
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);

        RenderSystem.setShader(() -> shader);
        RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_BLOCKS);
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
    }

    @Override
    public void end(@NotNull Tesselator tessellate) {
        if (fallbackToTerrainSheet) {
            ParticleRenderType.TERRAIN_SHEET.end(tessellate);
            fallbackToTerrainSheet = false;
            return;
        }

        BufferUploader.drawWithShader(tessellate.getBuilder().end());
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
    }

    @Override public String toString() {
        return "adorablearmory:cosmic_particle";
    }
}
