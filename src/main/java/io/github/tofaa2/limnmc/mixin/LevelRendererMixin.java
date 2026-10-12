package io.github.tofaa2.limnmc.mixin;

import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import io.github.tofaa2.limnmc.render.LimnFrame;
import io.github.tofaa2.limnmc.render.LimnWorldRenderer;
import io.github.tofaa2.limnmc.render.SkyState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
abstract class LevelRendererMixin {
    @Shadow
    @Final
    private LevelTargetBundle targets;
    @Shadow
    @Final
    private LevelRenderState levelRenderState;

    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private boolean limn$beginWorld(
        boolean shouldRenderSky,
        GraphicsResourceAllocator allocator,
        boolean renderOutline,
        CameraRenderState camera,
        GpuBufferSlice terrainFog,
        Vector4f fogColor,
        boolean originalShouldRenderSky,
        boolean consistentDepthRequired
    ) {
        RenderTarget target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        LimnWorldRenderer renderer = LimnWorldRenderer.acquire(target.width, target.height);
        if (renderer == null) {
            return shouldRenderSky;
        }
        camera.smartCull = false;
        renderer.beginCapture(camera);
        LimnFrame.setCapturing(true);
        return false;
    }

    @Inject(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher;prepareFrame(Lnet/minecraft/client/renderer/SubmitNodeStorage;)Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;",
            shift = At.Shift.AFTER
        )
    )
    private void limn$renderWorld(
        GraphicsResourceAllocator allocator,
        boolean renderOutline,
        CameraRenderState camera,
        GpuBufferSlice terrainFog,
        Vector4f fogColor,
        boolean shouldRenderSky,
        boolean consistentDepthRequired,
        CallbackInfo callback
    ) {
        if (!LimnFrame.capturing()) {
            return;
        }
        LimnFrame.setCapturing(false);
        RenderTarget target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        LimnWorldRenderer renderer = LimnWorldRenderer.active();
        LimnFrame.setDrawn(
            renderer != null && renderer.renderFrame(
                camera, SkyState.current(levelRenderState.worldPartialTicks, levelRenderState.cloudHeight), target.width, target.height
            )
        );
    }

    @Inject(method = "addMainPass", at = @At("HEAD"))
    private void limn$composite(
        FrameGraphBuilder frame,
        FeatureRenderDispatcher.PreparedFrame featureFrame,
        GpuBufferSlice terrainFog,
        ChunkSectionsToRender sections,
        boolean consistentDepthRequired,
        CallbackInfo callback
    ) {
        LimnWorldRenderer renderer = LimnWorldRenderer.active();
        if (renderer == null || !LimnFrame.drawn()) {
            return;
        }
        FramePass pass = frame.addPass("limn");
        targets.main = pass.readsAndWrites(targets.main);
        ResourceHandle<RenderTarget> main = targets.main;
        pass.executes(() -> renderer.composite(main.get()));
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void limn$endFrame(CallbackInfo callback) {
        LimnFrame.setDrawn(false);
        LimnFrame.setCapturing(false);
    }
}
