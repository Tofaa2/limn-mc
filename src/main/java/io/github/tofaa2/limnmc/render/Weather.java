package io.github.tofaa2.limnmc.render;

import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import io.github.tofaa2.limnmc.nativeapi.LimnAbi;
import io.github.tofaa2.limnmc.nativeapi.LimnContext;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;

final class Weather {
    private static final float SPAWN_RADIUS = 18.0F;
    private static final float SPAWN_HEIGHT = 14.0F;
    private static final int CAPACITY = 24_000;

    private final MemorySegment emitter;
    private long handle;
    private Biome.Precipitation active = Biome.Precipitation.NONE;

    Weather(Arena arena) {
        this.emitter = arena.allocate(LimnAbi.Emitter.SIZE, 4);
    }

    boolean replacesVanilla() {
        return handle != 0;
    }

    void update(LimnContext context, long scene, Vec3 camera, BlockPos cameraBlock, float intensity, int originX, int originY, int originZ) {
        ClientLevel level = Minecraft.getInstance().level;
        Biome.Precipitation wanted = Biome.Precipitation.NONE;
        if (LimnSettings.volumetricParticles && level != null && intensity > 0.01F && level.getBrightness(LightLayer.SKY, cameraBlock) > 0) {
            wanted = level.getBiome(cameraBlock).value().getPrecipitationAt(cameraBlock, level.getSeaLevel());
        }
        if (wanted != active && handle != 0) {
            context.destroyEmitter(handle);
            handle = 0;
        }
        active = wanted;
        if (wanted == Biome.Precipitation.NONE) {
            return;
        }
        boolean snow = wanted == Biome.Precipitation.SNOW;
        set(LimnAbi.Emitter.POSITION, (float) camera.x - originX, (float) camera.y + SPAWN_HEIGHT - originY, (float) camera.z - originZ);
        emitter.set(JAVA_FLOAT, LimnAbi.Emitter.RADIUS, SPAWN_RADIUS);
        emitter.set(JAVA_FLOAT, LimnAbi.Emitter.RATE, intensity * (snow ? 2500.0F : 9000.0F));
        set(LimnAbi.Emitter.LIFETIME, snow ? 8.0F : 1.6F, snow ? 12.0F : 2.4F);
        set(LimnAbi.Emitter.DIRECTION, 0.05F, -1.0F, 0.02F);
        emitter.set(JAVA_FLOAT, LimnAbi.Emitter.SPREAD, snow ? 0.5F : 0.03F);
        set(LimnAbi.Emitter.SPEED, snow ? 1.2F : 20.0F, snow ? 2.4F : 26.0F);
        set(LimnAbi.Emitter.GRAVITY, 0.0F, snow ? -0.4F : -9.0F, 0.0F);
        emitter.set(JAVA_FLOAT, LimnAbi.Emitter.DRAG, snow ? 0.4F : 0.0F);
        set(LimnAbi.Emitter.PARTICLE_SIZE, snow ? 0.07F : 0.025F, snow ? 0.07F : 0.025F);
        if (snow) {
            set(LimnAbi.Emitter.COLOR_START, 1.0F, 1.0F, 1.0F, 0.95F);
            set(LimnAbi.Emitter.COLOR_END, 1.0F, 1.0F, 1.0F, 0.0F);
        } else {
            set(LimnAbi.Emitter.COLOR_START, 0.62F, 0.7F, 0.82F, 0.55F);
            set(LimnAbi.Emitter.COLOR_END, 0.62F, 0.7F, 0.82F, 0.35F);
        }
        emitter.set(JAVA_FLOAT, LimnAbi.Emitter.STRETCH, snow ? 0.0F : 0.03F);
        emitter.set(JAVA_FLOAT, LimnAbi.Emitter.BOUNCE, snow ? 0.0F : 0.12F);
        emitter.set(JAVA_INT, LimnAbi.Emitter.CAPACITY, CAPACITY);
        emitter.set(JAVA_INT, LimnAbi.Emitter.FLAGS, LimnAbi.Emitter.LIT | LimnAbi.Emitter.COLLIDE);
        if (handle == 0) {
            handle = context.createEmitter(scene, emitter);
        } else {
            context.updateEmitter(handle, emitter);
        }
    }

    private void set(long offset, float... values) {
        for (int index = 0; index < values.length; index++) {
            emitter.set(JAVA_FLOAT, offset + index * 4L, values[index]);
        }
    }
}
