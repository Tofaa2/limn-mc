package io.github.tofaa2.limnmc.mixin;

import io.github.tofaa2.limnmc.render.BlockLights;
import io.github.tofaa2.limnmc.render.LimnSectionMesh;
import io.github.tofaa2.limnmc.render.LimnSectionResults;
import io.github.tofaa2.limnmc.render.LimnWorldRenderer;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.chunk.TranslucencyPointOfView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CompiledSectionMesh.class)
abstract class CompiledSectionMeshMixin implements LimnSectionMesh {
    @Unique
    private volatile long limn$model;
    @Unique
    private volatile long limn$entity;
    @Unique
    private volatile long limn$seaModel;
    @Unique
    private volatile long limn$seaEntity;
    @Unique
    private BlockLights.Emitters limn$emitters = BlockLights.Emitters.NONE;

    @Override
    public long limn$model() {
        return limn$model;
    }

    @Override
    public void limn$setModel(long model) {
        limn$model = model;
    }

    @Override
    public long limn$entity() {
        return limn$entity;
    }

    @Override
    public void limn$setEntity(long entity) {
        limn$entity = entity;
    }

    @Override
    public BlockLights.Emitters limn$emitters() {
        return limn$emitters;
    }

    @Override
    public long limn$seaModel() {
        return limn$seaModel;
    }

    @Override
    public void limn$setSeaModel(long model) {
        limn$seaModel = model;
    }

    @Override
    public long limn$seaEntity() {
        return limn$seaEntity;
    }

    @Override
    public void limn$setSeaEntity(long entity) {
        limn$seaEntity = entity;
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void limn$takeModel(TranslucencyPointOfView pointOfView, SectionCompiler.Results results, long startTime, CallbackInfo callback) {
        LimnSectionResults limnResults = (LimnSectionResults) (Object) results;
        limn$model = limnResults.limn$takeModel();
        limn$emitters = limnResults.limn$emitters();
        limn$seaModel = limnResults.limn$takeSeaModel();
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void limn$release(CallbackInfo callback) {
        LimnWorldRenderer renderer = LimnWorldRenderer.active();
        if (renderer != null) {
            renderer.releaseSection(this);
        }
    }
}
