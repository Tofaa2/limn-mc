package io.github.tofaa2.limnmc.mixin;

import io.github.tofaa2.limnmc.render.BlockLights;
import io.github.tofaa2.limnmc.render.LimnSectionResults;
import io.github.tofaa2.limnmc.render.LimnWorldRenderer;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SectionCompiler.Results.class)
abstract class SectionResultsMixin implements LimnSectionResults {
    @Unique
    private long limn$model;
    @Unique
    private long limn$seaModel;
    @Unique
    private BlockLights.Emitters limn$emitters = BlockLights.Emitters.NONE;

    @Override
    public long limn$takeModel() {
        long model = limn$model;
        limn$model = 0;
        return model;
    }

    @Override
    public void limn$setModel(long model) {
        limn$model = model;
    }

    @Override
    public BlockLights.Emitters limn$emitters() {
        return limn$emitters;
    }

    @Override
    public void limn$setEmitters(BlockLights.Emitters emitters) {
        limn$emitters = emitters;
    }

    @Override
    public long limn$takeSeaModel() {
        long model = limn$seaModel;
        limn$seaModel = 0;
        return model;
    }

    @Override
    public void limn$setSeaModel(long model) {
        limn$seaModel = model;
    }

    @Inject(method = "release", at = @At("HEAD"))
    private void limn$releaseModel(CallbackInfo callback) {
        LimnWorldRenderer renderer = LimnWorldRenderer.active();
        if (renderer != null) {
            renderer.releaseModel(0, limn$takeModel());
            renderer.releaseModel(0, limn$takeSeaModel());
        }
    }
}
