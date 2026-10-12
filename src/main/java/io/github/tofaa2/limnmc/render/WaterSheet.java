package io.github.tofaa2.limnmc.render;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import io.github.tofaa2.limnmc.nativeapi.LimnAbi;
import io.github.tofaa2.limnmc.nativeapi.LimnContext;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.phys.Vec3;

final class WaterSheet {
    private static final int SIZE = 512;
    private static final int SIMULATION_CELLS = 256;
    private static final int RECENTER_DISTANCE = 64;
    private static final int ROWS_PER_FRAME = 32;
    private static final int WATER = 0xFFFFFFFF;

    private final Arena arena;
    private final MemorySegment water;
    private final MemorySegment mask;
    private MemorySegment maskImage;
    private long handle;
    private int minX;
    private int minZ;
    private int seaLevel;
    private int nextRow;
    private float rain = -1.0F;
    private long lastKey = Long.MIN_VALUE;
    private int frame;

    WaterSheet(Arena arena) {
        this.arena = arena;
        this.water = arena.allocate(LimnAbi.Water.SIZE, 8);
        this.mask = arena.allocate((long) SIZE * SIZE * 4, 4);
    }

    static float surfaceHeight(int seaLevel) {
        return seaLevel - 1 + 8.0F / 9.0F;
    }

    boolean covers(long sectionNode) {
        if (handle == 0) {
            return false;
        }
        int x = SectionPos.sectionToBlockCoord(SectionPos.x(sectionNode));
        int z = SectionPos.sectionToBlockCoord(SectionPos.z(sectionNode));
        return x >= minX && x + 16 <= minX + SIZE && z >= minZ && z + 16 <= minZ + SIZE;
    }

    void update(LimnContext context, long scene, Vec3 camera, float rainIntensity, int originX, int originY, int originZ) {
        ClientLevel level = Minecraft.getInstance().level;
        if (!LimnSettings.volumetricWater || level == null) {
            if (handle != 0) {
                context.destroyWater(handle);
                handle = 0;
            }
            return;
        }
        frame++;
        int centerX = minX + SIZE / 2;
        int centerZ = minZ + SIZE / 2;
        boolean moved = handle == 0 || Math.abs(camera.x - centerX) > RECENTER_DISTANCE || Math.abs(camera.z - centerZ) > RECENTER_DISTANCE
            || seaLevel != level.getSeaLevel();
        if (moved) {
            minX = (((int) Math.floor(camera.x)) & ~15) - SIZE / 2;
            minZ = (((int) Math.floor(camera.z)) & ~15) - SIZE / 2;
            seaLevel = level.getSeaLevel();
            refreshRows(context, level, 0, SIZE);
            nextRow = 0;
        } else {
            refreshRows(context, level, nextRow, ROWS_PER_FRAME);
            nextRow = (nextRow + ROWS_PER_FRAME) % SIZE;
        }
        long key = minX * 31L + minZ * 17L + originX * 13L + originY * 7L + originZ * 3L + Float.floatToIntBits(rainIntensity);
        if (handle != 0 && key == lastKey) {
            ripple(context, originX, originY, originZ);
            return;
        }
        lastKey = key;
        for (int index = 0; index < 16; index++) {
            water.setAtIndex(JAVA_FLOAT, index, 0.0F);
        }
        water.setAtIndex(JAVA_FLOAT, 0, SIZE);
        water.setAtIndex(JAVA_FLOAT, 5, 1.0F);
        water.setAtIndex(JAVA_FLOAT, 10, SIZE);
        water.setAtIndex(JAVA_FLOAT, 12, minX + SIZE / 2 - originX);
        water.setAtIndex(JAVA_FLOAT, 13, surfaceHeight(seaLevel) - originY);
        water.setAtIndex(JAVA_FLOAT, 14, minZ + SIZE / 2 - originZ);
        water.setAtIndex(JAVA_FLOAT, 15, 1.0F);
        water.set(JAVA_FLOAT, LimnAbi.Water.COLOR, 0.03F);
        water.set(JAVA_FLOAT, LimnAbi.Water.COLOR + 4, 0.13F);
        water.set(JAVA_FLOAT, LimnAbi.Water.COLOR + 8, 0.22F);
        water.set(JAVA_FLOAT, LimnAbi.Water.MURK, 0.22F);
        water.set(JAVA_FLOAT, LimnAbi.Water.ROUGHNESS, 0.04F);
        water.set(JAVA_FLOAT, LimnAbi.Water.REFRACTION, 1.0F);
        water.set(JAVA_FLOAT, LimnAbi.Water.FOAM, 0.35F);
        water.set(JAVA_FLOAT, LimnAbi.Water.RIPPLE_DETAIL, 0.45F);
        water.set(JAVA_FLOAT, LimnAbi.Water.WAVE_SPEED, 2.5F);
        water.set(JAVA_FLOAT, LimnAbi.Water.DAMPING, 0.6F);
        water.set(JAVA_FLOAT, LimnAbi.Water.RAIN, rainIntensity * 80.0F);
        water.set(JAVA_FLOAT, LimnAbi.Water.SWELL, 0.05F);
        water.set(JAVA_FLOAT, LimnAbi.Water.SWELL_LENGTH, 9.0F);
        water.set(JAVA_INT, LimnAbi.Water.RESOLUTION, SIMULATION_CELLS);
        water.set(JAVA_INT, LimnAbi.Water.RESOLUTION + 4, SIMULATION_CELLS);
        water.set(ADDRESS, LimnAbi.Water.MASK, maskImage);
        water.set(JAVA_INT, LimnAbi.Water.FLAGS, 0);
        if (handle == 0) {
            handle = context.createWater(scene, water);
        } else {
            context.updateWater(handle, water);
        }
    }

    void hideCovered(LimnContext context, Map<LimnSectionMesh, Long> seaSurfaces, Map<LimnSectionMesh, Boolean> hidden) {
        for (Map.Entry<LimnSectionMesh, Long> entry : seaSurfaces.entrySet()) {
            LimnSectionMesh mesh = entry.getKey();
            long entity = mesh.limn$seaEntity();
            if (entity == 0) {
                continue;
            }
            boolean hide = covers(entry.getValue());
            Boolean previous = hidden.put(mesh, hide);
            if (previous == null ? hide : previous != hide) {
                context.setVisible(entity, !hide);
            }
        }
    }

    private void refreshRows(LimnContext context, ClientLevel level, int firstRow, int rows) {
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        boolean changed = maskImage == null;
        for (int row = firstRow; row < firstRow + rows; row++) {
            for (int column = 0; column < SIZE; column++) {
                position.set(minX + column, seaLevel - 1, minZ + row);
                boolean surface = level.getFluidState(position).is(FluidTags.WATER)
                    && level.getFluidState(position.move(0, 1, 0)).isEmpty()
                    && !level.getBlockState(position).isSolidRender();
                int value = surface ? WATER : 0xFF000000;
                long at = ((long) row * SIZE + column) * 4;
                if (mask.get(JAVA_INT, at) != value) {
                    mask.set(JAVA_INT, at, value);
                    changed = true;
                }
            }
        }
        if (maskImage == null) {
            maskImage = context.createImage(arena, SIZE, SIZE, mask, false);
        } else if (changed) {
            context.updateImage(maskImage, 0, firstRow, SIZE, rows, mask.asSlice((long) firstRow * SIZE * 4, (long) rows * SIZE * 4));
        }
    }

    private void ripple(LimnContext context, int originX, int originY, int originZ) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || frame % 4 != 0 || !player.isInWater() || player.getDeltaMovement().lengthSqr() < 1.0e-4) {
            return;
        }
        float surface = surfaceHeight(seaLevel);
        if (Math.abs(player.getY() + 0.9 - surface) > 1.4) {
            return;
        }
        context.rippleWater(
            handle, (float) player.getX() - originX, surface - originY, (float) player.getZ() - originZ, 0.7F, 0.06F
        );
    }
}
