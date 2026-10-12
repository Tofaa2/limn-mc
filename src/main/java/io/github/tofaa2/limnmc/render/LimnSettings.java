package io.github.tofaa2.limnmc.render;

import io.github.tofaa2.limnmc.nativeapi.LimnAbi;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

public final class LimnSettings {
    public static final String[] QUALITY_NAMES = {"Automatic", "Low", "Medium", "High", "Ultra"};
    public static final String[] UPSCALING_NAMES = {"Spatial", "Temporal", "FSR 1", "FSR 2", "FSR 3", "DLSS"};
    public static final String[] DEBUG_VIEW_NAMES = {
        "Off", "Albedo", "Normal", "Roughness", "Metallic", "Ambient occlusion", "Shadow", "Cascades", "Motion", "Depth", "Meshlets", "Triangles",
    };

    public static volatile boolean enabled = true;
    public static volatile boolean rayTracing = true;
    public static volatile boolean pathTracing = false;
    public static volatile int pathTracingBounces = 4;
    public static volatile int pathTracingSamples = 1;
    public static volatile int quality = -1;
    public static volatile int upscaling = 1;
    public static volatile int renderScalePercent = 100;
    public static volatile boolean frameGeneration = false;
    public static volatile boolean shadows = true;
    public static volatile boolean ambientOcclusion = true;
    public static volatile boolean globalIllumination = true;
    public static volatile boolean reflections = true;
    public static volatile boolean volumetricClouds = true;
    public static volatile boolean volumetricParticles = true;
    public static volatile boolean volumetricWater = true;
    public static volatile boolean physicallyBasedSurfaces = true;
    public static volatile boolean plantSway = true;
    public static volatile boolean limnEntities = true;
    public static volatile boolean distanceHaze = true;
    public static volatile int bloomPercent = 4;
    public static volatile boolean automaticExposure = true;
    public static volatile int exposureSeconds = 3;
    public static volatile int shadowDistance = 160;
    public static volatile float pathTracingClamp = 3.0F;
    public static volatile boolean pathTracingDenoise = true;
    public static volatile int debugView = Integer.getInteger("limn.debugView", 0);

    public static int flags() {
        int flags = LimnAbi.Settings.TEMPORAL_ANTIALIASING;
        if (automaticExposure) {
            flags |= LimnAbi.Settings.AUTOMATIC_EXPOSURE;
        }
        if (pathTracingDenoise) {
            flags |= LimnAbi.Settings.PATH_TRACING_DENOISE;
        }
        if (shadows) {
            flags |= LimnAbi.Settings.SHADOWS;
        }
        if (ambientOcclusion) {
            flags |= LimnAbi.Settings.AMBIENT_OCCLUSION;
        }
        if (globalIllumination) {
            flags |= LimnAbi.Settings.GLOBAL_ILLUMINATION;
        }
        if (reflections) {
            flags |= LimnAbi.Settings.SCREEN_SPACE_REFLECTIONS;
        }
        if (reflections && rayTracing) {
            flags |= LimnAbi.Settings.RAY_TRACED_REFLECTIONS;
        }
        if (rayTracing) {
            flags |= LimnAbi.Settings.RAY_TRACED_LIGHT_SHADOWS;
        }
        if (volumetricClouds) {
            flags |= LimnAbi.Settings.CLOUDS;
        }
        if (volumetricParticles) {
            flags |= LimnAbi.Settings.FLUIDS;
        }
        if (pathTracing) {
            flags |= LimnAbi.Settings.PATH_TRACING;
        }
        if (frameGeneration) {
            flags |= LimnAbi.Settings.FRAME_GENERATION;
        }
        return flags;
    }

    public static void load() {
        Path file = file();
        if (!Files.exists(file)) {
            return;
        }
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file)) {
            properties.load(reader);
        } catch (IOException exception) {
            return;
        }
        enabled = flag(properties, "enabled", enabled);
        rayTracing = flag(properties, "rayTracing", rayTracing);
        pathTracing = flag(properties, "pathTracing", pathTracing);
        pathTracingBounces = number(properties, "pathTracingBounces", pathTracingBounces, 1, 16);
        pathTracingSamples = number(properties, "pathTracingSamples", pathTracingSamples, 1, 16);
        quality = number(properties, "quality", quality, -1, 3);
        upscaling = number(properties, "upscaling", upscaling, 0, 5);
        renderScalePercent = number(properties, "renderScalePercent", renderScalePercent, 25, 100);
        frameGeneration = flag(properties, "frameGeneration", frameGeneration);
        shadows = flag(properties, "shadows", shadows);
        ambientOcclusion = flag(properties, "ambientOcclusion", ambientOcclusion);
        globalIllumination = flag(properties, "globalIllumination", globalIllumination);
        reflections = flag(properties, "reflections", reflections);
        volumetricClouds = flag(properties, "volumetricClouds", volumetricClouds);
        volumetricParticles = flag(properties, "volumetricParticles", volumetricParticles);
        volumetricWater = flag(properties, "volumetricWater", volumetricWater);
        physicallyBasedSurfaces = flag(properties, "physicallyBasedSurfaces", physicallyBasedSurfaces);
        plantSway = flag(properties, "plantSway", plantSway);
        limnEntities = flag(properties, "limnEntities", limnEntities);
        distanceHaze = flag(properties, "distanceHaze", distanceHaze);
        bloomPercent = number(properties, "bloomPercent", bloomPercent, 0, 100);
        automaticExposure = flag(properties, "automaticExposure", automaticExposure);
        exposureSeconds = number(properties, "exposureSeconds", exposureSeconds, 1, 15);
        shadowDistance = number(properties, "shadowDistance", shadowDistance, 16, 512);
    }

    public static void save() {
        Properties properties = new Properties();
        properties.setProperty("enabled", Boolean.toString(enabled));
        properties.setProperty("rayTracing", Boolean.toString(rayTracing));
        properties.setProperty("pathTracing", Boolean.toString(pathTracing));
        properties.setProperty("pathTracingBounces", Integer.toString(pathTracingBounces));
        properties.setProperty("pathTracingSamples", Integer.toString(pathTracingSamples));
        properties.setProperty("quality", Integer.toString(quality));
        properties.setProperty("upscaling", Integer.toString(upscaling));
        properties.setProperty("renderScalePercent", Integer.toString(renderScalePercent));
        properties.setProperty("frameGeneration", Boolean.toString(frameGeneration));
        properties.setProperty("shadows", Boolean.toString(shadows));
        properties.setProperty("ambientOcclusion", Boolean.toString(ambientOcclusion));
        properties.setProperty("globalIllumination", Boolean.toString(globalIllumination));
        properties.setProperty("reflections", Boolean.toString(reflections));
        properties.setProperty("volumetricClouds", Boolean.toString(volumetricClouds));
        properties.setProperty("volumetricParticles", Boolean.toString(volumetricParticles));
        properties.setProperty("volumetricWater", Boolean.toString(volumetricWater));
        properties.setProperty("physicallyBasedSurfaces", Boolean.toString(physicallyBasedSurfaces));
        properties.setProperty("plantSway", Boolean.toString(plantSway));
        properties.setProperty("limnEntities", Boolean.toString(limnEntities));
        properties.setProperty("distanceHaze", Boolean.toString(distanceHaze));
        properties.setProperty("bloomPercent", Integer.toString(bloomPercent));
        properties.setProperty("automaticExposure", Boolean.toString(automaticExposure));
        properties.setProperty("exposureSeconds", Integer.toString(exposureSeconds));
        properties.setProperty("shadowDistance", Integer.toString(shadowDistance));
        try (Writer writer = Files.newBufferedWriter(file())) {
            properties.store(writer, null);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not save limn settings", exception);
        }
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("limn-mc.properties");
    }

    private static boolean flag(Properties properties, String key, boolean fallback) {
        String value = properties.getProperty(key);
        return value == null ? fallback : Boolean.parseBoolean(value);
    }

    private static int number(Properties properties, String key, int fallback, int minimum, int maximum) {
        try {
            return Math.clamp(Integer.parseInt(properties.getProperty(key, Integer.toString(fallback))), minimum, maximum);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private LimnSettings() {
    }
}
