package io.github.tofaa2.limnmc.mixin;

import io.github.tofaa2.limnmc.render.LimnFrame;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
    @ModifyArg(
        method = "renderLevel",
        at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4f;mul(Lorg/joml/Matrix4fc;)Lorg/joml/Matrix4f;", ordinal = 0, remap = false),
        index = 0
    )
    private Matrix4fc limn$keepViewSway(Matrix4fc sway) {
        LimnFrame.setViewSway(sway);
        return sway;
    }

    @ModifyArg(
        method = "renderLevel",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ProjectionMatrixBuffer;getBuffer(Lorg/joml/Matrix4f;)Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;"
        ),
        index = 0
    )
    private Matrix4f limn$keepLevelProjection(Matrix4f projection) {
        LimnFrame.setLevelProjection(projection);
        return projection;
    }
}
