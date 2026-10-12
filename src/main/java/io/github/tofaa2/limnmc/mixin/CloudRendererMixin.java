package io.github.tofaa2.limnmc.mixin;

import io.github.tofaa2.limnmc.render.LimnFrame;
import io.github.tofaa2.limnmc.render.LimnSettings;
import net.minecraft.client.renderer.CloudRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CloudRenderer.class)
abstract class CloudRendererMixin {
    @Inject(method = {"render", "renderOit"}, at = @At("HEAD"), cancellable = true)
    private void limn$replaceClouds(CallbackInfo callback) {
        if (LimnFrame.drawn() && LimnSettings.volumetricClouds) {
            callback.cancel();
        }
    }
}
