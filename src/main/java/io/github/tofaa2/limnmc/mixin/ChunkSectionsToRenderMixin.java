package io.github.tofaa2.limnmc.mixin;

import io.github.tofaa2.limnmc.render.LimnFrame;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkSectionsToRender.class)
abstract class ChunkSectionsToRenderMixin {
    @Inject(method = {"renderGroup", "renderOit"}, at = @At("HEAD"), cancellable = true)
    private void limn$skipTerrain(CallbackInfo callback) {
        if (LimnFrame.drawn()) {
            callback.cancel();
        }
    }
}
