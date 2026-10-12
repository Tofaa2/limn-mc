package io.github.tofaa2.limnmc.nativeapi;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.file.Path;

public final class LimnContext implements AutoCloseable {
    public record Info(
        int vendorId,
        boolean rayTracing,
        int pathTracing,
        boolean dlssSuperResolution,
        boolean dlssRayReconstruction,
        int recommendedQuality,
        long gpuMemoryBytes
    ) {
    }

    private final LimnLibrary library;
    private final MemorySegment handle;

    private LimnContext(LimnLibrary library, MemorySegment handle) {
        this.library = library;
        this.handle = handle;
    }

    public record SharedImages(long colorHandle, long depthHandle, long colorBytes, long depthBytes, int width, int height) {
    }

    public static LimnContext create(LimnLibrary library, int width, int height, int flags, Path pipelineCache, String preferredDevice) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment options = arena.allocate(LimnAbi.Options.SIZE, 8);
            options.set(JAVA_INT, LimnAbi.Options.WIDTH, width);
            options.set(JAVA_INT, LimnAbi.Options.HEIGHT, height);
            options.set(JAVA_INT, LimnAbi.Options.FLAGS, flags);
            options.set(ADDRESS, LimnAbi.Options.PIPELINE_CACHE_PATH, arena.allocateFrom(pipelineCache.toString()));
            if (preferredDevice != null) {
                options.set(ADDRESS, LimnAbi.Options.PREFERRED_DEVICE, arena.allocateFrom(preferredDevice));
            }
            MemorySegment handle = (MemorySegment) library.create.invokeExact(options);
            if (handle.equals(MemorySegment.NULL)) {
                throw new LimnException("limn did not start: " + library.lastError());
            }
            return new LimnContext(library, handle);
        } catch (LimnException exception) {
            throw exception;
        } catch (Throwable throwable) {
            throw failure(throwable);
        }
    }

    public Info info() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment info = arena.allocate(LimnAbi.Info.SIZE, 8);
            library.info.invokeExact(handle, info);
            return new Info(
                info.get(JAVA_INT, LimnAbi.Info.VENDOR_ID),
                info.get(JAVA_INT, LimnAbi.Info.RAY_TRACING) != 0,
                info.get(JAVA_INT, LimnAbi.Info.PATH_TRACING),
                info.get(JAVA_INT, LimnAbi.Info.DLSS_SUPER_RESOLUTION) != 0,
                info.get(JAVA_INT, LimnAbi.Info.DLSS_RAY_RECONSTRUCTION) != 0,
                info.get(JAVA_INT, LimnAbi.Info.RECOMMENDED_QUALITY),
                info.get(JAVA_LONG, LimnAbi.Info.GPU_MEMORY_BYTES)
            );
        } catch (Throwable throwable) {
            throw failure(throwable);
        }
    }

    public void resize(int width, int height) {
        try {
            check((int) library.resize.invokeExact(handle, width, height));
        } catch (Throwable throwable) {
            throw failure(throwable);
        }
    }

    public long createScene() {
        try {
            return checkHandle((long) library.sceneCreate.invokeExact(handle));
        } catch (Throwable throwable) {
            throw failure(throwable);
        }
    }

    public void destroyScene(long scene) {
        try {
            library.sceneDestroy.invokeExact(handle, scene);
        } catch (Throwable throwable) {
            throw failure(throwable);
        }
    }

    public void setSun(long scene, MemorySegment sun) {
        try {
            library.sceneSetSun.invokeExact(handle, scene, sun);
        } catch (Throwable throwable) {
            throw failure(throwable);
        }
    }

    public void shiftScene(long scene, float x, float y, float z) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment offset = arena.allocateFrom(JAVA_FLOAT, x, y, z);
            check((int) library.sceneShift.invokeExact(handle, scene, offset));
        } catch (Throwable throwable) {
            throw failure(throwable);
        }
    }

    public void setSky(long scene, long sky, float intensity) {
        try {
            library.sceneSetSky.invokeExact(handle, scene, sky, intensity);
        } catch (Throwable throwable) {
            throw failure(throwable);
        }
    }

    public void setClouds(long scene, MemorySegment clouds) {
        try {
            check((int) library.sceneSetClouds.invokeExact(handle, scene, clouds));
        } catch (Throwable throwable) {
            throw failure(throwable);
        }
    }

    public void setLights(long scene, MemorySegment lights, int count) {
        try {
            check((int) library.sceneSetLights.invokeExact(handle, scene, lights, count));
        } catch (Throwable throwable) {
            throw failure(throwable);
        }
    }

    public long createFluid(long scene, MemorySegment fluid) {
        try {
            return checkHandle((long) library.fluidCreate.invokeExact(handle, scene, fluid));
        } catch (Throwable throwable) {
            throw failure(throwable);
        }
    }

    public void updateFluid(long fluidHandle, MemorySegment fluid) {
        try {
            check((int) library.fluidSet.invokeExact(handle, fluidHandle, fluid));
        } catch (Throwable throwable) {
            throw failure(throwable);
        }
    }

    public void destroyFluid(long fluidHandle) {
        try {
            library.fluidDestroy.invokeExact(handle, fluidHandle);
        } catch (Throwable throwable) {
            throw failure(throwable);
        }
    }

    public long createEmitter(long scene, MemorySegment emitter) {
        try {
            return checkHandle((long) library.emitterCreate.invokeExact(handle, scene, emitter));
        } catch (Throwable throwable) {
            throw failure(throwable);
        }
    }

    public void updateEmitter(long emitterHandle, MemorySegment emitter) {
        try {
            library.emitterSet.invokeExact(handle, emitterHandle, emitter);
        } catch (Throwable throwable) {
            throw failure(throwable);
        }
    }

    public void burstEmitter(long emitterHandle, int count) {
        try {
            library.emitterBurst.invokeExact(handle, emitterHandle, count);
        } catch (Throwable throwable) {
            throw failure(throwable);
        }
    }

    public void destroyEmitter(long emitterHandle) {
        try {
            library.emitterDestroy.invokeExact(handle, emitterHandle);
        } catch (Throwable throwable) {
            throw failure(throwable);
        }
    }

    public long createWater(long scene, MemorySegment water) {
        try {
            return checkHandle((long) library.waterCreate.invokeExact(handle, scene, water));
        } catch (Throwable throwable) {
            throw failure(throwable);
        }
    }

    public void updateWater(long waterHandle, MemorySegment water) {
        try {
            check((int) library.waterSet.invokeExact(handle, waterHandle, water));
        } catch (Throwable throwable) {
            throw failure(throwable);
        }
    }

    public void rippleWater(long waterHandle, float x, float y, float z, float radius, float depth) {
        try {
            library.waterRipple.invokeExact(handle, waterHandle, x, y, z, radius, depth);
        } catch (Throwable throwable) {
            throw failure(throwable);
        }
    }

    public void destroyWater(long waterHandle) {
        try {
            library.waterDestroy.invokeExact(handle, waterHandle);
        } catch (Throwable throwable) {
            throw failure(throwable);
        }
    }

    public long createSky(MemorySegment sky) {
        try {
            return checkHandle((long) library.skyCreate.invokeExact(handle, sky));
        } catch (Throwable throwable) {
            throw failure(throwable);
        }
    }

    public void updateSky(long handleOfSky, MemorySegment sky) {
        try {
            library.skySet.invokeExact(handle, handleOfSky, sky);
        } catch (Throwable throwable) {
            throw failure(throwable);
        }
    }

    public void destroySky(long sky) {
        try {
            library.skyDestroy.invokeExact(handle, sky);
        } catch (Throwable throwable) {
            throw failure(throwable);
        }
    }

    public void sunOfSky(MemorySegment sky, MemorySegment sun) {
        try {
            library.skySun.invokeExact(sky, sun);
        } catch (Throwable throwable) {
            throw failure(throwable);
        }
    }

    public MemorySegment createImage(Arena owner, int width, int height, MemorySegment pixels, boolean srgb) {
        return createImage(owner, width, height, pixels, srgb, 0);
    }

    public MemorySegment createImage(Arena owner, int width, int height, MemorySegment pixels, boolean srgb, int mipLevels) {
        try {
            MemorySegment image = owner.allocate(LimnAbi.Image.SIZE, 4);
            check((int) library.imageCreate.invokeExact(handle, width, height, pixels, srgb ? 1 : 0, mipLevels, image));
            return image;
        } catch (Throwable throwable) {
            throw failure(throwable);
        }
    }

    public void updateImage(MemorySegment image, int x, int y, int width, int height, MemorySegment pixels) {
        try {
            check((int) library.imageUpdate.invokeExact(handle, image, x, y, width, height, pixels));
        } catch (Throwable throwable) {
            throw failure(throwable);
        }
    }

    public void destroyImage(MemorySegment image) {
        try {
            library.imageDestroy.invokeExact(handle, image);
        } catch (Throwable throwable) {
            throw failure(throwable);
        }
    }

    public long createModel(MemorySegment meshes, int count) {
        try {
            return checkHandle((long) library.modelCreate.invokeExact(handle, meshes, count));
        } catch (Throwable throwable) {
            throw failure(throwable);
        }
    }

    public void destroyModel(long model) {
        try {
            check((int) library.modelDestroy.invokeExact(handle, model));
        } catch (Throwable throwable) {
            throw failure(throwable);
        }
    }

    public void setModelTextures(long model, int material, MemorySegment textures) {
        try {
            check((int) library.modelSetTextures.invokeExact(handle, model, material, textures));
        } catch (Throwable throwable) {
            throw failure(throwable);
        }
    }

    public long spawn(long scene, long model, MemorySegment transform) {
        try {
            return checkHandle((long) library.entitySpawn.invokeExact(handle, scene, model, transform));
        } catch (Throwable throwable) {
            throw failure(throwable);
        }
    }

    public void despawn(long entity) {
        try {
            library.entityDespawn.invokeExact(handle, entity);
        } catch (Throwable throwable) {
            throw failure(throwable);
        }
    }

    public boolean modelTraced(long model) {
        try {
            return (int) library.modelTraced.invokeExact(handle, model) != 0;
        } catch (Throwable throwable) {
            throw failure(throwable);
        }
    }

    public void setVisible(long entity, boolean visible) {
        try {
            library.entitySetVisible.invokeExact(handle, entity, visible ? 1 : 0);
        } catch (Throwable throwable) {
            throw failure(throwable);
        }
    }

    public void setTint(long entity, float red, float green, float blue) {
        try {
            library.entitySetTint.invokeExact(handle, entity, red, green, blue);
        } catch (Throwable throwable) {
            throw failure(throwable);
        }
    }

    public void setTransforms(MemorySegment entities, MemorySegment transforms, int count) {
        try {
            library.entitySetTransforms.invokeExact(handle, entities, transforms, count);
        } catch (Throwable throwable) {
            throw failure(throwable);
        }
    }

    public void render(MemorySegment frame) {
        try {
            check((int) library.render.invokeExact(handle, frame));
        } catch (Throwable throwable) {
            throw failure(throwable);
        }
    }

    public void readPixels(MemorySegment pixels) {
        try {
            check((int) library.readPixels.invokeExact(handle, pixels, pixels.byteSize()));
        } catch (Throwable throwable) {
            throw failure(throwable);
        }
    }

    public SharedImages sharedImages() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment images = arena.allocate(LimnAbi.SharedImages.SIZE, 8);
            if ((int) library.sharedImages.invokeExact(handle, images) != 0) {
                return null;
            }
            return new SharedImages(
                images.get(JAVA_LONG, LimnAbi.SharedImages.COLOR_HANDLE),
                images.get(JAVA_LONG, LimnAbi.SharedImages.DEPTH_HANDLE),
                images.get(JAVA_LONG, LimnAbi.SharedImages.COLOR_BYTES),
                images.get(JAVA_LONG, LimnAbi.SharedImages.DEPTH_BYTES),
                images.get(JAVA_INT, LimnAbi.SharedImages.WIDTH),
                images.get(JAVA_INT, LimnAbi.SharedImages.HEIGHT)
            );
        } catch (Throwable throwable) {
            throw failure(throwable);
        }
    }

    public int[] diagnostics() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment values = arena.allocate(JAVA_INT, 4);
            library.diagnostics.invokeExact(handle, values);
            return values.toArray(JAVA_INT);
        } catch (Throwable throwable) {
            throw failure(throwable);
        }
    }

    public int[] depthSize() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment size = arena.allocate(JAVA_INT, 2);
            library.depthSize.invokeExact(handle, size);
            return new int[] {size.getAtIndex(JAVA_INT, 0), size.getAtIndex(JAVA_INT, 1)};
        } catch (Throwable throwable) {
            throw failure(throwable);
        }
    }

    public void readDepth(MemorySegment depth) {
        try {
            check((int) library.readDepth.invokeExact(handle, depth, depth.byteSize() / Float.BYTES));
        } catch (Throwable throwable) {
            throw failure(throwable);
        }
    }

    @Override
    public void close() {
        try {
            library.destroy.invokeExact(handle);
        } catch (Throwable throwable) {
            throw failure(throwable);
        }
    }

    private void check(int status) {
        if (status != 0) {
            throw new LimnException("limn: " + library.lastError());
        }
    }

    private long checkHandle(long value) {
        if (value == 0) {
            throw new LimnException("limn: " + library.lastError());
        }
        return value;
    }

    private static LimnException failure(Throwable throwable) {
        return throwable instanceof LimnException exception ? exception : new LimnException("limn call failed", throwable);
    }
}
