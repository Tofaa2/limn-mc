package io.github.tofaa2.limnmc.mixin;

import io.github.tofaa2.limnmc.render.LimnSettings;
import io.github.tofaa2.limnmc.render.LimnWorldRenderer;
import io.github.tofaa2.limnmc.render.ParticleBursts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParticleEngine.class)
abstract class ParticleEngineMixin {
    @Inject(method = "createParticle", at = @At("HEAD"), cancellable = true)
    private void limn$replaceSmoke(
        ParticleOptions options, double x, double y, double z, double xa, double ya, double za, CallbackInfoReturnable<Particle> callback
    ) {
        LimnWorldRenderer renderer = LimnWorldRenderer.active();
        if (renderer == null || !LimnSettings.volumetricParticles) {
            return;
        }
        ParticleType<?> type = options.getType();
        if (type == ParticleTypes.CAMPFIRE_COSY_SMOKE || type == ParticleTypes.CAMPFIRE_SIGNAL_SMOKE) {
            callback.setReturnValue(null);
            return;
        }
        ParticleBursts.Look look = null;
        int color = 0xFFFFFF;
        if (options instanceof BlockParticleOption block && type == ParticleTypes.BLOCK) {
            look = ParticleBursts.Look.DUST;
            color = block.getState().getMapColor(Minecraft.getInstance().level, BlockPos.containing(x, y, z)).col;
        } else if (type == ParticleTypes.CRIT) {
            look = ParticleBursts.Look.SPARK;
            color = 0xFFD070;
        } else if (type == ParticleTypes.ENCHANTED_HIT) {
            look = ParticleBursts.Look.SPARK;
            color = 0x70D0FF;
        } else if (type == ParticleTypes.SPLASH || type == ParticleTypes.RAIN || type == ParticleTypes.FISHING) {
            look = ParticleBursts.Look.DROPLET;
            color = 0x7090E0;
        } else if (type == ParticleTypes.LAVA) {
            look = ParticleBursts.Look.EMBER;
            color = 0xFF7020;
        }
        if (look != null) {
            if (renderer.addBurst(look, color, x, y, z)) {
                callback.setReturnValue(null);
            }
            return;
        }
        boolean blast = type == ParticleTypes.EXPLOSION || type == ParticleTypes.EXPLOSION_EMITTER;
        boolean puff = type == ParticleTypes.SMOKE || type == ParticleTypes.LARGE_SMOKE || type == ParticleTypes.POOF || type == ParticleTypes.WHITE_SMOKE;
        if ((blast || puff) && renderer.addSmokePuff(x, y, z, blast) && !blast) {
            callback.setReturnValue(null);
        }
    }
}
