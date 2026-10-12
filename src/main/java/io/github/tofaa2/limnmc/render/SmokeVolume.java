package io.github.tofaa2.limnmc.render;

import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import io.github.tofaa2.limnmc.nativeapi.LimnAbi;
import io.github.tofaa2.limnmc.nativeapi.LimnContext;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Arrays;
import net.minecraft.world.phys.Vec3;

final class SmokeVolume {
    static final float CAMPFIRE = 0.0F;
    static final float FIRE = 1.0F;
    static final float PUFF = 2.0F;
    static final float BLAST = 3.0F;

    private static final float WIDTH = 16.0F;
    private static final float HEIGHT = 24.0F;
    private static final float FLOOR_BELOW_ANCHOR = 1.0F;
    private static final float REACH = 48.0F;
    private static final int PUFF_FRAMES = 5;
    private static final int IDLE_FRAMES_KEPT = 900;

    private final MemorySegment fluid;
    private final FloatArrayList puffs = new FloatArrayList();
    private long handle;
    private float anchorX;
    private float anchorY;
    private float anchorZ;
    private long lastKey;
    private int idleFrames;
    private float cameraX;
    private float cameraY;
    private float cameraZ;

    SmokeVolume(Arena arena) {
        this.fluid = arena.allocate(LimnAbi.Fluid.SIZE, 4);
    }

    boolean addPuff(float x, float y, float z, float kind) {
        if (!LimnSettings.volumetricParticles) {
            return false;
        }
        float fromCameraX = x - cameraX;
        float fromCameraY = y - cameraY;
        float fromCameraZ = z - cameraZ;
        if (fromCameraX * fromCameraX + fromCameraY * fromCameraY + fromCameraZ * fromCameraZ > REACH * REACH) {
            return false;
        }
        if (handle != 0 && !inside(x - anchorX, y - anchorY, z - anchorZ)) {
            return false;
        }
        puffs.add(x);
        puffs.add(y);
        puffs.add(z);
        puffs.add(kind);
        puffs.add(PUFF_FRAMES);
        return true;
    }

    void update(LimnContext context, long scene, Iterable<LimnSectionMesh> meshes, Vec3 camera, int originX, int originY, int originZ, boolean obstacles) {
        cameraX = (float) camera.x;
        cameraY = (float) camera.y;
        cameraZ = (float) camera.z;
        boolean simulated = LimnSettings.volumetricParticles;
        float[] emitters = simulated ? gather(meshes) : BlockLights.NONE;
        int count = emitters.length / BlockLights.SMOKE_STRIDE;
        if (count == 0) {
            idleFrames++;
            if (handle != 0 && (idleFrames > IDLE_FRAMES_KEPT || !simulated || !withinReach(anchorX, anchorY, anchorZ, 1.5F))) {
                destroy(context);
            }
            if (handle == 0) {
                return;
            }
        } else {
            idleFrames = 0;
        }
        if (handle == 0 || !anchorValid(emitters, count)) {
            destroy(context);
            int nearest = nearest(emitters, count, cameraX, cameraY, cameraZ);
            if (count == 0 || distanceSquared(emitters, nearest, cameraX, cameraY, cameraZ) > REACH * REACH) {
                return;
            }
            anchorX = (float) Math.floor(emitters[nearest * BlockLights.SMOKE_STRIDE]) + 0.5F;
            anchorY = (float) Math.floor(emitters[nearest * BlockLights.SMOKE_STRIDE + 1]) + 0.5F;
            anchorZ = (float) Math.floor(emitters[nearest * BlockLights.SMOKE_STRIDE + 2]) + 0.5F;
        }
        long[] order = new long[count];
        int inside = 0;
        for (int index = 0; index < count; index++) {
            int at = index * BlockLights.SMOKE_STRIDE;
            float x = emitters[at] - anchorX;
            float y = emitters[at + 1] - anchorY;
            float z = emitters[at + 2] - anchorZ;
            if (inside(x, y, z)) {
                boolean brief = emitters[at + 3] >= PUFF;
                order[inside++] = (long) Float.floatToIntBits(x * x + y * y + z * z + (brief ? 0.0F : 4096.0F)) << 32 | index;
            }
        }
        Arrays.sort(order, 0, inside);
        int used = Math.min(inside, LimnAbi.Fluid.MAX_SOURCES);
        long key = used * 31L + originX * 17L + originY * 13L + originZ * 7L + (obstacles ? 1 : 0);
        for (int slot = 0; slot < used; slot++) {
            int at = (int) order[slot] * BlockLights.SMOKE_STRIDE;
            float kind = emitters[at + 3];
            MemorySegment source = fluid.asSlice(LimnAbi.Fluid.SOURCES + (long) slot * LimnAbi.FluidSource.SIZE, LimnAbi.FluidSource.SIZE);
            source.set(JAVA_FLOAT, LimnAbi.FluidSource.POSITION, (emitters[at] - anchorX) / WIDTH + 0.5F);
            source.set(JAVA_FLOAT, LimnAbi.FluidSource.POSITION + 4, (emitters[at + 1] - anchorY + FLOOR_BELOW_ANCHOR) / HEIGHT);
            source.set(JAVA_FLOAT, LimnAbi.FluidSource.POSITION + 8, (emitters[at + 2] - anchorZ) / WIDTH + 0.5F);
            source.set(JAVA_FLOAT, LimnAbi.FluidSource.RADIUS, kind == BLAST ? 0.05F : kind == PUFF ? 0.01F : 0.025F);
            source.set(JAVA_FLOAT, LimnAbi.FluidSource.VELOCITY, 0.0F);
            source.set(JAVA_FLOAT, LimnAbi.FluidSource.VELOCITY + 4, kind == PUFF ? 0.05F : 0.14F);
            source.set(JAVA_FLOAT, LimnAbi.FluidSource.VELOCITY + 8, 0.0F);
            source.set(JAVA_FLOAT, LimnAbi.FluidSource.SMOKE, kind == BLAST ? 8.0F : kind == PUFF ? 1.5F : kind == FIRE ? 1.0F : 2.4F);
            source.set(JAVA_FLOAT, LimnAbi.FluidSource.FUEL, kind == FIRE ? 2.5F : 0.0F);
            source.set(JAVA_FLOAT, LimnAbi.FluidSource.TEMPERATURE, kind == BLAST ? 0.9F : kind == PUFF ? 0.6F : kind == FIRE ? 1.2F : 1.6F);
            key = key * 31 + Float.floatToIntBits(emitters[at] + emitters[at + 1] * 3.0F + emitters[at + 2] * 7.0F + kind);
        }
        if (handle != 0 && key == lastKey) {
            return;
        }
        lastKey = key;
        for (int index = 0; index < 16; index++) {
            fluid.setAtIndex(JAVA_FLOAT, index, 0.0F);
        }
        fluid.setAtIndex(JAVA_FLOAT, 0, WIDTH);
        fluid.setAtIndex(JAVA_FLOAT, 5, HEIGHT);
        fluid.setAtIndex(JAVA_FLOAT, 10, WIDTH);
        fluid.setAtIndex(JAVA_FLOAT, 12, anchorX - originX);
        fluid.setAtIndex(JAVA_FLOAT, 13, anchorY - FLOOR_BELOW_ANCHOR + HEIGHT * 0.5F - originY);
        fluid.setAtIndex(JAVA_FLOAT, 14, anchorZ - originZ);
        fluid.setAtIndex(JAVA_FLOAT, 15, 1.0F);
        fluid.set(JAVA_INT, LimnAbi.Fluid.SOURCE_COUNT, used);
        fluid.set(JAVA_INT, LimnAbi.Fluid.RESOLUTION, 64);
        fluid.set(JAVA_INT, LimnAbi.Fluid.RESOLUTION + 4, 96);
        fluid.set(JAVA_INT, LimnAbi.Fluid.RESOLUTION + 8, 64);
        fluid.set(JAVA_INT, LimnAbi.Fluid.FLAGS, obstacles ? LimnAbi.Fluid.SCENE_OBSTACLES : 0);
        if (handle == 0) {
            handle = context.createFluid(scene, fluid);
        } else {
            context.updateFluid(handle, fluid);
        }
    }

    private void destroy(LimnContext context) {
        if (handle != 0) {
            context.destroyFluid(handle);
            handle = 0;
        }
    }

    private static boolean inside(float x, float y, float z) {
        return Math.abs(x) < WIDTH * 0.45F && Math.abs(z) < WIDTH * 0.45F && y > -FLOOR_BELOW_ANCHOR && y < HEIGHT * 0.5F;
    }

    private boolean withinReach(float x, float y, float z, float slack) {
        float dx = cameraX - x;
        float dy = cameraY - y;
        float dz = cameraZ - z;
        return dx * dx + dy * dy + dz * dz <= REACH * REACH * slack;
    }

    private boolean anchorValid(float[] emitters, int count) {
        if (!withinReach(anchorX, anchorY, anchorZ, 1.5F)) {
            return false;
        }
        for (int index = 0; index < count; index++) {
            int at = index * BlockLights.SMOKE_STRIDE;
            if (inside(emitters[at] - anchorX, emitters[at + 1] - anchorY, emitters[at + 2] - anchorZ)) {
                return true;
            }
        }
        return count == 0;
    }

    private static int nearest(float[] emitters, int count, float x, float y, float z) {
        int best = 0;
        float bestDistance = Float.MAX_VALUE;
        for (int index = 0; index < count; index++) {
            float distance = distanceSquared(emitters, index, x, y, z);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = index;
            }
        }
        return best;
    }

    private static float distanceSquared(float[] emitters, int index, float x, float y, float z) {
        int at = index * BlockLights.SMOKE_STRIDE;
        float dx = emitters[at] - x;
        float dy = emitters[at + 1] - y;
        float dz = emitters[at + 2] - z;
        return dx * dx + dy * dy + dz * dz;
    }

    private float[] gather(Iterable<LimnSectionMesh> meshes) {
        FloatArrayList all = new FloatArrayList();
        for (LimnSectionMesh mesh : meshes) {
            all.addElements(all.size(), mesh.limn$emitters().smoke());
        }
        int kept = 0;
        for (int at = 0; at + 5 <= puffs.size(); at += 5) {
            all.add(puffs.getFloat(at));
            all.add(puffs.getFloat(at + 1));
            all.add(puffs.getFloat(at + 2));
            all.add(puffs.getFloat(at + 3));
            float frames = puffs.getFloat(at + 4) - 1.0F;
            if (frames > 0.0F) {
                for (int field = 0; field < 4; field++) {
                    puffs.set(kept + field, puffs.getFloat(at + field));
                }
                puffs.set(kept + 4, frames);
                kept += 5;
            }
        }
        puffs.size(kept);
        return all.toFloatArray();
    }
}
