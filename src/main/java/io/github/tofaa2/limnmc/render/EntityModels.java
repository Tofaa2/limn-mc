package io.github.tofaa2.limnmc.render;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_INT_UNALIGNED;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.tofaa2.limnmc.mixin.ModelPartAccessor;
import io.github.tofaa2.limnmc.mixin.RenderSetupAccessor;
import io.github.tofaa2.limnmc.mixin.RenderTypeAccessor;
import io.github.tofaa2.limnmc.nativeapi.LimnAbi;
import io.github.tofaa2.limnmc.nativeapi.LimnContext;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.joml.Matrix4f;

public final class EntityModels {
    private static final int UNUSED_FRAMES_KEPT = 600;
    private static final int HURT_OVERLAY_ROW = 3;
    private static final float OVERLAY_LIFT = 0.004F;
    private static final float[] MATRIX = new float[16];

    private static final class Part {
        long model;
        final LongArrayList entities = new LongArrayList();
        final IntArrayList tints = new IntArrayList();
        final FloatArrayList transforms = new FloatArrayList();
        final IntArrayList wantedTints = new IntArrayList();
        int visible;
        long lastUsedFrame;
    }

    private record Texture(MemorySegment image, boolean[] opaque, int width, int height) {
        boolean shows(ModelPart.Polygon polygon) {
            return covered(polygon, true);
        }

        boolean hasGaps(ModelPart.Polygon polygon) {
            return covered(polygon, false);
        }

        private boolean covered(ModelPart.Polygon polygon, boolean wanted) {
            float minU = 1.0F;
            float minV = 1.0F;
            float maxU = 0.0F;
            float maxV = 0.0F;
            for (ModelPart.Vertex vertex : polygon.vertices()) {
                minU = Math.min(minU, vertex.u());
                minV = Math.min(minV, vertex.v());
                maxU = Math.max(maxU, vertex.u());
                maxV = Math.max(maxV, vertex.v());
            }
            int fromX = Math.clamp((int) Math.floor(minU * width), 0, width - 1);
            int fromY = Math.clamp((int) Math.floor(minV * height), 0, height - 1);
            int toX = Math.clamp((int) Math.ceil(maxU * width), fromX + 1, width);
            int toY = Math.clamp((int) Math.ceil(maxV * height), fromY + 1, height);
            for (int y = fromY; y < toY; y++) {
                for (int x = fromX; x < toX; x++) {
                    if (opaque[y * width + x] == wanted) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    private final LimnContext context;
    private final long scene;
    private final Arena arena;
    private final Map<ModelPart.Cube, Map<Identifier, Part>> parts = new IdentityHashMap<>();
    private final Map<Identifier, Texture> textures = new HashMap<>();
    private final PoseStack poseStack = new PoseStack();
    private double cameraX;
    private double cameraY;
    private double cameraZ;
    private long frame;

    EntityModels(LimnContext context, long scene, Arena arena) {
        this.context = context;
        this.scene = scene;
        this.arena = arena;
    }

    public static <S> boolean capture(ModelFeatureRenderer.Submit<S> submit) {
        LimnWorldRenderer renderer = LimnWorldRenderer.active();
        if (renderer == null || !LimnSettings.limnEntities) {
            return false;
        }
        try {
            return renderer.entities().add(submit);
        } catch (RuntimeException exception) {
            LimnWorldRenderer.fail(exception);
            return false;
        }
    }

    void begin(double x, double y, double z) {
        cameraX = x;
        cameraY = y;
        cameraZ = z;
        frame++;
    }

    private <S> boolean add(ModelFeatureRenderer.Submit<S> submit) {
        RenderType type = submit.renderType();
        if (submit.sheetedDecalPose() != null || submit.uvMapping() != null || type.hasBlending()) {
            return false;
        }
        RenderSetup setup = ((RenderTypeAccessor) type).limn$state();
        RenderSetup.TextureBinding binding = ((RenderSetupAccessor) (Object) setup).limn$textures().get("Sampler0");
        if (binding == null) {
            return false;
        }
        Texture texture = textures.computeIfAbsent(binding.location(), this::loadTexture);
        if (texture.image() == null) {
            return false;
        }
        Model<? super S> model = submit.model();
        model.setupAnim(submit.state());
        poseStack.last().set(submit.pose());
        int tint = submit.tintedColor() & 0xFFFFFF;
        if ((submit.overlayCoords() >> 16 & 0xFFFF) == HURT_OVERLAY_ROW) {
            tint = (tint & 0xFF0000) | ((tint >> 8 & 0xFF) * 2 / 5) << 8 | (tint & 0xFF) * 2 / 5;
        }
        walk(model.root(), binding.location(), texture, tint);
        return true;
    }

    private void walk(ModelPart part, Identifier textureId, Texture texture, int tint) {
        if (!part.visible) {
            return;
        }
        ModelPartAccessor access = (ModelPartAccessor) (Object) part;
        if (access.limn$cubes().isEmpty() && access.limn$children().isEmpty()) {
            return;
        }
        poseStack.pushPose();
        part.translateAndRotate(poseStack);
        if (!part.skipDraw) {
            Matrix4f pose = poseStack.last().pose();
            for (ModelPart.Cube cube : access.limn$cubes()) {
                Part target = parts.computeIfAbsent(cube, key -> new HashMap<>()).computeIfAbsent(textureId, key -> bake(cube, texture));
                target.lastUsedFrame = frame;
                pose.get(MATRIX);
                for (float value : MATRIX) {
                    target.transforms.add(value);
                }
                target.wantedTints.add(tint);
            }
        }
        for (ModelPart child : access.limn$children().values()) {
            walk(child, textureId, texture, tint);
        }
        poseStack.popPose();
    }

    void flush(int originX, int originY, int originZ) {
        float offsetX = (float) (cameraX - originX);
        float offsetY = (float) (cameraY - originY);
        float offsetZ = (float) (cameraZ - originZ);
        try (Arena confined = Arena.ofConfined()) {
            Iterator<Map<Identifier, Part>> groups = parts.values().iterator();
            while (groups.hasNext()) {
                Map<Identifier, Part> group = groups.next();
                Iterator<Part> iterator = group.values().iterator();
                while (iterator.hasNext()) {
                    Part part = iterator.next();
                    if (frame - part.lastUsedFrame > UNUSED_FRAMES_KEPT) {
                        for (int index = 0; index < part.entities.size(); index++) {
                            context.despawn(part.entities.getLong(index));
                        }
                        if (part.model != 0) {
                            context.destroyModel(part.model);
                        }
                        iterator.remove();
                        continue;
                    }
                    place(confined, part, offsetX, offsetY, offsetZ);
                }
                if (group.isEmpty()) {
                    groups.remove();
                }
            }
        }
    }

    private void place(Arena confined, Part part, float offsetX, float offsetY, float offsetZ) {
        if (part.model == 0) {
            part.transforms.clear();
            part.wantedTints.clear();
            return;
        }
        int wanted = part.wantedTints.size();
        if (wanted > 0) {
            MemorySegment transforms = confined.allocate(JAVA_FLOAT, wanted * 16L);
            MemorySegment.copy(part.transforms.elements(), 0, transforms, JAVA_FLOAT, 0, wanted * 16);
            for (int index = 0; index < wanted; index++) {
                long at = index * 16L;
                transforms.setAtIndex(JAVA_FLOAT, at + 12, transforms.getAtIndex(JAVA_FLOAT, at + 12) + offsetX);
                transforms.setAtIndex(JAVA_FLOAT, at + 13, transforms.getAtIndex(JAVA_FLOAT, at + 13) + offsetY);
                transforms.setAtIndex(JAVA_FLOAT, at + 14, transforms.getAtIndex(JAVA_FLOAT, at + 14) + offsetZ);
            }
            while (part.entities.size() < wanted) {
                long at = part.entities.size() * 64L;
                part.entities.add(context.spawn(scene, part.model, transforms.asSlice(at, 64)));
                part.tints.add(0xFFFFFF);
            }
            MemorySegment handles = confined.allocate(JAVA_LONG, wanted);
            MemorySegment.copy(part.entities.elements(), 0, handles, JAVA_LONG, 0, wanted);
            context.setTransforms(handles, transforms, wanted);
        }
        for (int index = 0; index < wanted; index++) {
            long entity = part.entities.getLong(index);
            if (index >= part.visible) {
                context.setVisible(entity, true);
            }
            int tint = part.wantedTints.getInt(index);
            if (part.tints.getInt(index) != tint) {
                part.tints.set(index, tint);
                context.setTint(entity, (tint >> 16 & 0xFF) / 255.0F, (tint >> 8 & 0xFF) / 255.0F, (tint & 0xFF) / 255.0F);
            }
        }
        for (int index = wanted; index < part.visible; index++) {
            context.setVisible(part.entities.getLong(index), false);
        }
        part.visible = wanted;
        part.transforms.clear();
        part.wantedTints.clear();
    }

    private Part bake(ModelPart.Cube cube, Texture texture) {
        List<ModelPart.Polygon> visible = new ArrayList<>();
        for (ModelPart.Polygon polygon : cube.polygons) {
            if (texture.shows(polygon)) {
                visible.add(polygon);
            }
        }
        int quads = visible.size();
        boolean overlay = false;
        for (ModelPart.Polygon polygon : visible) {
            overlay |= texture.hasGaps(polygon);
        }
        float lift = overlay ? OVERLAY_LIFT : 0.0F;
        if (quads == 0) {
            return new Part();
        }
        try (Arena confined = Arena.ofConfined()) {
            MemorySegment positions = confined.allocate(quads * 48L, 4);
            MemorySegment normals = confined.allocate(quads * 48L, 4);
            MemorySegment uvs = confined.allocate(quads * 32L, 4);
            MemorySegment indices = confined.allocate(quads * 24L, 4);
            for (int quad = 0; quad < quads; quad++) {
                ModelPart.Polygon polygon = visible.get(quad);
                for (int corner = 0; corner < 4; corner++) {
                    ModelPart.Vertex vertex = polygon.vertices()[corner];
                    long at = quad * 4L + corner;
                    positions.setAtIndex(JAVA_FLOAT, at * 3, vertex.worldX() + polygon.normal().x() * lift);
                    positions.setAtIndex(JAVA_FLOAT, at * 3 + 1, vertex.worldY() + polygon.normal().y() * lift);
                    positions.setAtIndex(JAVA_FLOAT, at * 3 + 2, vertex.worldZ() + polygon.normal().z() * lift);
                    normals.setAtIndex(JAVA_FLOAT, at * 3, polygon.normal().x());
                    normals.setAtIndex(JAVA_FLOAT, at * 3 + 1, polygon.normal().y());
                    normals.setAtIndex(JAVA_FLOAT, at * 3 + 2, polygon.normal().z());
                    uvs.setAtIndex(JAVA_FLOAT, at * 2, vertex.u());
                    uvs.setAtIndex(JAVA_FLOAT, at * 2 + 1, vertex.v());
                }
                int base = quad * 4;
                long at = quad * 6L;
                indices.setAtIndex(JAVA_INT, at, base);
                indices.setAtIndex(JAVA_INT, at + 1, base + 1);
                indices.setAtIndex(JAVA_INT, at + 2, base + 2);
                indices.setAtIndex(JAVA_INT, at + 3, base);
                indices.setAtIndex(JAVA_INT, at + 4, base + 2);
                indices.setAtIndex(JAVA_INT, at + 5, base + 3);
            }
            MemorySegment mesh = confined.allocate(LimnAbi.Mesh.SIZE, 8);
            mesh.set(ADDRESS, LimnAbi.Mesh.POSITIONS, positions);
            mesh.set(ADDRESS, LimnAbi.Mesh.NORMALS, normals);
            mesh.set(ADDRESS, LimnAbi.Mesh.UVS, uvs);
            mesh.set(ADDRESS, LimnAbi.Mesh.INDICES, indices);
            mesh.set(JAVA_INT, LimnAbi.Mesh.VERTEX_COUNT, quads * 4);
            mesh.set(JAVA_INT, LimnAbi.Mesh.INDEX_COUNT, quads * 6);
            MemorySegment material = mesh.asSlice(LimnAbi.Mesh.MATERIAL, LimnAbi.Material.SIZE);
            for (int channel = 0; channel < 4; channel++) {
                material.set(JAVA_FLOAT, LimnAbi.Material.BASE_COLOR + channel * 4L, 1.0F);
            }
            material.set(JAVA_FLOAT, LimnAbi.Material.ROUGHNESS, 0.85F);
            material.set(JAVA_FLOAT, LimnAbi.Material.ALPHA_CUTOFF, 0.5F);
            material.set(JAVA_FLOAT, LimnAbi.Material.IOR, 1.5F);
            material.set(JAVA_INT, LimnAbi.Material.ALPHA_MODE, LimnAbi.Material.ALPHA_MASK);
            material.set(JAVA_INT, LimnAbi.Material.FLAGS, LimnAbi.Material.DOUBLE_SIDED);
            Part part = new Part();
            part.model = context.createModel(mesh, 1);
            MemorySegment textureSet = confined.allocate(LimnAbi.MaterialTextures.SIZE, 8);
            textureSet.set(ADDRESS, LimnAbi.MaterialTextures.BASE_COLOR, texture.image());
            textureSet.set(JAVA_INT, LimnAbi.MaterialTextures.FLAGS, LimnAbi.MaterialTextures.NEAREST);
            context.setModelTextures(part.model, -1, textureSet);
            return part;
        }
    }

    private Texture loadTexture(Identifier location) {
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(location);
        if (resource.isEmpty()) {
            return new Texture(null, null, 0, 0);
        }
        try (InputStream stream = resource.get().open(); NativeImage image = NativeImage.read(stream); Arena confined = Arena.ofConfined()) {
            int[] pixels = image.getPixelsABGR();
            boolean[] opaque = new boolean[pixels.length];
            for (int index = 0; index < pixels.length; index++) {
                opaque[index] = pixels[index] >>> 24 != 0;
            }
            BlockAtlasPixels.withFilledTransparency(pixels);
            MemorySegment bytes = confined.allocate(pixels.length * 4L, 4);
            MemorySegment.copy(pixels, 0, bytes, JAVA_INT_UNALIGNED, 0, pixels.length);
            return new Texture(context.createImage(arena, image.getWidth(), image.getHeight(), bytes, true, 1), opaque, image.getWidth(), image.getHeight());
        } catch (IOException | IllegalArgumentException exception) {
            return new Texture(null, null, 0, 0);
        }
    }
}
