package io.github.tofaa2.limnmc.render;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import io.github.tofaa2.limnmc.nativeapi.LimnContext;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.Optional;
import net.minecraft.client.renderer.RenderPipelines;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

final class FrameComposite implements AutoCloseable {
    private static final int USAGE = GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING;

    private GpuTexture color;
    private GpuTextureView colorView;
    private ByteBuffer colorPixels;
    private int colorWidth;
    private int colorHeight;
    private GpuTexture depth;
    private GpuTextureView depthView;
    private ByteBuffer depthPixels;
    private int depthWidth;
    private int depthHeight;
    private boolean ready;
    private boolean shared;
    private GpuBufferSlice sharedDepthTransform;

    boolean useShared(LimnContext.SharedImages images) {
        close();
        SharedImport.releaseMemoryObjects();
        if (images == null) {
            shared = false;
            return false;
        }
        GpuDevice device = RenderSystem.getDevice();
        color = SharedImport.importTexture(
            "Limn shared color", GpuFormat.RGBA8_UNORM, images.width(), images.height(), images.colorHandle(), images.colorBytes()
        );
        colorView = device.createTextureView(color);
        colorWidth = images.width();
        colorHeight = images.height();
        depth = SharedImport.importTexture(
            "Limn shared depth", GpuFormat.R32_FLOAT, images.width(), images.height(), images.depthHandle(), images.depthBytes()
        );
        depthView = device.createTextureView(depth);
        depthWidth = images.width();
        depthHeight = images.height();
        shared = true;
        return true;
    }

    void presentShared(LimnContext context, Matrix4fc projection, float limnNear) {
        int[] size = context.depthSize();
        if (size[0] == 0 || size[1] == 0) {
            ready = false;
            return;
        }
        boolean zeroToOne = RenderSystem.getDevice().getDeviceInfo().isZZeroToOne();
        float offset = -projection.m22();
        float scale = projection.m32() / limnNear;
        Vector4f conversion = new Vector4f(
            zeroToOne ? offset : offset * 0.5F + 0.5F, zeroToOne ? scale : scale * 0.5F, (float) size[0] / depthWidth, (float) size[1] / depthHeight
        );
        Matrix4f toLimnClip = new Matrix4f(projection).mul(LimnFrame.viewSway()).mul(new Matrix4f(LimnFrame.levelProjection()).invert());
        sharedDepthTransform = RenderSystem.getDynamicUniforms().writeTransform(toLimnClip, conversion);
        ready = true;
    }

    boolean shared() {
        return shared;
    }

    void upload(LimnContext context, int width, int height, Matrix4fc projection, float limnNear) {
        GpuDevice device = RenderSystem.getDevice();
        CommandEncoder encoder = device.createCommandEncoder();
        if (color == null || colorWidth != width || colorHeight != height) {
            closeColor();
            color = device.createTexture("Limn color", USAGE, GpuFormat.RGBA8_UNORM, width, height, 1, 1);
            colorView = device.createTextureView(color);
            colorPixels = MemoryUtil.memAlloc(width * height * 4);
            colorWidth = width;
            colorHeight = height;
        }
        boolean zeroToOne = device.getDeviceInfo().isZZeroToOne();
        context.readPixels(MemorySegment.ofBuffer(colorPixels));
        flipRows(colorPixels, width * 4, height);
        encoder.writeToTexture(color, colorPixels, 0, 0, 0, 0, width, height);

        int[] size = context.depthSize();
        if (size[0] == 0 || size[1] == 0) {
            ready = false;
            return;
        }
        if (depth == null || depthWidth != size[0] || depthHeight != size[1]) {
            closeDepth();
            depth = device.createTexture("Limn depth", USAGE, GpuFormat.R32_FLOAT, size[0], size[1], 1, 1);
            depthView = device.createTextureView(depth);
            depthPixels = MemoryUtil.memAlloc(size[0] * size[1] * 4);
            depthWidth = size[0];
            depthHeight = size[1];
        }
        context.readDepth(MemorySegment.ofBuffer(depthPixels));
        convertDepth(projection, limnNear, zeroToOne);
        flipRows(depthPixels, depthWidth * 4, depthHeight);
        encoder.writeToTexture(depth, depthPixels, 0, 0, 0, 0, depthWidth, depthHeight);
        ready = true;
    }

    private void convertDepth(Matrix4fc projection, float limnNear, boolean zeroToOne) {
        float offset = -projection.m22();
        float scale = projection.m32() / limnNear;
        FloatBuffer values = depthPixels.asFloatBuffer();
        int count = values.remaining();
        for (int index = 0; index < count; index++) {
            float clip = offset + scale * values.get(index);
            float window = zeroToOne ? clip : clip * 0.5F + 0.5F;
            values.put(index, Math.clamp(window, 0.0F, 1.0F));
        }
    }

    private static void flipRows(ByteBuffer pixels, int rowBytes, int rows) {
        MemorySegment segment = MemorySegment.ofBuffer(pixels);
        byte[] upper = new byte[rowBytes];
        byte[] lower = new byte[rowBytes];
        for (int row = 0; row < rows / 2; row++) {
            long top = (long) row * rowBytes;
            long bottom = (long) (rows - 1 - row) * rowBytes;
            MemorySegment.copy(segment, JAVA_BYTE, top, upper, 0, rowBytes);
            MemorySegment.copy(segment, JAVA_BYTE, bottom, lower, 0, rowBytes);
            MemorySegment.copy(lower, 0, segment, JAVA_BYTE, top, rowBytes);
            MemorySegment.copy(upper, 0, segment, JAVA_BYTE, bottom, rowBytes);
        }
    }

    boolean ready() {
        return ready;
    }

    void draw(RenderTarget target) {
        if (shared) {
            drawShared(target);
            return;
        }
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        try (RenderPass pass = encoder.createRenderPass(() -> "Limn color", target.getColorTextureView(), Optional.empty())) {
            RenderSystem.bindDefaultUniforms(pass);
            pass.setPipeline(RenderSystem.getCompiledPipeline(RenderPipelines.TRACY_BLIT));
            pass.setUniform("InSampler", colorView, sampler);
            pass.draw(3, 1, 0, 0);
        }
        RenderPassDescriptor depthOnly = RenderPassDescriptor.builder(() -> "Limn depth").withDepthAttachment(target.getDepthTextureView()).build();
        try (RenderPass pass = encoder.createRenderPass(depthOnly)) {
            RenderSystem.bindDefaultUniforms(pass);
            pass.setPipeline(RenderSystem.getCompiledPipeline(RenderPipelines.BLIT_DEPTH));
            pass.setUniform("InSampler", depthView, sampler);
            pass.draw(3, 1, 0, 0);
        }
    }

    private void drawShared(RenderTarget target) {
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        try (RenderPass pass = encoder.createRenderPass(() -> "Limn color", target.getColorTextureView(), Optional.empty())) {
            RenderSystem.bindDefaultUniforms(pass);
            pass.setPipeline(RenderSystem.getCompiledPipeline(LimnPipelines.SHARED_COLOR));
            pass.setUniform("DynamicTransforms", sharedDepthTransform);
            pass.setUniform("InSampler", colorView, sampler);
            pass.draw(3, 1, 0, 0);
        }
        RenderPassDescriptor depthOnly = RenderPassDescriptor.builder(() -> "Limn depth").withDepthAttachment(target.getDepthTextureView()).build();
        try (RenderPass pass = encoder.createRenderPass(depthOnly)) {
            RenderSystem.bindDefaultUniforms(pass);
            pass.setPipeline(RenderSystem.getCompiledPipeline(LimnPipelines.SHARED_DEPTH));
            pass.setUniform("InSampler", depthView, sampler);
            pass.setUniform("DynamicTransforms", sharedDepthTransform);
            pass.draw(3, 1, 0, 0);
        }
    }

    private void closeColor() {
        if (color != null) {
            colorView.close();
            color.close();
            if (colorPixels != null) {
                MemoryUtil.memFree(colorPixels);
                colorPixels = null;
            }
            color = null;
        }
    }

    private void closeDepth() {
        if (depth != null) {
            depthView.close();
            depth.close();
            if (depthPixels != null) {
                MemoryUtil.memFree(depthPixels);
                depthPixels = null;
            }
            depth = null;
        }
    }

    @Override
    public void close() {
        closeColor();
        closeDepth();
        ready = false;
    }
}
