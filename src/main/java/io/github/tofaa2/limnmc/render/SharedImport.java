package io.github.tofaa2.limnmc.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.device.DeviceInfo;
import com.mojang.renderpearl.api.textures.GpuTexture;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.Locale;
import org.lwjgl.opengl.EXTMemoryObject;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;

public final class SharedImport {
    public static final String EXTENSION = "VK_KHR_external_memory_fd";
    public static final int EXPORTED_USAGE = 0x17;

    public record Request(long handle, long bytes) {
    }

    private static final ThreadLocal<Request> PENDING = new ThreadLocal<>();
    private static final IntArrayList MEMORY_OBJECTS = new IntArrayList();

    public static boolean available() {
        DeviceInfo info = RenderSystem.getDevice().getDeviceInfo();
        if (info.backendName().equals("Vulkan")) {
            return info.underlyingExtensions().contains(EXTENSION + " (D)");
        }
        if (info.backendName().equals("OpenGL") && System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("linux")) {
            GLCapabilities capabilities = GL.getCapabilities();
            return capabilities.GL_EXT_memory_object && capabilities.GL_EXT_memory_object_fd;
        }
        return false;
    }

    public static String deviceName() {
        String name = RenderSystem.getDevice().getDeviceInfo().name();
        int slash = name.indexOf('/');
        return slash < 0 ? name : name.substring(0, slash);
    }

    public static Request takePending() {
        Request request = PENDING.get();
        PENDING.remove();
        return request;
    }

    public static void keepMemoryObject(int memory) {
        MEMORY_OBJECTS.add(memory);
    }

    static void releaseMemoryObjects() {
        for (int index = 0; index < MEMORY_OBJECTS.size(); index++) {
            EXTMemoryObject.glDeleteMemoryObjectsEXT(MEMORY_OBJECTS.getInt(index));
        }
        MEMORY_OBJECTS.clear();
    }

    static GpuTexture importTexture(String label, GpuFormat format, int width, int height, long handle, long bytes) {
        PENDING.set(new Request(handle, bytes));
        try {
            return RenderSystem.getDevice()
                .createTexture(label, GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_COPY_DST, format, width, height, 1, 1);
        } finally {
            PENDING.remove();
        }
    }

    private SharedImport() {
    }
}
