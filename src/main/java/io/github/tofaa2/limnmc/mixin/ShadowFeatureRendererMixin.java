package io.github.tofaa2.limnmc.mixin;

import io.github.tofaa2.limnmc.render.LimnFrame;
import io.github.tofaa2.limnmc.render.LimnSettings;
import net.minecraft.client.renderer.feature.ShadowFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ShadowFeatureRenderer.class)
abstract class ShadowFeatureRendererMixin {
    @Inject(method = "buildGroup", at = @At("HEAD"), cancellable = true)
    private void limn$realShadowsOnly(CallbackInfo callback) {
        if (LimnFrame.capturing() && LimnSettings.limnEntities && LimnSettings.shadows) {
            callback.cancel();
        }
    }
}
