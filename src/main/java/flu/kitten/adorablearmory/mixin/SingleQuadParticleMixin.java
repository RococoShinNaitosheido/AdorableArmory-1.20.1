package flu.kitten.adorablearmory.mixin;

import com.mojang.blaze3d.vertex.VertexConsumer;
import flu.kitten.adorablearmory.api.duck.CosmicTerrainParticleAccess;
import flu.kitten.adorablearmory.client.compat.oculus.LolaCosmicParticleLateRenderQueue;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SingleQuadParticle.class)
public abstract class SingleQuadParticleMixin extends Particle {

    protected SingleQuadParticleMixin(ClientLevel level, double x, double y, double z) {
        super(level, x, y, z);
    }

    @Shadow public abstract float getQuadSize(float partialTick);
    @Shadow protected abstract float getU0();
    @Shadow protected abstract float getU1();
    @Shadow protected abstract float getV0();
    @Shadow protected abstract float getV1();

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void captureLateCosmicParticle(VertexConsumer consumer, Camera camera, float partialTick, CallbackInfo ci) {
        if (!(this instanceof CosmicTerrainParticleAccess access) || !access.adorablearmory$isCosmicTerrainParticle() || !LolaCosmicParticleLateRenderQueue.isReady()) {
            return;
        }

        Vec3 cameraPos = camera.getPosition();
        float x = (float) (Mth.lerp(partialTick, this.xo, this.x) - cameraPos.x());
        float y = (float) (Mth.lerp(partialTick, this.yo, this.y) - cameraPos.y());
        float z = (float) (Mth.lerp(partialTick, this.zo, this.z) - cameraPos.z());
        Quaternionf rotation = this.roll == 0.0F ? camera.rotation() : new Quaternionf(camera.rotation()).rotateZ(Mth.lerp(partialTick, this.oRoll, this.roll));
        Vector3f[] vertices = new Vector3f[]{new Vector3f(-1.0F, -1.0F, 0.0F), new Vector3f(-1.0F, 1.0F, 0.0F), new Vector3f(1.0F, 1.0F, 0.0F), new Vector3f(1.0F, -1.0F, 0.0F)};
        float size = this.getQuadSize(partialTick);

        for (Vector3f vertex : vertices) {
            vertex.rotate(rotation);
            vertex.mul(size);
            vertex.add(x, y, z);
        }

        LolaCosmicParticleLateRenderQueue.enqueue(vertices[0].x(), vertices[0].y(), vertices[0].z(), vertices[1].x(), vertices[1].y(), vertices[1].z(), vertices[2].x(), vertices[2].y(), vertices[2].z(), vertices[3].x(), vertices[3].y(), vertices[3].z(), this.getU0(), this.getU1(), this.getV0(), this.getV1(), this.rCol, this.gCol, this.bCol, this.alpha, this.getLightColor(partialTick));
        ci.cancel();
    }
}
