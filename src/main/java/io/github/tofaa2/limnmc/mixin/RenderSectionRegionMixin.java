package io.github.tofaa2.limnmc.mixin;

import io.github.tofaa2.limnmc.render.LimnWorldRenderer;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.world.level.CardinalLighting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderSectionRegion.class)
abstract class RenderSectionRegionMixin {
    @Unique
    private static final CardinalLighting LIMN_UNSHADED = new CardinalLighting(1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F);

    @Inject(method = "cardinalLighting", at = @At("HEAD"), cancellable = true)
    private void limn$unshaded(CallbackInfoReturnable<CardinalLighting> callback) {
        if (LimnWorldRenderer.active() != null) {
            callback.setReturnValue(LIMN_UNSHADED);
        }
    }
}
