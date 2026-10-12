package io.github.tofaa2.limnmc.mixin;

import io.github.tofaa2.limnmc.render.BlockAtlasPixels;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TextureAtlas.class)
abstract class TextureAtlasMixin {
    @Shadow
    @Final
    private Identifier location;

    @Inject(method = "upload", at = @At("TAIL"))
    private void limn$captureBlocks(SpriteLoader.Preparations preparations, CallbackInfo callback) {
        if (location.equals(TextureAtlas.LOCATION_BLOCKS)) {
            BlockAtlasPixels.capture(preparations.width(), preparations.height(), preparations.regions().values());
        }
    }

    @Inject(method = "cycleAnimationFrames", at = @At("TAIL"))
    private void limn$animateBlocks(CallbackInfo callback) {
        BlockAtlasPixels pixels = BlockAtlasPixels.latest();
        if (pixels != null && location.equals(TextureAtlas.LOCATION_BLOCKS)) {
            pixels.tickAnimations();
        }
    }
}
