package io.github.tofaa2.limnmc.render;

import it.unimi.dsi.fastutil.floats.FloatArrayList;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class BlockLights {
    public static final int STRIDE = 8;
    public static final float[] NONE = new float[0];

    public static final int SMOKE_STRIDE = 4;

    private static final float POOL_HEIGHT = 1.6F;
    private static final float POOL_STRENGTH = 3.0F;
    private static final float POOL_REACH = 13.0F;

    public record Emitters(float[] lights, float[] smoke) {
        public static final Emitters NONE = new Emitters(BlockLights.NONE, BlockLights.NONE);
    }

    public static Emitters collect(SectionPos section, RenderSectionRegion region) {
        FloatArrayList smoke = null;
        FloatArrayList lights = null;
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        int originX = section.minBlockX();
        int originY = section.minBlockY();
        int originZ = section.minBlockZ();
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    BlockState state = region.getBlockState(position.set(originX + x, originY + y, originZ + z));
                    boolean campfire = state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT);
                    if (campfire || state.getBlock() instanceof BaseFireBlock) {
                        if (smoke == null) {
                            smoke = new FloatArrayList();
                        }
                        smoke.add(originX + x + 0.5F);
                        smoke.add(originY + y + 0.5F);
                        smoke.add(originZ + z + 0.5F);
                        smoke.add(campfire ? 0.0F : 1.0F);
                    }
                    int emission = state.getLightEmission();
                    if (emission <= 0) {
                        continue;
                    }
                    boolean pool = !state.getFluidState().isEmpty();
                    if (pool && ((x & 3) != 0 || (z & 3) != 0 || !region.getBlockState(position.move(0, 1, 0)).isAir())) {
                        continue;
                    }
                    if (lights == null) {
                        lights = new FloatArrayList();
                    }
                    String name = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
                    lights.add(originX + x + 0.5F);
                    lights.add(originY + y + (pool ? POOL_HEIGHT : 0.6F));
                    lights.add(originZ + z + 0.5F);
                    addColor(lights, name);
                    lights.add(emission / 15.0F * (pool ? POOL_STRENGTH : 1.0F));
                    lights.add(emission + (pool ? POOL_REACH : 1.0F));
                }
            }
        }
        if (lights == null && smoke == null) {
            return Emitters.NONE;
        }
        return new Emitters(lights == null ? NONE : lights.toFloatArray(), smoke == null ? NONE : smoke.toFloatArray());
    }

    private static void addColor(FloatArrayList lights, String name) {
        if (name.contains("soul")) {
            add(lights, 0.3F, 0.8F, 1.0F);
        } else if (name.contains("redstone")) {
            add(lights, 1.0F, 0.15F, 0.1F);
        } else if (name.contains("sea_lantern") || name.contains("beacon") || name.contains("end_rod") || name.contains("conduit")) {
            add(lights, 0.85F, 0.95F, 1.0F);
        } else if (name.contains("portal") || name.contains("amethyst") || name.contains("crying")) {
            add(lights, 0.6F, 0.3F, 1.0F);
        } else if (name.contains("sculk") || name.contains("lichen")) {
            add(lights, 0.2F, 0.8F, 0.7F);
        } else if (name.contains("lava") || name.contains("magma") || name.contains("fire")) {
            add(lights, 1.0F, 0.45F, 0.12F);
        } else {
            add(lights, 1.0F, 0.78F, 0.5F);
        }
    }

    private static void add(FloatArrayList lights, float red, float green, float blue) {
        lights.add(red);
        lights.add(green);
        lights.add(blue);
    }

    private BlockLights() {
    }
}
