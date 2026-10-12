package io.github.tofaa2.limnmc.mixin;

import java.util.List;
import net.minecraft.client.renderer.texture.SpriteContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SpriteContents.AnimatedTexture.class)
public interface AnimatedTextureAccessor {
    @Accessor("frames")
    List<SpriteContents.FrameInfo> limn$frames();

    @Accessor("frameRowSize")
    int limn$frameRowSize();
}
