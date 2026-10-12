package io.github.tofaa2.limnmc.render;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT_UNALIGNED;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import io.github.tofaa2.limnmc.nativeapi.LimnAbi;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.core.SectionPos;

public final class TerrainMeshes {
    private static final int POSITION_OFFSET = 0;
    private static final int COLOR_OFFSET = 12;
    private static final int UV_OFFSET = 16;
    private static final float EMISSIVE_STRENGTH = 6.0F;
    private static final float OVERLAY_LIFT = 0.004F;
    private static final float SWAY = 0.14F;
    private static final float LEAF_LEAN = 0.1F;
    private static final float LEAF_FLUTTER = 0.25F;
    private static final float GROUND_LEAN = 0.3F;
    private static final float[] LINEAR = new float[256];

    private enum Kind {
        SOLID, CUTOUT, TRANSLUCENT, WATER
    }

    private enum Quads {
        ALL, WATER, NOT_WATER, SEA
    }

    static {
        for (int value = 0; value < 256; value++) {
            LINEAR[value] = (float) Math.pow(value / 255.0, 2.2);
        }
    }

    public record Built(long model, long seaModel) {
        static final Built NONE = new Built(0, 0);
    }

    public static Built build(SectionCompiler.Results results, SectionPos section) {
        LimnWorldRenderer renderer = LimnWorldRenderer.active();
        if (renderer == null || results.renderedLayers.isEmpty()) {
            return Built.NONE;
        }
        BlockAtlasPixels atlas = BlockAtlasPixels.latest();
        boolean splitWater = LimnSettings.volumetricWater && atlas != null;
        ClientLevel level = Minecraft.getInstance().level;
        float seaSurface = level == null ? Float.NaN : WaterSheet.surfaceHeight(level.getSeaLevel()) - section.minBlockY();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment meshes = arena.allocate((long) LimnAbi.Mesh.SIZE * (results.renderedLayers.size() + 1), 8);
            MemorySegment seaMeshes = arena.allocate(LimnAbi.Mesh.SIZE, 8);
            int count = 0;
            int seaCount = 0;
            for (Map.Entry<ChunkSectionLayer, MeshData> entry : results.renderedLayers.entrySet()) {
                MeshData data = entry.getValue();
                MeshData.DrawState state = data.drawState();
                if (state.vertexCount() < 4 || state.primitiveTopology() != PrimitiveTopology.QUADS) {
                    continue;
                }
                switch (entry.getKey()) {
                    case SOLID -> count += writeMesh(arena, meshes, count, data, atlas, Quads.ALL, Kind.SOLID, seaSurface);
                    case CUTOUT -> count += writeMesh(arena, meshes, count, data, atlas, Quads.ALL, Kind.CUTOUT, seaSurface);
                    case TRANSLUCENT -> {
                        if (splitWater) {
                            count += writeMesh(arena, meshes, count, data, atlas, Quads.NOT_WATER, Kind.TRANSLUCENT, seaSurface);
                            count += writeMesh(arena, meshes, count, data, atlas, Quads.WATER, Kind.WATER, seaSurface);
                            seaCount += writeMesh(arena, seaMeshes, 0, data, atlas, Quads.SEA, Kind.WATER, seaSurface);
                        } else {
                            count += writeMesh(arena, meshes, count, data, atlas, Quads.ALL, Kind.TRANSLUCENT, seaSurface);
                        }
                    }
                }
            }
            return new Built(
                count == 0 ? 0 : renderer.createTerrainModel(meshes, count),
                seaCount == 0 ? 0 : renderer.createTerrainModel(seaMeshes, seaCount)
            );
        } catch (RuntimeException exception) {
            LimnWorldRenderer.fail(exception);
            return Built.NONE;
        }
    }

    private static int writeMesh(
        Arena arena, MemorySegment meshes, int index, MeshData data, BlockAtlasPixels atlas, Quads wanted, Kind kind, float seaSurface
    ) {
        MeshData.DrawState state = data.drawState();
        int stride = state.format().getVertexSize();
        int sourceQuads = state.vertexCount() / 4;
        MemorySegment source = MemorySegment.ofBuffer(data.vertexBuffer());
        MemorySegment positions = arena.allocate(sourceQuads * 48L, 4);
        MemorySegment uvs = arena.allocate(sourceQuads * 32L, 4);
        MemorySegment colors = arena.allocate(sourceQuads * 64L, 4);
        MemorySegment indices = arena.allocate(sourceQuads * 24L, 4);
        boolean canSway = LimnSettings.plantSway && atlas != null && (kind == Kind.SOLID || kind == Kind.CUTOUT);
        MemorySegment sways = canSway ? arena.allocate(sourceQuads * 32L, 4) : null;
        boolean swaying = false;
        int quads = 0;
        for (int quad = 0; quad < sourceQuads; quad++) {
            long first = (long) quad * 4 * stride;
            if (wanted != Quads.ALL) {
                float u = (source.get(JAVA_FLOAT_UNALIGNED, first + UV_OFFSET) + source.get(JAVA_FLOAT_UNALIGNED, first + 2L * stride + UV_OFFSET)) * 0.5F;
                float v = (source.get(JAVA_FLOAT_UNALIGNED, first + UV_OFFSET + 4) + source.get(JAVA_FLOAT_UNALIGNED, first + 2L * stride + UV_OFFSET + 4)) * 0.5F;
                boolean water = atlas.isWater(u, v);
                float y0 = source.get(JAVA_FLOAT_UNALIGNED, first + 4);
                float y1 = source.get(JAVA_FLOAT_UNALIGNED, first + stride + 4);
                float y2 = source.get(JAVA_FLOAT_UNALIGNED, first + 2L * stride + 4);
                boolean sea = water && Math.abs(y0 - seaSurface) < 0.02F && Math.abs(y1 - seaSurface) < 0.02F && Math.abs(y2 - seaSurface) < 0.02F;
                boolean keep = switch (wanted) {
                    case NOT_WATER -> !water;
                    case WATER -> water && !sea;
                    case SEA -> sea;
                    case ALL -> true;
                };
                if (!keep) {
                    continue;
                }
            }
            for (int corner = 0; corner < 4; corner++) {
                long from = first + (long) corner * stride;
                long vertex = quads * 4L + corner;
                MemorySegment.copy(source, from + POSITION_OFFSET, positions, vertex * 12, 12);
                MemorySegment.copy(source, from + UV_OFFSET, uvs, vertex * 8, 8);
                long color = vertex * 16;
                colors.set(JAVA_FLOAT, color, LINEAR[source.get(JAVA_BYTE, from + COLOR_OFFSET) & 0xFF]);
                colors.set(JAVA_FLOAT, color + 4, LINEAR[source.get(JAVA_BYTE, from + COLOR_OFFSET + 1) & 0xFF]);
                colors.set(JAVA_FLOAT, color + 8, LINEAR[source.get(JAVA_BYTE, from + COLOR_OFFSET + 2) & 0xFF]);
                colors.set(JAVA_FLOAT, color + 12, (source.get(JAVA_BYTE, from + COLOR_OFFSET + 3) & 0xFF) / 255.0F);
            }
            if (canSway) {
                swaying |= writeSway(sways, quads * 4L, source, first, stride, atlas);
            }
            if (kind == Kind.CUTOUT) {
                liftOverlay(positions, quads * 4L);
            }
            int base = quads * 4;
            long at = quads * 24L;
            indices.set(JAVA_INT, at, base);
            indices.set(JAVA_INT, at + 4, base + 1);
            indices.set(JAVA_INT, at + 8, base + 2);
            indices.set(JAVA_INT, at + 12, base);
            indices.set(JAVA_INT, at + 16, base + 2);
            indices.set(JAVA_INT, at + 20, base + 3);
            quads++;
        }
        if (quads == 0) {
            return 0;
        }
        MemorySegment mesh = meshes.asSlice((long) LimnAbi.Mesh.SIZE * index, LimnAbi.Mesh.SIZE);
        mesh.set(ADDRESS, LimnAbi.Mesh.POSITIONS, positions);
        mesh.set(ADDRESS, LimnAbi.Mesh.UVS, uvs);
        mesh.set(ADDRESS, LimnAbi.Mesh.COLORS, colors);
        if (swaying) {
            mesh.set(ADDRESS, LimnAbi.Mesh.UVS1, sways);
        }
        mesh.set(ADDRESS, LimnAbi.Mesh.INDICES, indices);
        mesh.set(JAVA_INT, LimnAbi.Mesh.VERTEX_COUNT, quads * 4);
        mesh.set(JAVA_INT, LimnAbi.Mesh.INDEX_COUNT, quads * 6);
        writeMaterial(mesh.asSlice(LimnAbi.Mesh.MATERIAL, LimnAbi.Material.SIZE), kind, LimnSettings.physicallyBasedSurfaces && atlas != null, swaying);
        return 1;
    }

    private static boolean writeSway(MemorySegment sways, long vertex, MemorySegment source, long first, int stride, BlockAtlasPixels atlas) {
        float u = (source.get(JAVA_FLOAT_UNALIGNED, first + UV_OFFSET) + source.get(JAVA_FLOAT_UNALIGNED, first + 2L * stride + UV_OFFSET)) * 0.5F;
        float v = (source.get(JAVA_FLOAT_UNALIGNED, first + UV_OFFSET + 4) + source.get(JAVA_FLOAT_UNALIGNED, first + 2L * stride + UV_OFFSET + 4)) * 0.5F;
        byte foliage = atlas.foliageAt(u, v);
        if (foliage == Foliage.NONE) {
            return false;
        }
        float lowest = Float.MAX_VALUE;
        for (int corner = 0; corner < 4; corner++) {
            lowest = Math.min(lowest, source.get(JAVA_FLOAT_UNALIGNED, first + (long) corner * stride + 4));
        }
        float ground = (float) Math.floor(lowest + 0.001F);
        for (int corner = 0; corner < 4; corner++) {
            float height = Math.max(source.get(JAVA_FLOAT_UNALIGNED, first + (long) corner * stride + 4) - ground, 0.0F);
            float lean = switch (foliage) {
                case Foliage.PLANT -> height * height;
                case Foliage.UPPER_PLANT -> 1.0F + height;
                case Foliage.GROUND -> GROUND_LEAN;
                default -> LEAF_LEAN;
            };
            sways.set(JAVA_FLOAT, (vertex + corner) * 8, lean);
            sways.set(JAVA_FLOAT, (vertex + corner) * 8 + 4, foliage == Foliage.LEAVES ? LEAF_FLUTTER : 0.0F);
        }
        return true;
    }

    private static void liftOverlay(MemorySegment positions, long vertex) {
        float[] corner = new float[12];
        for (int index = 0; index < 12; index++) {
            corner[index] = positions.get(JAVA_FLOAT, vertex * 12 + index * 4L);
        }
        float ax = corner[3] - corner[0];
        float ay = corner[4] - corner[1];
        float az = corner[5] - corner[2];
        float bx = corner[6] - corner[0];
        float by = corner[7] - corner[1];
        float bz = corner[8] - corner[2];
        float nx = ay * bz - az * by;
        float ny = az * bx - ax * bz;
        float nz = ax * by - ay * bx;
        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length < 1.0e-6F) {
            return;
        }
        float scale = OVERLAY_LIFT / length;
        for (int index = 0; index < 4; index++) {
            long at = (vertex + index) * 12;
            positions.set(JAVA_FLOAT, at, positions.get(JAVA_FLOAT, at) + nx * scale);
            positions.set(JAVA_FLOAT, at + 4, positions.get(JAVA_FLOAT, at + 4) + ny * scale);
            positions.set(JAVA_FLOAT, at + 8, positions.get(JAVA_FLOAT, at + 8) + nz * scale);
        }
    }

    private static void writeMaterial(MemorySegment material, Kind kind, boolean surfaceAtlas, boolean swaying) {
        for (int channel = 0; channel < 4; channel++) {
            material.set(JAVA_FLOAT, LimnAbi.Material.BASE_COLOR + channel * 4L, 1.0F);
        }
        float emissive = surfaceAtlas ? EMISSIVE_STRENGTH : 0.0F;
        for (int channel = 0; channel < 3; channel++) {
            material.set(JAVA_FLOAT, LimnAbi.Material.EMISSIVE + channel * 4L, emissive);
        }
        material.set(JAVA_FLOAT, LimnAbi.Material.METALLIC, surfaceAtlas ? 1.0F : 0.0F);
        material.set(JAVA_FLOAT, LimnAbi.Material.ALPHA_CUTOFF, 0.5F);
        material.set(JAVA_FLOAT, LimnAbi.Material.IOR, 1.33F);
        material.set(JAVA_FLOAT, LimnAbi.Material.SWAY, swaying ? SWAY : 0.0F);
        float roughness = switch (kind) {
            case SOLID, CUTOUT -> 0.9F;
            case TRANSLUCENT -> 0.1F;
            case WATER -> 0.04F;
        };
        material.set(JAVA_FLOAT, LimnAbi.Material.ROUGHNESS, surfaceAtlas ? 1.0F : roughness);
        switch (kind) {
            case SOLID -> material.set(JAVA_INT, LimnAbi.Material.ALPHA_MODE, LimnAbi.Material.ALPHA_OPAQUE);
            case CUTOUT -> {
                material.set(JAVA_INT, LimnAbi.Material.ALPHA_MODE, LimnAbi.Material.ALPHA_MASK);
            }
            case TRANSLUCENT -> {
                material.set(JAVA_INT, LimnAbi.Material.ALPHA_MODE, LimnAbi.Material.ALPHA_BLEND);
                material.set(JAVA_INT, LimnAbi.Material.FLAGS, LimnAbi.Material.DOUBLE_SIDED);
            }
            case WATER -> {
                material.set(JAVA_INT, LimnAbi.Material.ALPHA_MODE, LimnAbi.Material.ALPHA_BLEND);
                material.set(JAVA_INT, LimnAbi.Material.FLAGS, LimnAbi.Material.DOUBLE_SIDED);
                material.set(JAVA_FLOAT, LimnAbi.Material.TRANSMISSION, 0.85F);
            }
        }
    }

    private TerrainMeshes() {
    }
}
