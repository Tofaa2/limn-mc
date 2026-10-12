package io.github.tofaa2.limnmc.mixin;

import io.github.tofaa2.limnmc.render.LimnFrame;
import io.github.tofaa2.limnmc.render.LimnWorldRenderer;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WeatherEffectRenderer.class)
abstract class WeatherEffectRendererMixin {
    @Inject(method = {"render", "renderOit"}, at = @At("HEAD"), cancellable = true)
    private void limn$replaceWeather(CallbackInfo callback) {
        LimnWorldRenderer renderer = LimnWorldRenderer.active();
        if (LimnFrame.drawn() && renderer != null && renderer.replacesWeather()) {
            callback.cancel();
        }
    }
}
