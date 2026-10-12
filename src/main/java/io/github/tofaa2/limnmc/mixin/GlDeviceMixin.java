package io.github.tofaa2.limnmc.mixin;

import com.mojang.renderpearl.backend.opengl.GlStateManager;
import io.github.tofaa2.limnmc.render.SharedImport;
import java.nio.ByteBuffer;
import org.lwjgl.opengl.EXTMemoryObject;
import org.lwjgl.opengl.EXTMemoryObjectFD;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "com.mojang.renderpearl.backend.opengl.GlDevice")
abstract class GlDeviceMixin {
    @Redirect(
        method = "createTexture",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/renderpearl/backend/opengl/GlStateManager;_texImage2D(IIIIIIIILjava/nio/ByteBuffer;)V",
            ordinal = 1
        )
    )
    private void limn$importShared(int target, int level, int internalFormat, int width, int height, int border, int format, int type, ByteBuffer pixels) {
        SharedImport.Request request = SharedImport.takePending();
        if (request == null) {
            GlStateManager._texImage2D(target, level, internalFormat, width, height, border, format, type, pixels);
            return;
        }
        int memory = EXTMemoryObject.glCreateMemoryObjectsEXT();
        EXTMemoryObjectFD.glImportMemoryFdEXT(memory, request.bytes(), EXTMemoryObjectFD.GL_HANDLE_TYPE_OPAQUE_FD_EXT, (int) request.handle());
        EXTMemoryObject.glTexStorageMem2DEXT(target, 1, internalFormat, width, height, memory, 0);
        SharedImport.keepMemoryObject(memory);
    }
}
