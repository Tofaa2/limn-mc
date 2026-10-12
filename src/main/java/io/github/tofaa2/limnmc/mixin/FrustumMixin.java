package io.github.tofaa2.limnmc.mixin;

import io.github.tofaa2.limnmc.render.LimnWorldRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Frustum.class)
abstract class FrustumMixin {
    @Unique
    private static final int LIMN_FULLY_INSIDE = -2;

    @Inject(method = "cubeInFrustum(DDDDDD)I", at = @At("HEAD"), cancellable = true)
    private void limn$everythingIsTraced(CallbackInfoReturnable<Integer> callback) {
        if (LimnWorldRenderer.active() != null) {
            callback.setReturnValue(LIMN_FULLY_INSIDE);
        }
    }
}
