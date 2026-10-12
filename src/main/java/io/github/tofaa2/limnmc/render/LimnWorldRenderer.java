package io.github.tofaa2.limnmc.render;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import io.github.tofaa2.limnmc.nativeapi.LimnAbi;
import io.github.tofaa2.limnmc.nativeapi.LimnContext;
import io.github.tofaa2.limnmc.nativeapi.LimnLibrary;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.slf4j.Logger;

public final class LimnWorldRenderer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int REBASE_DISTANCE = 1024;
    private static final int RELEASE_DELAY_FRAMES = 4;
    private static final int AWAITED_FRAMES = 240;
    private static final int ATLAS_KEPT_FRAMES = 1200;
    private static final float NEAR = 0.05F;
    private static final float SKY_STEP = 0.002F;
    private static final int DLSS_UPSCALING = 5;
    private static final boolean DIAGNOSTICS = Boolean.getBoolean("limn.diagnostics");
    private static final float PATH_TRACED_SUN_STEP = 0.004F;
    private static final int MAX_LIGHTS = 256;
    private static final int ATLAS_MIP_LEVELS = 4;
    private static final float MOONLIGHT = 0.08F;
    private static final float MOON_RISE = 0.1F;
    private static final float MOON_LOWEST = 0.35F;
    private static final float LIGHT_INTENSITY = 6.0F;
    private static final float DAY_LUMINANCE = 0.35F;
    private static final float NIGHT_LUMINANCE = 0.005F;
    private static final float LAMP_LUMINANCE = 0.06F;
    private static final float MIDDLE_GREY = 0.18F;
    private static final float DAY_SUN_HEIGHT = 0.15F;
    private static final float NIGHT_SUN_HEIGHT = -0.2F;

    private static volatile LimnWorldRenderer instance;
    private static volatile boolean failed;

    private record AtlasImages(MemorySegment color, MemorySegment surface, MemorySegment emission, MemorySegment normal) {
    }

    private record RetiredAtlas(AtlasImages images, long frame) {
    }

    private record Release(long entity, long model, long frame, long awaited) {
    }

    private final Arena arena = Arena.ofShared();
    private final LimnContext context;
    private final LimnContext.Info info;
    private final long scene;
    private final long sky;
    private final MemorySegment frame = arena.allocate(LimnAbi.Frame.SIZE, 8);
    private final MemorySegment skyDesc = arena.allocate(LimnAbi.Sky.SIZE, 4);
    private final MemorySegment sun = arena.allocate(LimnAbi.Sun.SIZE, 4);
    private final MemorySegment clouds = arena.allocate(LimnAbi.Clouds.SIZE, 4);
    private final ReentrantReadWriteLock originLock = new ReentrantReadWriteLock();
    private final ConcurrentLinkedQueue<Release> released = new ConcurrentLinkedQueue<>();
    private final Set<LimnSectionMesh> lit = ConcurrentHashMap.newKeySet();
    private final MemorySegment lightBuffer = arena.allocate((long) MAX_LIGHTS * LimnAbi.Light.SIZE, 4);
    private final FrameComposite composite = new FrameComposite();
    private final SmokeVolume smoke = new SmokeVolume(arena);
    private final EntityModels entities;
    private final Weather weather = new Weather(arena);
    private final ParticleBursts bursts = new ParticleBursts(arena);
    private final WaterSheet waterSheet = new WaterSheet(arena);
    private final Map<LimnSectionMesh, Long> seaSurfaces = new ConcurrentHashMap<>();
    private final Map<LimnSectionMesh, Boolean> hiddenSeaSurfaces = new ConcurrentHashMap<>();
    private final Map<Long, LimnSectionMesh> shownSections = new ConcurrentHashMap<>();
    private final Map<LimnSectionMesh, Long> sectionNodes = new ConcurrentHashMap<>();
    private final ArrayDeque<RetiredAtlas> retiredAtlases = new ArrayDeque<>();
    private volatile AtlasImages atlas;
    private long lastLightsKey = Long.MIN_VALUE;
    private volatile long frameCount;
    private boolean hasOrigin;
    private int originX;
    private int originY;
    private int originZ;
    private int width;
    private int height;
    private long lastFrameNanos;
    private float lastSkyAngle = Float.NaN;
    private float lastSunAngle = Float.NaN;
    private float sunHeight = 1.0F;
    private float adaptedLuminance = Float.NaN;
    private float lastCloudCoverage = Float.NaN;

    private LimnWorldRenderer(int width, int height) {
        Path directory = Minecraft.getInstance().gameDirectory.toPath().resolve("limn");
        LimnLibrary library = LimnLibrary.load(directory);
        int flags = LimnSettings.rayTracing ? LimnAbi.Options.RAY_TRACING : 0;
        boolean share = SharedImport.available();
        if (share) {
            flags |= LimnAbi.Options.SHARED_IMAGES;
        }
        String device = share ? SharedImport.deviceName() : null;
        this.context = LimnContext.create(library, width, height, flags, directory.resolve("pipeline.cache"), device);
        this.info = context.info();
        this.width = width;
        this.height = height;
        boolean zeroCopy = composite.useShared(context.sharedImages());
        this.scene = context.createScene();
        this.entities = new EntityModels(context, scene, arena);
        writeSky(0.0F, 0.0F, 0.0F);
        this.sky = context.createSky(skyDesc);
        context.setSky(scene, sky, 1.0F);
        BlockAtlasPixels pixels = BlockAtlasPixels.latest();
        if (pixels != null) {
            useAtlas(pixels);
        }
        LOGGER.info(
            "limn started: vendor {}, ray tracing {}, path tracing {}, DLSS {}, shared images {} (requested {})",
            Integer.toHexString(info.vendorId()), info.rayTracing(), info.pathTracing(), info.dlssSuperResolution(), zeroCopy, share
        );
    }

    public static LimnWorldRenderer active() {
        return LimnSettings.enabled && !failed ? instance : null;
    }

    public static LimnWorldRenderer acquire(int width, int height) {
        if (!LimnSettings.enabled || failed) {
            return null;
        }
        if (instance == null) {
            try {
                instance = new LimnWorldRenderer(width, height);
            } catch (RuntimeException exception) {
                fail(exception);
                return null;
            }
        }
        return instance;
    }

    public static void fail(Throwable throwable) {
        if (!failed) {
            failed = true;
            LOGGER.error("limn is off for this session", throwable);
        }
    }

    public static void shutdown() {
        LimnWorldRenderer renderer = instance;
        instance = null;
        if (renderer != null) {
            renderer.composite.close();
            renderer.context.close();
            renderer.arena.close();
        }
    }

    public boolean replacesWeather() {
        return weather.replacesVanilla();
    }

    public boolean addBurst(ParticleBursts.Look look, int color, double x, double y, double z) {
        return bursts.add(look, color, (float) x, (float) y, (float) z);
    }

    public boolean addSmokePuff(double x, double y, double z, boolean blast) {
        return smoke.addPuff((float) x, (float) y, (float) z, blast ? SmokeVolume.BLAST : SmokeVolume.PUFF);
    }

    EntityModels entities() {
        return entities;
    }

    public void beginCapture(CameraRenderState camera) {
        entities.begin(camera.pos.x, camera.pos.y, camera.pos.z);
    }

    public LimnContext.Info info() {
        return info;
    }

    public void useAtlas(BlockAtlasPixels pixels) {
        int width = pixels.width();
        int height = pixels.height();
        AtlasImages previous = atlas;
        if (previous != null) {
            synchronized (retiredAtlases) {
                retiredAtlases.add(new RetiredAtlas(previous, frameCount));
            }
        }
        atlas = new AtlasImages(
            context.createImage(arena, width, height, pixels.color(), true, ATLAS_MIP_LEVELS),
            context.createImage(arena, width, height, pixels.surface(), false, ATLAS_MIP_LEVELS),
            context.createImage(arena, width, height, pixels.emission(), true, ATLAS_MIP_LEVELS),
            pixels.hasNormals() ? context.createImage(arena, width, height, pixels.normal(), false, ATLAS_MIP_LEVELS) : null
        );
    }

    void updateAtlas(int x, int y, int width, int height, MemorySegment color, MemorySegment emission) {
        AtlasImages images = atlas;
        if (images == null) {
            return;
        }
        try {
            context.updateImage(images.color(), x, y, width, height, color);
            if (emission != null) {
                context.updateImage(images.emission(), x, y, width, height, emission);
            }
        } catch (RuntimeException exception) {
            fail(exception);
        }
    }

    long createTerrainModel(MemorySegment meshes, int count) {
        long model = context.createModel(meshes, count);
        AtlasImages images = atlas;
        if (images != null) {
            try (Arena confined = Arena.ofConfined()) {
                MemorySegment textures = confined.allocate(LimnAbi.MaterialTextures.SIZE, 8);
                textures.set(ADDRESS, LimnAbi.MaterialTextures.BASE_COLOR, images.color());
                textures.set(JAVA_INT, LimnAbi.MaterialTextures.FLAGS, LimnAbi.MaterialTextures.NEAREST);
                if (LimnSettings.physicallyBasedSurfaces) {
                    textures.set(ADDRESS, LimnAbi.MaterialTextures.METALLIC_ROUGHNESS, images.surface());
                    textures.set(ADDRESS, LimnAbi.MaterialTextures.EMISSIVE, images.emission());
                    if (images.normal() != null) {
                        textures.set(ADDRESS, LimnAbi.MaterialTextures.NORMAL, images.normal());
                    }
                }
                context.setModelTextures(model, -1, textures);
            }
        }
        return model;
    }

    private void updateLights(CameraRenderState camera) {
        int count = 0;
        for (LimnSectionMesh mesh : lit) {
            count += mesh.limn$emitters().lights().length / BlockLights.STRIDE;
        }
        float[] all = new float[count * BlockLights.STRIDE];
        long[] order = new long[count];
        int filled = 0;
        for (LimnSectionMesh mesh : lit) {
            float[] lights = mesh.limn$emitters().lights();
            for (int at = 0; at + BlockLights.STRIDE <= lights.length && filled < count; at += BlockLights.STRIDE) {
                System.arraycopy(lights, at, all, filled * BlockLights.STRIDE, BlockLights.STRIDE);
                float x = (float) (lights[at] - camera.pos.x);
                float y = (float) (lights[at + 1] - camera.pos.y);
                float z = (float) (lights[at + 2] - camera.pos.z);
                order[filled] = (long) Float.floatToIntBits(x * x + y * y + z * z) << 32 | filled;
                filled++;
            }
        }
        Arrays.sort(order, 0, filled);
        int used = Math.min(filled, MAX_LIGHTS);
        for (int index = 0; index < used; index++) {
            int from = (int) order[index] * BlockLights.STRIDE;
            long place = Float.floatToIntBits(all[from]) * 31L + Float.floatToIntBits(all[from + 1]) * 17L + Float.floatToIntBits(all[from + 2]);
            order[index] = place << 32 | (order[index] & 0xFFFFFFFFL);
        }
        Arrays.sort(order, 0, used);
        long key = originX * 17L + originY * 13L + originZ * 7L;
        int written = 0;
        long previousPlace = Long.MIN_VALUE;
        for (int index = 0; index < used; index++) {
            long place = order[index] >> 32;
            if (place == previousPlace) {
                continue;
            }
            previousPlace = place;
            int from = (int) order[index] * BlockLights.STRIDE;
            MemorySegment light = lightBuffer.asSlice((long) written * LimnAbi.Light.SIZE, LimnAbi.Light.SIZE);
            writeVector(light, LimnAbi.Light.POSITION, all[from] - originX, all[from + 1] - originY, all[from + 2] - originZ);
            writeVector(light, LimnAbi.Light.COLOR, all[from + 3], all[from + 4], all[from + 5]);
            light.set(JAVA_FLOAT, LimnAbi.Light.INTENSITY, all[from + 6] * LIGHT_INTENSITY);
            light.set(JAVA_FLOAT, LimnAbi.Light.RANGE, all[from + 7]);
            key = key * 31 + place * 131 + Float.floatToIntBits(all[from + 6]);
            written++;
        }
        key = key * 31 + written;
        if (key != lastLightsKey) {
            lastLightsKey = key;
            context.setLights(scene, lightBuffer, written);
        }
    }

    public void showSection(LimnSectionMesh mesh, long sectionNode) {
        shownSections.put(sectionNode, mesh);
        sectionNodes.put(mesh, sectionNode);
        if (mesh.limn$emitters() != BlockLights.Emitters.NONE) {
            lit.add(mesh);
        }
        boolean spawnTerrain = mesh.limn$model() != 0 && mesh.limn$entity() == 0;
        boolean spawnSea = mesh.limn$seaModel() != 0 && mesh.limn$seaEntity() == 0;
        if (!spawnTerrain && !spawnSea) {
            return;
        }
        originLock.readLock().lock();
        try (Arena confined = Arena.ofConfined()) {
            MemorySegment transform = confined.allocate(JAVA_FLOAT, 16);
            transform.setAtIndex(JAVA_FLOAT, 0, 1.0F);
            transform.setAtIndex(JAVA_FLOAT, 5, 1.0F);
            transform.setAtIndex(JAVA_FLOAT, 10, 1.0F);
            transform.setAtIndex(JAVA_FLOAT, 15, 1.0F);
            transform.setAtIndex(JAVA_FLOAT, 12, SectionPos.sectionToBlockCoord(SectionPos.x(sectionNode)) - originX);
            transform.setAtIndex(JAVA_FLOAT, 13, SectionPos.sectionToBlockCoord(SectionPos.y(sectionNode)) - originY);
            transform.setAtIndex(JAVA_FLOAT, 14, SectionPos.sectionToBlockCoord(SectionPos.z(sectionNode)) - originZ);
            if (spawnTerrain) {
                mesh.limn$setEntity(context.spawn(scene, mesh.limn$model(), transform));
            }
            if (spawnSea) {
                mesh.limn$setSeaEntity(context.spawn(scene, mesh.limn$seaModel(), transform));
                seaSurfaces.put(mesh, sectionNode);
            }
        } catch (RuntimeException exception) {
            fail(exception);
        } finally {
            originLock.readLock().unlock();
        }
    }

    public void releaseSection(LimnSectionMesh mesh) {
        lit.remove(mesh);
        long awaited = 0;
        long awaitedSea = 0;
        Long sectionNode = sectionNodes.remove(mesh);
        if (sectionNode != null && !shownSections.remove(sectionNode, mesh)) {
            LimnSectionMesh replacement = shownSections.get(sectionNode);
            if (replacement != null) {
                awaited = replacement.limn$model();
                awaitedSea = replacement.limn$seaModel();
            }
        }
        release(mesh.limn$entity(), mesh.limn$model(), awaited);
        mesh.limn$setEntity(0);
        mesh.limn$setModel(0);
        seaSurfaces.remove(mesh);
        hiddenSeaSurfaces.remove(mesh);
        release(mesh.limn$seaEntity(), mesh.limn$seaModel(), awaitedSea != 0 ? awaitedSea : awaited);
        mesh.limn$setSeaEntity(0);
        mesh.limn$setSeaModel(0);
    }

    public void releaseModel(long entity, long model) {
        release(entity, model, 0);
    }

    private void release(long entity, long model, long awaited) {
        if (entity != 0 || model != 0) {
            released.add(new Release(entity, model, frameCount, awaited));
        }
    }

    public boolean renderFrame(CameraRenderState camera, SkyState sky, int targetWidth, int targetHeight) {
        try {
            if (targetWidth != width || targetHeight != height) {
                context.resize(targetWidth, targetHeight);
                composite.useShared(context.sharedImages());
                width = targetWidth;
                height = targetHeight;
            }
            frameCount++;
            drainReleases();
            moveOrigin(camera);
            entities.flush(originX, originY, originZ);
            updateSky(sky);
            updateClouds(sky.cloudHeight(), sky.rain());
            updateLights(camera);
            bursts.flush(context, scene, originX, originY, originZ);
            waterSheet.update(context, scene, camera.pos, sky.rain(), originX, originY, originZ);
            waterSheet.hideCovered(context, seaSurfaces, hiddenSeaSurfaces);
            weather.update(context, scene, camera.pos, camera.blockPos, sky.rain(), originX, originY, originZ);
            smoke.update(
                context, scene, lit, camera.pos, originX, originY, originZ,
                info.rayTracing() && LimnSettings.rayTracing && LimnSettings.globalIllumination
            );
            writeFrame(camera);
            context.render(frame);
            if (DIAGNOSTICS && frameCount % 60 == 0) {
            }
            if (DIAGNOSTICS && frameCount % 60 == 0) {
            }
            if (DIAGNOSTICS && frameCount % 60 == 0) {
                int[] values = context.diagnostics();
                LOGGER.info("limn frame {}: structures pending {}, traced frames {}, untraced frames {}, instances {}", frameCount, values[0], values[1], values[2], values[3]);
            }
            if (composite.shared()) {
                composite.presentShared(context, camera.projectionMatrix, NEAR);
            } else {
                composite.upload(context, width, height, camera.projectionMatrix, NEAR);
            }
            return composite.ready();
        } catch (RuntimeException exception) {
            fail(exception);
            return false;
        }
    }

    public void composite(RenderTarget target) {
        if (composite.ready()) {
            composite.draw(target);
        }
    }

    private void drainReleases() {
        synchronized (retiredAtlases) {
            while (!retiredAtlases.isEmpty() && frameCount - retiredAtlases.peek().frame() > ATLAS_KEPT_FRAMES) {
                AtlasImages images = retiredAtlases.poll().images();
                context.destroyImage(images.color());
                context.destroyImage(images.surface());
                context.destroyImage(images.emission());
                if (images.normal() != null) {
                    context.destroyImage(images.normal());
                }
            }
        }
        Iterator<Release> pending = released.iterator();
        while (pending.hasNext()) {
            Release release = pending.next();
            long waited = frameCount - release.frame();
            if (waited < RELEASE_DELAY_FRAMES) {
                continue;
            }
            if (release.awaited() != 0 && waited < AWAITED_FRAMES && !context.modelTraced(release.awaited())) {
                continue;
            }
            pending.remove();
            if (release.awaited() != 0) {
                released.add(new Release(release.entity(), release.model(), frameCount, 0));
                continue;
            }
            if (release.entity() != 0) {
                context.despawn(release.entity());
            }
            if (release.model() != 0) {
                try {
                    context.destroyModel(release.model());
                } catch (RuntimeException exception) {
                    LOGGER.debug("limn kept a model", exception);
                }
            }
        }
    }

    private void moveOrigin(CameraRenderState camera) {
        int x = camera.blockPos.getX();
        int y = camera.blockPos.getY();
        int z = camera.blockPos.getZ();
        if (hasOrigin && Math.abs(x - originX) < REBASE_DISTANCE && Math.abs(y - originY) < REBASE_DISTANCE && Math.abs(z - originZ) < REBASE_DISTANCE) {
            return;
        }
        int nextX = x & ~15;
        int nextY = y & ~15;
        int nextZ = z & ~15;
        originLock.writeLock().lock();
        try {
            if (hasOrigin) {
                context.shiftScene(scene, originX - nextX, originY - nextY, originZ - nextZ);
            }
            originX = nextX;
            originY = nextY;
            originZ = nextZ;
            hasOrigin = true;
            lastCloudCoverage = Float.NaN;
        } finally {
            originLock.writeLock().unlock();
        }
    }

    private void updateSky(SkyState state) {
        float step = LimnSettings.pathTracing ? PATH_TRACED_SUN_STEP : 0.0F;
        sunHeight = (float) Math.cos(state.sunAngle());
        float sunAngle = step > 0.0F ? Math.round(state.sunAngle() / step) * step : state.sunAngle();
        float moonAngle = step > 0.0F ? Math.round(state.moonAngle() / step) * step : state.moonAngle();
        if (sunAngle != lastSunAngle) {
            lastSunAngle = sunAngle;
            writeSky(sunAngle, moonAngle, state.starBrightness());
            context.sunOfSky(skyDesc, sun);
            float moonHeight = (float) Math.cos(moonAngle);
            if (sun.get(JAVA_FLOAT, LimnAbi.Sun.INTENSITY) <= 0.0F && moonHeight > 0.0F) {
                writeVector(sun, LimnAbi.Sun.DIRECTION, (float) Math.sin(moonAngle), -Math.max(moonHeight, MOON_LOWEST), 0.02F);
                writeVector(sun, LimnAbi.Sun.COLOR, 0.6F, 0.72F, 1.0F);
                sun.set(JAVA_FLOAT, LimnAbi.Sun.INTENSITY, MOONLIGHT * Math.min(moonHeight / MOON_RISE, 1.0F));
            }
            context.setSun(scene, sun);
        }
        if (Float.isNaN(lastSkyAngle) || Math.abs(sunAngle - lastSkyAngle) >= SKY_STEP) {
            lastSkyAngle = sunAngle;
            writeSky(sunAngle, moonAngle, state.starBrightness());
            context.updateSky(sky, skyDesc);
        }
    }

    private void updateClouds(float cloudHeight, float rain) {
        float coverage = LimnSettings.volumetricClouds ? 0.4F + 0.5F * rain : -1.0F;
        if (Math.abs(coverage - lastCloudCoverage) < 0.01F) {
            return;
        }
        lastCloudCoverage = coverage;
        if (coverage < 0.0F) {
            context.setClouds(scene, MemorySegment.NULL);
            return;
        }
        clouds.set(JAVA_FLOAT, LimnAbi.Clouds.COVERAGE, coverage);
        clouds.set(JAVA_FLOAT, LimnAbi.Clouds.DENSITY, 0.012F + 0.02F * rain);
        clouds.set(JAVA_FLOAT, LimnAbi.Clouds.BOTTOM, cloudHeight + 700.0F - originY);
        clouds.set(JAVA_FLOAT, LimnAbi.Clouds.THICKNESS, 1000.0F);
        clouds.set(JAVA_FLOAT, LimnAbi.Clouds.SCALE, 0.7F);
        writeVector(clouds, LimnAbi.Clouds.WIND, 8.0F, 0.0F, 3.0F);
        context.setClouds(scene, clouds);
    }

    private void writeSky(float sunAngle, float moonAngle, float stars) {
        writeVector(skyDesc, LimnAbi.Sky.SUN_DIRECTION, (float) Math.sin(sunAngle), (float) -Math.cos(sunAngle), 0.02F);
        writeVector(skyDesc, LimnAbi.Sky.MOON_DIRECTION, (float) Math.sin(moonAngle), (float) -Math.cos(moonAngle), 0.02F);
        writeVector(skyDesc, LimnAbi.Sky.GROUND_COLOR, 0.8F, 0.9F, 1.0F);
        skyDesc.set(JAVA_FLOAT, LimnAbi.Sky.TURBIDITY, 2.5F);
        skyDesc.set(JAVA_FLOAT, LimnAbi.Sky.INTENSITY, 1.0F);
        skyDesc.set(JAVA_FLOAT, LimnAbi.Sky.STARS, stars);
        skyDesc.set(JAVA_FLOAT, LimnAbi.Sky.MOON, 1.0F);
        skyDesc.set(JAVA_INT, LimnAbi.Sky.SUN_DISC, 1);
    }

    private float exposure(CameraRenderState camera, float delta) {
        if (LimnSettings.automaticExposure) {
            adaptedLuminance = Float.NaN;
            return 0.0F;
        }
        ClientLevel level = Minecraft.getInstance().level;
        float skyLight = level == null ? 1.0F : level.getBrightness(LightLayer.SKY, camera.blockPos) / 15.0F;
        float lampLight = level == null ? 0.0F : level.getBrightness(LightLayer.BLOCK, camera.blockPos) / 15.0F;
        float daylight = (float) Math.sqrt(Math.clamp((sunHeight - NIGHT_SUN_HEIGHT) / (DAY_SUN_HEIGHT - NIGHT_SUN_HEIGHT), 0.0F, 1.0F));
        float outdoors = NIGHT_LUMINANCE * (float) Math.pow(DAY_LUMINANCE / NIGHT_LUMINANCE, daylight);
        float seen = Math.max(NIGHT_LUMINANCE, Math.max(outdoors * skyLight * skyLight, LAMP_LUMINANCE * lampLight * lampLight));
        float target = (float) Math.log(seen);
        adaptedLuminance = Float.isNaN(adaptedLuminance) ? target : adaptedLuminance + (target - adaptedLuminance) * (1.0F - (float) Math.exp(-delta / LimnSettings.exposureSeconds));
        return (float) ((Math.log(MIDDLE_GREY) - adaptedLuminance) / Math.log(2.0));
    }

    private void writeFrame(CameraRenderState camera) {
        long now = System.nanoTime();
        float delta = lastFrameNanos == 0 ? 1.0F / 60.0F : Math.clamp((now - lastFrameNanos) / 1.0e9F, 1.0e-4F, 0.25F);
        lastFrameNanos = now;
        Matrix4f swayToView = new Matrix4f(LimnFrame.viewSway()).invert();
        Vector3f forward = camera.orientation.transform(swayToView.transformDirection(new Vector3f(0.0F, 0.0F, -1.0F)));
        Vector3f offset = camera.orientation.transform(swayToView.transformPosition(new Vector3f()));
        Vector3f up = camera.orientation.transform(swayToView.transformDirection(new Vector3f(0.0F, 1.0F, 0.0F)));
        MemorySegment view = frame.asSlice(LimnAbi.Frame.CAMERA, LimnAbi.Camera.SIZE);
        writeVector(
            view, LimnAbi.Camera.POSITION,
            (float) (camera.pos.x - originX) + offset.x, (float) (camera.pos.y - originY) + offset.y, (float) (camera.pos.z - originZ) + offset.z
        );
        writeVector(view, LimnAbi.Camera.FORWARD, forward.x, forward.y, forward.z);
        writeVector(view, LimnAbi.Camera.UP, up.x, up.y, up.z);
        view.set(JAVA_FLOAT, LimnAbi.Camera.FOV_Y, (float) (2.0 * Math.atan(1.0 / camera.projectionMatrix.m11())));
        view.set(JAVA_FLOAT, LimnAbi.Camera.NEAR, NEAR);
        frame.set(JAVA_LONG, LimnAbi.Frame.SCENE, scene);
        frame.set(JAVA_FLOAT, LimnAbi.Frame.DELTA_TIME, delta);
        MemorySegment settings = frame.asSlice(LimnAbi.Frame.SETTINGS, LimnAbi.Settings.SIZE);
        settings.set(JAVA_INT, LimnAbi.Settings.QUALITY, LimnSettings.quality < 0 ? info.recommendedQuality() : LimnSettings.quality);
        boolean reconstructed = LimnSettings.pathTracing && LimnSettings.pathTracingDenoise && info.dlssRayReconstruction();
        settings.set(JAVA_INT, LimnAbi.Settings.UPSCALING, reconstructed ? DLSS_UPSCALING : LimnSettings.upscaling);
        settings.set(JAVA_INT, LimnAbi.Settings.FLAGS, LimnSettings.flags());
        settings.set(JAVA_INT, LimnAbi.Settings.PATH_TRACING_BOUNCES, LimnSettings.pathTracingBounces);
        settings.set(JAVA_INT, LimnAbi.Settings.PATH_TRACING_SAMPLES, LimnSettings.pathTracingSamples);
        settings.set(JAVA_FLOAT, LimnAbi.Settings.RENDER_SCALE, LimnSettings.renderScalePercent / 100.0F);
        settings.set(JAVA_FLOAT, LimnAbi.Settings.EXPOSURE_COMPENSATION, exposure(camera, delta));
        settings.set(JAVA_FLOAT, LimnAbi.Settings.EXPOSURE_SPEED, 1.0F / LimnSettings.exposureSeconds);
        settings.set(JAVA_FLOAT, LimnAbi.Settings.BLOOM, LimnSettings.bloomPercent / 100.0F);
        float viewDistance = Minecraft.getInstance().options.getEffectiveRenderDistance() * 16.0F;
        settings.set(JAVA_FLOAT, LimnAbi.Settings.FOG_DENSITY, 0.0F);
        settings.set(JAVA_FLOAT, LimnAbi.Settings.AERIAL_PERSPECTIVE, LimnSettings.distanceHaze ? 0.5F / viewDistance : 0.0F);
        settings.set(JAVA_FLOAT, LimnAbi.Settings.SHADOW_DISTANCE, (float) LimnSettings.shadowDistance);
        settings.set(JAVA_INT, LimnAbi.Settings.DEBUG_VIEW, LimnSettings.debugView);
        settings.set(JAVA_FLOAT, LimnAbi.Settings.PATH_TRACING_CLAMP, LimnSettings.pathTracingClamp);
    }

    private static void writeVector(MemorySegment segment, long offset, float x, float y, float z) {
        segment.set(JAVA_FLOAT, offset, x);
        segment.set(JAVA_FLOAT, offset + 4, y);
        segment.set(JAVA_FLOAT, offset + 8, z);
    }
}
