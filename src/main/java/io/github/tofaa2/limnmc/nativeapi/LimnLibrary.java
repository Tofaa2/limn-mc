package io.github.tofaa2.limnmc.nativeapi;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

public final class LimnLibrary {
    private static final Linker LINKER = Linker.nativeLinker();

    private final SymbolLookup symbols;
    private final MethodHandle lastError;
    final MethodHandle create;
    final MethodHandle destroy;
    final MethodHandle info;
    final MethodHandle resize;
    final MethodHandle sceneCreate;
    final MethodHandle sceneDestroy;
    final MethodHandle sceneSetSun;
    final MethodHandle sceneShift;
    final MethodHandle sceneSetSky;
    final MethodHandle sceneSetClouds;
    final MethodHandle sceneSetLights;
    final MethodHandle fluidCreate;
    final MethodHandle fluidSet;
    final MethodHandle fluidDestroy;
    final MethodHandle emitterCreate;
    final MethodHandle emitterSet;
    final MethodHandle emitterDestroy;
    final MethodHandle emitterBurst;
    final MethodHandle waterCreate;
    final MethodHandle waterSet;
    final MethodHandle waterRipple;
    final MethodHandle waterDestroy;
    final MethodHandle skyCreate;
    final MethodHandle skySet;
    final MethodHandle skyDestroy;
    final MethodHandle skySun;
    final MethodHandle imageCreate;
    final MethodHandle imageDestroy;
    final MethodHandle imageUpdate;
    final MethodHandle modelCreate;
    final MethodHandle modelDestroy;
    final MethodHandle modelSetTextures;
    final MethodHandle entitySpawn;
    final MethodHandle entityDespawn;
    final MethodHandle entitySetVisible;
    final MethodHandle entitySetTint;
    final MethodHandle entitySetTransforms;
    final MethodHandle render;
    final MethodHandle readPixels;
    final MethodHandle depthSize;
    final MethodHandle diagnostics;
    final MethodHandle modelTraced;
    final MethodHandle sharedImages;
    final MethodHandle readDepth;

    private LimnLibrary(SymbolLookup symbols) {
        this.symbols = symbols;
        this.lastError = function("limn_mc_last_error", ADDRESS);
        checkAbi();
        this.create = function("limn_mc_create", ADDRESS, ADDRESS);
        this.destroy = procedure("limn_mc_destroy", ADDRESS);
        this.info = procedure("limn_mc_info", ADDRESS, ADDRESS);
        this.resize = function("limn_mc_resize", JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT);
        this.sceneCreate = function("limn_mc_scene_create", JAVA_LONG, ADDRESS);
        this.sceneDestroy = procedure("limn_mc_scene_destroy", ADDRESS, JAVA_LONG);
        this.sceneSetSun = procedure("limn_mc_scene_set_sun", ADDRESS, JAVA_LONG, ADDRESS);
        this.sceneShift = function("limn_mc_scene_shift", JAVA_INT, ADDRESS, JAVA_LONG, ADDRESS);
        this.sceneSetSky = procedure("limn_mc_scene_set_sky", ADDRESS, JAVA_LONG, JAVA_LONG, JAVA_FLOAT);
        this.sceneSetClouds = function("limn_mc_scene_set_clouds", JAVA_INT, ADDRESS, JAVA_LONG, ADDRESS);
        this.sceneSetLights = function("limn_mc_scene_set_lights", JAVA_INT, ADDRESS, JAVA_LONG, ADDRESS, JAVA_INT);
        this.fluidCreate = function("limn_mc_fluid_create", JAVA_LONG, ADDRESS, JAVA_LONG, ADDRESS);
        this.fluidSet = function("limn_mc_fluid_set", JAVA_INT, ADDRESS, JAVA_LONG, ADDRESS);
        this.fluidDestroy = procedure("limn_mc_fluid_destroy", ADDRESS, JAVA_LONG);
        this.emitterCreate = function("limn_mc_emitter_create", JAVA_LONG, ADDRESS, JAVA_LONG, ADDRESS);
        this.emitterSet = procedure("limn_mc_emitter_set", ADDRESS, JAVA_LONG, ADDRESS);
        this.emitterBurst = procedure("limn_mc_emitter_burst", ADDRESS, JAVA_LONG, JAVA_INT);
        this.emitterDestroy = procedure("limn_mc_emitter_destroy", ADDRESS, JAVA_LONG);
        this.waterCreate = function("limn_mc_water_create", JAVA_LONG, ADDRESS, JAVA_LONG, ADDRESS);
        this.waterSet = function("limn_mc_water_set", JAVA_INT, ADDRESS, JAVA_LONG, ADDRESS);
        this.waterRipple = procedure("limn_mc_water_ripple", ADDRESS, JAVA_LONG, JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT);
        this.waterDestroy = procedure("limn_mc_water_destroy", ADDRESS, JAVA_LONG);
        this.skyCreate = function("limn_mc_sky_create", JAVA_LONG, ADDRESS, ADDRESS);
        this.skySet = procedure("limn_mc_sky_set", ADDRESS, JAVA_LONG, ADDRESS);
        this.skyDestroy = procedure("limn_mc_sky_destroy", ADDRESS, JAVA_LONG);
        this.skySun = procedure("limn_mc_sky_sun", ADDRESS, ADDRESS);
        this.imageCreate = function("limn_mc_image_create", JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS);
        this.imageUpdate = function("limn_mc_image_update", JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, ADDRESS);
        this.imageDestroy = procedure("limn_mc_image_destroy", ADDRESS, ADDRESS);
        this.modelCreate = function("limn_mc_model_create", JAVA_LONG, ADDRESS, ADDRESS, JAVA_INT);
        this.modelDestroy = function("limn_mc_model_destroy", JAVA_INT, ADDRESS, JAVA_LONG);
        this.modelSetTextures = function("limn_mc_model_set_textures", JAVA_INT, ADDRESS, JAVA_LONG, JAVA_INT, ADDRESS);
        this.entitySpawn = function("limn_mc_entity_spawn", JAVA_LONG, ADDRESS, JAVA_LONG, JAVA_LONG, ADDRESS);
        this.entityDespawn = procedure("limn_mc_entity_despawn", ADDRESS, JAVA_LONG);
        this.entitySetTint = procedure("limn_mc_entity_set_tint", ADDRESS, JAVA_LONG, JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT);
        this.entitySetVisible = procedure("limn_mc_entity_set_visible", ADDRESS, JAVA_LONG, JAVA_INT);
        this.entitySetTransforms = procedure("limn_mc_entity_set_transforms", ADDRESS, ADDRESS, ADDRESS, JAVA_INT);
        this.render = function("limn_mc_render", JAVA_INT, ADDRESS, ADDRESS);
        this.readPixels = function("limn_mc_read_pixels", JAVA_INT, ADDRESS, ADDRESS, JAVA_LONG);
        this.sharedImages = function("limn_mc_shared_images", JAVA_INT, ADDRESS, ADDRESS);
        this.diagnostics = procedure("limn_mc_diagnostics", ADDRESS, ADDRESS);
        this.modelTraced = function("limn_mc_model_traced", JAVA_INT, ADDRESS, JAVA_LONG);
        this.depthSize = procedure("limn_mc_depth_size", ADDRESS, ADDRESS);
        this.readDepth = function("limn_mc_read_depth", JAVA_INT, ADDRESS, ADDRESS, JAVA_LONG);
    }

    public static LimnLibrary load(Path extractionDirectory) {
        String resource = "/natives/" + platform() + "/" + System.mapLibraryName("limn_mc");
        try (InputStream stream = LimnLibrary.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new LimnException("No limn build for " + platform());
            }
            Files.createDirectories(extractionDirectory);
            Path file = extractionDirectory.resolve(System.mapLibraryName("limn_mc"));
            Files.copy(stream, file, StandardCopyOption.REPLACE_EXISTING);
            return new LimnLibrary(SymbolLookup.libraryLookup(file, Arena.global()));
        } catch (IOException | IllegalArgumentException exception) {
            throw new LimnException("Could not load limn", exception);
        }
    }

    private static String platform() {
        String name = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        String os = name.contains("win") ? "windows" : name.contains("mac") ? "macos" : "linux";
        String arch = System.getProperty("os.arch");
        return os + "-" + (arch.equals("amd64") ? "x86_64" : arch);
    }

    private void checkAbi() {
        try (Arena arena = Arena.ofConfined()) {
            int version = (int) function("limn_mc_abi_version", JAVA_INT).invokeExact();
            if (version != LimnAbi.VERSION) {
                throw new LimnException("limn ABI " + version + ", expected " + LimnAbi.VERSION);
            }
            int expected = LimnAbi.STRUCT_SIZES.length;
            MemorySegment sizes = arena.allocate(JAVA_INT, expected);
            int count = (int) function("limn_mc_struct_sizes", JAVA_INT, ADDRESS, JAVA_INT).invokeExact(sizes, expected);
            if (count != expected) {
                throw new LimnException("limn has " + count + " structs, expected " + expected);
            }
            for (int index = 0; index < expected; index++) {
                int size = sizes.getAtIndex(JAVA_INT, index);
                if (size != LimnAbi.STRUCT_SIZES[index]) {
                    throw new LimnException("limn struct " + index + " is " + size + " bytes, expected " + LimnAbi.STRUCT_SIZES[index]);
                }
            }
        } catch (LimnException exception) {
            throw exception;
        } catch (Throwable throwable) {
            throw new LimnException("Could not check the limn ABI", throwable);
        }
    }

    String lastError() {
        try {
            MemorySegment text = (MemorySegment) lastError.invokeExact();
            return text.reinterpret(128).getString(0);
        } catch (Throwable throwable) {
            throw new LimnException("Could not read the limn error", throwable);
        }
    }

    private MethodHandle function(String name, MemoryLayout result, MemoryLayout... arguments) {
        return handle(name, FunctionDescriptor.of(result, arguments));
    }

    private MethodHandle procedure(String name, MemoryLayout... arguments) {
        return handle(name, FunctionDescriptor.ofVoid(arguments));
    }

    private MethodHandle handle(String name, FunctionDescriptor descriptor) {
        MemorySegment symbol = symbols.find(name).orElseThrow(() -> new LimnException("limn has no " + name));
        return LINKER.downcallHandle(symbol, descriptor);
    }
}
