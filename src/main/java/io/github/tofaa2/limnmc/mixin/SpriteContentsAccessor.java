package io.github.tofaa2.limnmc.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.SpriteContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SpriteContents.class)
public interface SpriteContentsAccessor {
    @Accessor("originalImage")
    NativeImage limn$originalImage();

    @Accessor("animatedTexture")
    SpriteContents.AnimatedTexture limn$animatedTexture();
}
