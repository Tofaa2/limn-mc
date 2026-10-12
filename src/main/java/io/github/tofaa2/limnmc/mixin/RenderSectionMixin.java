package io.github.tofaa2.limnmc.mixin;

import io.github.tofaa2.limnmc.render.LimnSectionMesh;
import io.github.tofaa2.limnmc.render.LimnWorldRenderer;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SectionRenderDispatcher.RenderSection.class)
abstract class RenderSectionMixin {
    @Shadow
    private volatile long sectionNode;

    @Inject(method = "setSectionMesh", at = @At("HEAD"))
    private void limn$show(SectionMesh mesh, CallbackInfoReturnable<SectionMesh> callback) {
        LimnWorldRenderer renderer = LimnWorldRenderer.active();
        if (renderer != null && mesh instanceof LimnSectionMesh limnMesh) {
            renderer.showSection(limnMesh, sectionNode);
        }
    }
}
