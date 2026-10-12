package io.github.tofaa2.limnmc.nativeapi;

public final class LimnAbi {
    public static final int VERSION = 18;

    public static final int[] STRUCT_SIZES = {
        Options.SIZE, Info.SIZE, Image.SIZE, Material.SIZE, Mesh.SIZE, MaterialTextures.SIZE,
        Sun.SIZE, Sky.SIZE, Camera.SIZE, Settings.SIZE, Frame.SIZE, Clouds.SIZE, Light.SIZE, SharedImages.SIZE, FluidSource.SIZE, Fluid.SIZE, Emitter.SIZE, Water.SIZE,
    };

    public static final class Options {
        public static final int SIZE = 32;
        public static final int WIDTH = 0;
        public static final int HEIGHT = 4;
        public static final int FLAGS = 8;
        public static final int WORKER_THREADS = 12;
        public static final int PIPELINE_CACHE_PATH = 16;
        public static final int PREFERRED_DEVICE = 24;

        public static final int VALIDATION = 1;
        public static final int DEBUG_NAMES = 1 << 1;
        public static final int RAY_TRACING = 1 << 2;
        public static final int PATH_TRACING_FALLBACK = 1 << 3;
        public static final int MESH_SHADERS = 1 << 4;
        public static final int SHARED_IMAGES = 1 << 5;
    }

    public static final class Info {
        public static final int SIZE = 32;
        public static final int VENDOR_ID = 0;
        public static final int RAY_TRACING = 4;
        public static final int PATH_TRACING = 8;
        public static final int DLSS_SUPER_RESOLUTION = 12;
        public static final int DLSS_RAY_RECONSTRUCTION = 16;
        public static final int RECOMMENDED_QUALITY = 20;
        public static final int GPU_MEMORY_BYTES = 24;
    }

    public static final class Image {
        public static final int SIZE = 16;
    }

    public static final class Material {
        public static final int SIZE = 60;
        public static final int BASE_COLOR = 0;
        public static final int EMISSIVE = 16;
        public static final int METALLIC = 28;
        public static final int ROUGHNESS = 32;
        public static final int ALPHA_CUTOFF = 36;
        public static final int TRANSMISSION = 40;
        public static final int IOR = 44;
        public static final int ALPHA_MODE = 48;
        public static final int FLAGS = 52;
        public static final int SWAY = 56;

        public static final int ALPHA_OPAQUE = 0;
        public static final int ALPHA_MASK = 1;
        public static final int ALPHA_BLEND = 2;
        public static final int DOUBLE_SIDED = 1;
    }

    public static final class Mesh {
        public static final int SIZE = 120;
        public static final int POSITIONS = 0;
        public static final int NORMALS = 8;
        public static final int UVS = 16;
        public static final int COLORS = 24;
        public static final int UVS1 = 32;
        public static final int INDICES = 40;
        public static final int VERTEX_COUNT = 48;
        public static final int INDEX_COUNT = 52;
        public static final int MATERIAL = 56;
    }

    public static final class MaterialTextures {
        public static final int SIZE = 48;
        public static final int FLAGS = 40;
        public static final int NEAREST = 1;
        public static final int BASE_COLOR = 0;
        public static final int NORMAL = 8;
        public static final int METALLIC_ROUGHNESS = 16;
        public static final int OCCLUSION = 24;
        public static final int EMISSIVE = 32;
    }

    public static final class Sun {
        public static final int SIZE = 28;
        public static final int DIRECTION = 0;
        public static final int COLOR = 12;
        public static final int INTENSITY = 24;
    }

    public static final class Sky {
        public static final int SIZE = 56;
        public static final int SUN_DIRECTION = 0;
        public static final int MOON_DIRECTION = 12;
        public static final int GROUND_COLOR = 24;
        public static final int TURBIDITY = 36;
        public static final int INTENSITY = 40;
        public static final int STARS = 44;
        public static final int MOON = 48;
        public static final int SUN_DISC = 52;
    }

    public static final class Camera {
        public static final int SIZE = 44;
        public static final int POSITION = 0;
        public static final int FORWARD = 12;
        public static final int UP = 24;
        public static final int FOV_Y = 36;
        public static final int NEAR = 40;
    }

    public static final class Settings {
        public static final int SIZE = 56;
        public static final int QUALITY = 0;
        public static final int UPSCALING = 4;
        public static final int FLAGS = 8;
        public static final int PATH_TRACING_BOUNCES = 12;
        public static final int PATH_TRACING_SAMPLES = 16;
        public static final int RENDER_SCALE = 20;
        public static final int EXPOSURE_COMPENSATION = 24;
        public static final int BLOOM = 28;
        public static final int FOG_DENSITY = 32;
        public static final int SHADOW_DISTANCE = 36;
        public static final int DEBUG_VIEW = 40;
        public static final int PATH_TRACING_CLAMP = 44;
        public static final int AERIAL_PERSPECTIVE = 48;
        public static final int EXPOSURE_SPEED = 52;

        public static final int SHADOWS = 1;
        public static final int AMBIENT_OCCLUSION = 1 << 1;
        public static final int GLOBAL_ILLUMINATION = 1 << 2;
        public static final int SCREEN_SPACE_REFLECTIONS = 1 << 3;
        public static final int RAY_TRACED_REFLECTIONS = 1 << 4;
        public static final int RAY_TRACED_LIGHT_SHADOWS = 1 << 5;
        public static final int CLOUDS = 1 << 6;
        public static final int FLUIDS = 1 << 7;
        public static final int TEMPORAL_ANTIALIASING = 1 << 8;
        public static final int PATH_TRACING = 1 << 9;
        public static final int PATH_TRACING_DENOISE = 1 << 10;
        public static final int FRAME_GENERATION = 1 << 11;
        public static final int AUTOMATIC_EXPOSURE = 1 << 12;
    }

    public static final class Frame {
        public static final int SIZE = 112;
        public static final int SCENE = 0;
        public static final int CAMERA = 8;
        public static final int DELTA_TIME = 52;
        public static final int SETTINGS = 56;
    }

    public static final class Clouds {
        public static final int SIZE = 32;
        public static final int COVERAGE = 0;
        public static final int DENSITY = 4;
        public static final int BOTTOM = 8;
        public static final int THICKNESS = 12;
        public static final int SCALE = 16;
        public static final int WIND = 20;
    }

    public static final class Light {
        public static final int SIZE = 32;
        public static final int POSITION = 0;
        public static final int COLOR = 12;
        public static final int INTENSITY = 24;
        public static final int RANGE = 28;
    }

    public static final class SharedImages {
        public static final int SIZE = 40;
        public static final int COLOR_HANDLE = 0;
        public static final int DEPTH_HANDLE = 8;
        public static final int COLOR_BYTES = 16;
        public static final int DEPTH_BYTES = 24;
        public static final int WIDTH = 32;
        public static final int HEIGHT = 36;
    }

    public static final class FluidSource {
        public static final int SIZE = 40;
        public static final int POSITION = 0;
        public static final int RADIUS = 12;
        public static final int VELOCITY = 16;
        public static final int SMOKE = 28;
        public static final int FUEL = 32;
        public static final int TEMPERATURE = 36;
    }

    public static final class Fluid {
        public static final int SIZE = 416;
        public static final int TRANSFORM = 0;
        public static final int SOURCES = 64;
        public static final int SOURCE_COUNT = 384;
        public static final int RESOLUTION = 388;
        public static final int FLAGS = 400;
        public static final int WIND = 404;
        public static final int MAX_SOURCES = 8;
        public static final int SCENE_OBSTACLES = 1;
    }

    public static final class Emitter {
        public static final int SIZE = 124;
        public static final int POSITION = 0;
        public static final int RADIUS = 12;
        public static final int RATE = 16;
        public static final int LIFETIME = 20;
        public static final int DIRECTION = 28;
        public static final int SPREAD = 40;
        public static final int SPEED = 44;
        public static final int GRAVITY = 52;
        public static final int DRAG = 64;
        public static final int PARTICLE_SIZE = 68;
        public static final int COLOR_START = 76;
        public static final int COLOR_END = 92;
        public static final int STRETCH = 108;
        public static final int BOUNCE = 112;
        public static final int CAPACITY = 116;
        public static final int FLAGS = 120;
        public static final int LIT = 1;
        public static final int COLLIDE = 1 << 1;
        public static final int ADDITIVE = 1 << 2;
    }

    public static final class Water {
        public static final int SIZE = 144;
        public static final int TRANSFORM = 0;
        public static final int COLOR = 64;
        public static final int MURK = 76;
        public static final int ROUGHNESS = 80;
        public static final int REFRACTION = 84;
        public static final int FOAM = 88;
        public static final int RIPPLE_DETAIL = 92;
        public static final int WAVE_SPEED = 96;
        public static final int DAMPING = 100;
        public static final int RAIN = 104;
        public static final int SWELL = 108;
        public static final int SWELL_LENGTH = 112;
        public static final int RESOLUTION = 116;
        public static final int MASK = 128;
        public static final int FLAGS = 136;
    }

    private LimnAbi() {
    }
}
