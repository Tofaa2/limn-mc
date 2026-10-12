package io.github.tofaa2.limnmc.mixin;

import com.mojang.blaze3d.vertex.VertexSorting;
import io.github.tofaa2.limnmc.render.BlockLights;
import io.github.tofaa2.limnmc.render.LimnSectionResults;
import io.github.tofaa2.limnmc.render.LimnWorldRenderer;
import io.github.tofaa2.limnmc.render.TerrainMeshes;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SectionCompiler.class)
abstract class SectionCompilerMixin {
    @Inject(method = "compile", at = @At("RETURN"))
    private void limn$buildModel(
        SectionPos section,
        RenderSectionRegion region,
        VertexSorting sorting,
        SectionBufferBuilderPack builders,
        CallbackInfoReturnable<SectionCompiler.Results> callback
    ) {
        if (LimnWorldRenderer.active() == null) {
            return;
        }
        SectionCompiler.Results results = callback.getReturnValue();
        LimnSectionResults limnResults = (LimnSectionResults) (Object) results;
        TerrainMeshes.Built built = TerrainMeshes.build(results, section);
        limnResults.limn$setModel(built.model());
        limnResults.limn$setSeaModel(built.seaModel());
        limnResults.limn$setEmitters(BlockLights.collect(section, region));
    }

    @ModifyArg(
        method = "compile",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/block/ModelBlockRenderer;<init>(ZZLnet/minecraft/client/color/block/BlockColors;)V"
        ),
        index = 0
    )
    private boolean limn$withoutBakedOcclusion(boolean ambientOcclusion) {
        return ambientOcclusion && LimnWorldRenderer.active() == null;
    }
}
