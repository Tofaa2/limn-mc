package io.github.tofaa2.limnmc.mixin;

import io.github.tofaa2.limnmc.render.EntityModels;
import io.github.tofaa2.limnmc.render.LimnFrame;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelFeatureRenderer.class)
abstract class ModelFeatureRendererMixin {
    @Inject(method = "prepareModel", at = @At("HEAD"), cancellable = true)
    private <S> void limn$drawInLimn(ModelFeatureRenderer.Submit<S> submit, CallbackInfo callback) {
        if (LimnFrame.capturing() && EntityModels.capture(submit)) {
            callback.cancel();
        }
    }
}
