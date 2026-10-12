const std = @import("std");
const gfx = @import("limn");

pub const abi_version: u32 = 18;
const night_exposure_floor = 0.005;
const block_light_radius = 0.9;

const gpa = std.heap.smp_allocator;

pub const Context = struct {
    threaded: std.Io.Threaded,
    strings: std.heap.ArenaAllocator,
    renderer: *gfx.Renderer,
    target: gfx.rhi.Texture,
    width: u32,
    height: u32,
    depth: ?gfx.rhi.Texture = null,
    depth_size: [2]u32 = .{ 0, 0 },

    shared: ?Shared = null,

    const Shared = struct {
        depth: gfx.rhi.Texture,
        staging: gfx.rhi.Buffer,
    };

    fn makeTargets(context: *Context, width: u32, height: u32, share: bool) !void {
        const renderer = context.renderer;
        if (!share) {
            context.target = try renderer.views.createTarget(width, height);
            context.shared = null;
            return;
        }
        const color = try renderer.views.createSharedTarget(width, height, .rgba8_unorm);
        errdefer renderer.views.destroyTarget(color);
        const depth = try renderer.views.createSharedTarget(width, height, .r32_float);
        errdefer renderer.views.destroyTarget(depth);
        const staging = try renderer.device.createBuffer(.{
            .name = "shared depth staging",
            .size = @as(u64, width) * height * 4,
            .usage = .{ .copy_src = true },
        });
        context.target = color;
        context.shared = .{ .depth = depth, .staging = staging };
    }

    fn destroyTargets(context: *Context) void {
        const renderer = context.renderer;
        renderer.views.destroyTarget(context.target);
        if (context.shared) |shared| {
            renderer.views.destroyTarget(shared.depth);
            renderer.device.destroyBuffer(shared.staging);
        }
        context.shared = null;
    }

    fn keepDepth(opaque_context: ?*anyopaque, pass: gfx.PassContext) anyerror!void {
        const context: *Context = @ptrCast(@alignCast(opaque_context.?));
        context.depth = pass.depth;
        const depth_info = pass.device.textureInfo(pass.depth);
        context.depth_size = .{ depth_info.width, depth_info.height };
        if (context.shared) |shared| {
            const width = @min(depth_info.width, context.width);
            const height = @min(depth_info.height, context.height);
            context.depth_size = .{ width, height };
            pass.cmd.copyDepthToColor(pass.depth, shared.staging, shared.depth, width, height);
        }
    }
};

pub const Options = extern struct {
    width: u32,
    height: u32,
    flags: u32 = ray_tracing,
    worker_threads: u32 = 0,
    pipeline_cache_path: ?[*:0]const u8 = null,
    preferred_device: ?[*:0]const u8 = null,

    pub const validation: u32 = 1 << 0;
    pub const debug_names: u32 = 1 << 1;
    pub const ray_tracing: u32 = 1 << 2;
    pub const path_tracing_fallback: u32 = 1 << 3;
    pub const mesh_shaders: u32 = 1 << 4;
    pub const shared_images: u32 = 1 << 5;
};

pub const Info = extern struct {
    vendor_id: u32,
    ray_tracing: u32,
    path_tracing: u32,
    dlss_super_resolution: u32,
    dlss_ray_reconstruction: u32,
    recommended_quality: u32,
    gpu_memory_bytes: u64,
};

pub const Image = extern struct {
    index: u32,
    width: u32,
    height: u32,
    id: u32,
};

pub const Material = extern struct {
    base_color: [4]f32 = .{ 1, 1, 1, 1 },
    emissive: [3]f32 = .{ 0, 0, 0 },
    metallic: f32 = 0,
    roughness: f32 = 0.6,
    alpha_cutoff: f32 = 0.5,
    transmission: f32 = 0,
    ior: f32 = 1.5,
    alpha_mode: u32 = alpha_opaque,
    flags: u32 = 0,
    sway: f32 = 0,

    pub const alpha_opaque: u32 = 0;
    pub const alpha_mask: u32 = 1;
    pub const alpha_blend: u32 = 2;
    pub const double_sided: u32 = 1 << 0;
};

pub const Mesh = extern struct {
    positions: [*]const [3]f32,
    normals: ?[*]const [3]f32 = null,
    uvs: ?[*]const [2]f32 = null,
    colors: ?[*]const [4]f32 = null,
    uvs1: ?[*]const [2]f32 = null,
    indices: [*]const u32,
    vertex_count: u32,
    index_count: u32,
    material: Material = .{},
};

pub const MaterialTextures = extern struct {
    base_color: ?*const Image = null,
    normal: ?*const Image = null,
    metallic_roughness: ?*const Image = null,
    occlusion: ?*const Image = null,
    emissive: ?*const Image = null,
    flags: u32 = 0,

    pub const nearest: u32 = 1 << 0;
};

pub const Sun = extern struct {
    direction: [3]f32,
    color: [3]f32 = .{ 1, 0.96, 0.9 },
    intensity: f32 = 0,
};

pub const Sky = extern struct {
    sun_direction: [3]f32,
    moon_direction: [3]f32 = .{ 0.3, -0.6, 0.5 },
    ground_color: [3]f32 = .{ 0.25, 0.23, 0.2 },
    turbidity: f32 = 2.5,
    intensity: f32 = 1,
    stars: f32 = 0,
    moon: f32 = 0,
    sun_disc: u32 = 1,
};

pub const Clouds = extern struct {
    coverage: f32 = 0.5,
    density: f32 = 0.012,
    bottom: f32 = 1500,
    thickness: f32 = 1400,
    scale: f32 = 1,
    wind: [3]f32 = .{ 12, 0, 5 },
};

pub const Light = extern struct {
    position: [3]f32,
    color: [3]f32 = .{ 1, 1, 1 },
    intensity: f32 = 1,
    range: f32 = 5,
};

pub const SharedImages = extern struct {
    color_handle: i64,
    depth_handle: i64,
    color_bytes: u64,
    depth_bytes: u64,
    width: u32,
    height: u32,
};

pub const FluidSource = extern struct {
    position: [3]f32,
    radius: f32 = 0.07,
    velocity: [3]f32 = .{ 0, 0.5, 0 },
    smoke: f32 = 0,
    fuel: f32 = 0,
    temperature: f32 = 0,
};

pub const Fluid = extern struct {
    transform: [16]f32,
    sources: [8]FluidSource,
    source_count: u32,
    resolution: [3]u32 = .{ 64, 96, 64 },
    flags: u32 = 0,
    wind: [3]f32 = .{ 0, 0, 0 },

    pub const scene_obstacles: u32 = 1 << 0;

    fn desc(self: *const Fluid, sources: *[8]gfx.FluidSource) gfx.FluidDesc {
        const count = @min(self.source_count, 8);
        for (sources[0..count], self.sources[0..count]) |*to, from| to.* = .{
            .position = from.position,
            .radius = from.radius,
            .velocity = from.velocity,
            .smoke = from.smoke,
            .fuel = from.fuel,
            .temperature = from.temperature,
        };
        return .{
            .resolution = self.resolution,
            .transform = self.transform,
            .sources = sources[0..count],
            .scene_obstacles = self.flags & scene_obstacles != 0,
            .walls = .open,
            .wind = self.wind,
        };
    }
};

pub const Emitter = extern struct {
    position: [3]f32,
    radius: f32 = 0.1,
    rate: f32 = 100,
    lifetime: [2]f32 = .{ 1, 2 },
    direction: [3]f32 = .{ 0, 1, 0 },
    spread: f32 = 0.4,
    speed: [2]f32 = .{ 1, 2 },
    gravity: [3]f32 = .{ 0, 0, 0 },
    drag: f32 = 0.5,
    size: [2]f32 = .{ 0.05, 0.2 },
    color_start: [4]f32 = .{ 1, 1, 1, 1 },
    color_end: [4]f32 = .{ 1, 1, 1, 0 },
    stretch: f32 = 0,
    bounce: f32 = 0.4,
    capacity: u32 = 1024,
    flags: u32 = lit,

    pub const lit: u32 = 1 << 0;
    pub const collide: u32 = 1 << 1;
    pub const additive: u32 = 1 << 2;

    fn desc(self: *const Emitter) gfx.EmitterDesc {
        return .{
            .position = self.position,
            .radius = self.radius,
            .capacity = self.capacity,
            .rate = self.rate,
            .lifetime = self.lifetime,
            .direction = self.direction,
            .spread = self.spread,
            .speed = self.speed,
            .gravity = self.gravity,
            .drag = self.drag,
            .size = self.size,
            .color_start = self.color_start,
            .color_end = self.color_end,
            .lit = self.flags & lit != 0,
            .blend = if (self.flags & additive != 0) .additive else .alpha,
            .collide = self.flags & collide != 0,
            .bounce = self.bounce,
            .stretch = self.stretch,
        };
    }
};

pub const Water = extern struct {
    transform: [16]f32,
    color: [3]f32 = .{ 0.02, 0.1, 0.12 },
    murk: f32 = 0.5,
    roughness: f32 = 0.04,
    refraction: f32 = 1.2,
    foam: f32 = 0.6,
    ripple_detail: f32 = 0.5,
    wave_speed: f32 = 3,
    damping: f32 = 0.5,
    rain: f32 = 0,
    swell: f32 = 0,
    swell_length: f32 = 6,
    resolution: [2]u32 = .{ 256, 256 },
    mask: ?*const Image = null,
    flags: u32 = 0,

    pub const write_depth: u32 = 1 << 0;
    pub const underwater: u32 = 1 << 1;

    fn desc(self: *const Water) gfx.WaterDesc {
        return .{
            .resolution = self.resolution,
            .transform = self.transform,
            .wave_speed = self.wave_speed,
            .damping = self.damping,
            .rain = self.rain,
            .swell = self.swell,
            .swell_length = self.swell_length,
            .color = self.color,
            .murk = self.murk,
            .roughness = self.roughness,
            .refraction = self.refraction,
            .foam = self.foam,
            .caustics = 0,
            .ripple_detail = self.ripple_detail,
            .underwater = self.flags & underwater != 0,
            .write_depth = self.flags & write_depth != 0,
            .mask = imageOrNull(self.mask),
        };
    }
};

pub const Camera = extern struct {
    position: [3]f32,
    forward: [3]f32,
    up: [3]f32 = .{ 0, 1, 0 },
    fov_y: f32,
    near: f32 = 0.05,
};

pub const Settings = extern struct {
    quality: u32 = 2,
    upscaling: u32 = 1,
    flags: u32 = defaults,
    path_tracing_bounces: u32 = 4,
    path_tracing_samples: u32 = 1,
    render_scale: f32 = 1,
    exposure_compensation: f32 = 0,
    bloom: f32 = 0.04,
    fog_density: f32 = 0,
    shadow_distance: f32 = 60,
    debug_view: u32 = 0,
    path_tracing_clamp: f32 = 12,
    aerial_perspective: f32 = 0,
    exposure_speed: f32 = 3,

    pub const shadows: u32 = 1 << 0;
    pub const ambient_occlusion: u32 = 1 << 1;
    pub const global_illumination: u32 = 1 << 2;
    pub const screen_space_reflections: u32 = 1 << 3;
    pub const ray_traced_reflections: u32 = 1 << 4;
    pub const ray_traced_light_shadows: u32 = 1 << 5;
    pub const clouds: u32 = 1 << 6;
    pub const fluids: u32 = 1 << 7;
    pub const temporal_antialiasing: u32 = 1 << 8;
    pub const path_tracing: u32 = 1 << 9;
    pub const path_tracing_denoise: u32 = 1 << 10;
    pub const frame_generation: u32 = 1 << 11;
    pub const automatic_exposure: u32 = 1 << 12;
    pub const defaults: u32 = shadows | ambient_occlusion | global_illumination | screen_space_reflections |
        ray_traced_reflections | ray_traced_light_shadows | clouds | fluids | temporal_antialiasing |
        path_tracing_denoise | automatic_exposure;

    fn resolve(self: Settings) !gfx.Settings {
        var settings = gfx.Settings.preset(try enumOf(gfx.Quality, self.quality));
        settings.shadows = self.has(shadows);
        settings.ambient_occlusion = self.has(ambient_occlusion);
        settings.global_illumination = settings.global_illumination and self.has(global_illumination);
        settings.screen_space_reflections = settings.screen_space_reflections and self.has(screen_space_reflections);
        settings.reflection_ray_tracing = self.has(ray_traced_reflections);
        settings.ray_traced_light_shadows = self.has(ray_traced_light_shadows);
        settings.clouds = self.has(clouds);
        settings.fluids = self.has(fluids);
        settings.temporal_antialiasing = self.has(temporal_antialiasing);
        settings.path_tracing = self.has(path_tracing);
        settings.path_tracing_denoise = self.has(path_tracing_denoise);
        settings.frame_generation = self.has(frame_generation);
        settings.automatic_exposure = self.has(automatic_exposure);
        settings.upscaling = try enumOf(gfx.Upscaling, self.upscaling);
        settings.path_tracing_bounces = self.path_tracing_bounces;
        settings.path_tracing_samples = self.path_tracing_samples;
        settings.render_scale = @min(settings.render_scale, self.render_scale);
        settings.exposure_compensation = self.exposure_compensation;
        settings.exposure_darkest = night_exposure_floor;
        settings.exposure_speed = self.exposure_speed;
        settings.bloom = self.bloom;
        settings.fog_density = self.fog_density;
        settings.shadow_distance = self.shadow_distance;
        settings.debug_view = try enumOf(gfx.DebugView, self.debug_view);
        settings.path_tracing_clamp = self.path_tracing_clamp;
        settings.aerial_perspective = self.aerial_perspective;
        return settings;
    }

    fn has(self: Settings, flag: u32) bool {
        return self.flags & flag != 0;
    }
};

pub const Frame = extern struct {
    scene: u64,
    camera: Camera,
    delta_time: f32,
    settings: Settings = .{},
};

threadlocal var error_name: [128]u8 = @splat(0);

fn fail(err: anyerror) void {
    const name = @errorName(err);
    const len = @min(name.len, error_name.len - 1);
    @memcpy(error_name[0..len], name[0..len]);
    error_name[len] = 0;
}

fn status(result: anyerror!void) i32 {
    result catch |err| {
        fail(err);
        return -1;
    };
    return 0;
}

fn handleOf(result: anytype) u64 {
    const handle = result catch |err| {
        fail(err);
        return 0;
    };
    return @bitCast(handle);
}

fn enumOf(comptime T: type, value: u32) !T {
    return std.enums.fromInt(T, value) orelse error.InvalidEnum;
}

fn textOf(arena: std.mem.Allocator, text: ?[*:0]const u8) !?[]const u8 {
    return if (text) |value| try arena.dupe(u8, std.mem.span(value)) else null;
}

const deep_stack_bytes = 16 * 1024 * 1024;

fn onDeepStack(comptime function: anytype, arguments: anytype) @typeInfo(@TypeOf(function)).@"fn".return_type.? {
    const Result = @typeInfo(@TypeOf(function)).@"fn".return_type.?;
    const Call = struct {
        fn run(result: *Result, inner: @TypeOf(arguments)) void {
            result.* = @call(.auto, function, inner);
        }
    };
    var result: Result = undefined;
    const thread = std.Thread.spawn(.{ .stack_size = deep_stack_bytes }, Call.run, .{ &result, arguments }) catch
        return @call(.auto, function, arguments);
    thread.join();
    return result;
}

export fn limn_mc_abi_version() u32 {
    return abi_version;
}

export fn limn_mc_last_error() [*:0]const u8 {
    return @ptrCast(&error_name);
}

export fn limn_mc_struct_sizes(sizes: [*]u32, capacity: u32) u32 {
    const all = [_]u32{
        @sizeOf(Options),  @sizeOf(Info),             @sizeOf(Image),
        @sizeOf(Material), @sizeOf(Mesh),             @sizeOf(MaterialTextures),
        @sizeOf(Sun),      @sizeOf(Sky),              @sizeOf(Camera),
        @sizeOf(Settings), @sizeOf(Frame),            @sizeOf(Clouds),
        @sizeOf(Light),            @sizeOf(SharedImages),
        @sizeOf(FluidSource),      @sizeOf(Fluid),
        @sizeOf(Emitter),          @sizeOf(Water),
    };
    const count = @min(all.len, capacity);
    @memcpy(sizes[0..count], all[0..count]);
    return all.len;
}

export fn limn_mc_create(options: *const Options) ?*Context {
    return onDeepStack(create, .{options}) catch |err| {
        fail(err);
        return null;
    };
}

fn create(options: *const Options) !*Context {
    const context = try gpa.create(Context);
    errdefer gpa.destroy(context);
    context.strings = .init(gpa);
    errdefer context.strings.deinit();
    const arena = context.strings.allocator();
    context.threaded = .init(gpa, .{});
    errdefer context.threaded.deinit();
    const renderer = try gfx.Renderer.init(gpa, context.threaded.io(), .{
        .application_name = "limn-mc",
        .validation = options.flags & Options.validation != 0,
        .debug_names = options.flags & Options.debug_names != 0,
        .ray_tracing = options.flags & Options.ray_tracing != 0,
        .path_tracing_fallback = options.flags & Options.path_tracing_fallback != 0,
        .mesh_shaders = options.flags & Options.mesh_shaders != 0,
        .worker_threads = options.worker_threads,
        .pipeline_cache_path = try textOf(arena, options.pipeline_cache_path),
        .preferred_device = try textOf(arena, options.preferred_device),
        .texture_compression = .none,
        .mesh_lods = false,
    });
    errdefer renderer.deinit();
    context.renderer = renderer;
    context.width = @max(options.width, 1);
    context.height = @max(options.height, 1);
    context.depth = null;
    context.depth_size = .{ 0, 0 };
    context.shared = null;
    try context.makeTargets(context.width, context.height, options.flags & Options.shared_images != 0 and renderer.device.shared_textures);
    return context;
}

export fn limn_mc_destroy(context: *Context) void {
    onDeepStack(destroy, .{context});
}

fn destroy(context: *Context) void {
    context.renderer.device.waitIdle() catch {};
    context.destroyTargets();
    context.renderer.deinit();
    context.threaded.deinit();
    context.strings.deinit();
    gpa.destroy(context);
}

export fn limn_mc_info(context: *Context, info: *Info) void {
    const renderer = context.renderer;
    const adapter = renderer.device.adapterInfo();
    const dlss = renderer.dlssSupport();
    info.* = .{
        .vendor_id = adapter.vendor_id,
        .ray_tracing = @intFromBool(adapter.ray_tracing),
        .path_tracing = switch (renderer.pathTracing()) {
            .unavailable => 0,
            .hardware => 1,
            .shader => 2,
        },
        .dlss_super_resolution = @intFromBool(dlss.super_resolution),
        .dlss_ray_reconstruction = @intFromBool(dlss.ray_reconstruction),
        .recommended_quality = @intFromEnum(renderer.recommendedQuality()),
        .gpu_memory_bytes = adapter.memory_bytes,
    };
}

export fn limn_mc_resize(context: *Context, width: u32, height: u32) i32 {
    return status(onDeepStack(resize, .{ context, @max(width, 1), @max(height, 1) }));
}

fn resize(context: *Context, width: u32, height: u32) !void {
    if (width == context.width and height == context.height) return;
    context.depth = null;
    const renderer = context.renderer;
    const share = context.shared != null;
    try renderer.device.waitIdle();
    context.destroyTargets();
    try context.makeTargets(width, height, share);
    context.width = width;
    context.height = height;
}

export fn limn_mc_scene_create(context: *Context) u64 {
    return handleOf(context.renderer.scenes.create());
}

export fn limn_mc_scene_destroy(context: *Context, scene: u64) void {
    context.renderer.scenes.destroy(@bitCast(scene));
}

export fn limn_mc_scene_set_sun(context: *Context, scene: u64, sun: *const Sun) void {
    context.renderer.scenes.setSun(@bitCast(scene), .{
        .direction = sun.direction,
        .color = sun.color,
        .intensity = sun.intensity,
    });
}

export fn limn_mc_scene_shift(context: *Context, scene: u64, offset: *const [3]f32) i32 {
    return status(context.renderer.scenes.shift(@bitCast(scene), offset.*));
}

export fn limn_mc_scene_set_sky(context: *Context, scene: u64, sky: u64, intensity: f32) void {
    const environment: ?gfx.Environment = if (sky == 0) null else @bitCast(sky);
    context.renderer.scenes.setEnvironment(@bitCast(scene), environment, intensity);
}

fn skyDesc(sky: *const Sky) gfx.SkyDesc {
    return .{
        .sun_direction = sky.sun_direction,
        .moon_direction = sky.moon_direction,
        .ground_color = sky.ground_color,
        .turbidity = sky.turbidity,
        .intensity = sky.intensity,
        .stars = sky.stars,
        .moon = sky.moon,
        .sun_disc = sky.sun_disc != 0,
    };
}

export fn limn_mc_scene_set_clouds(context: *Context, scene: u64, clouds: ?*const Clouds) i32 {
    const desc: ?gfx.CloudDesc = if (clouds) |value| .{
        .coverage = value.coverage,
        .density = value.density,
        .bottom = value.bottom,
        .thickness = value.thickness,
        .scale = value.scale,
        .wind = value.wind,
    } else null;
    return status(context.renderer.scenes.setClouds(@bitCast(scene), desc));
}

export fn limn_mc_scene_set_lights(context: *Context, scene: u64, lights: [*]const Light, count: u32) i32 {
    return status(setLights(context, scene, lights[0..count]));
}

fn setLights(context: *Context, scene: u64, lights: []const Light) !void {
    const descs = try gpa.alloc(gfx.Light, lights.len);
    defer gpa.free(descs);
    for (descs, lights) |*desc, light| desc.* = .{
        .position = light.position,
        .color = light.color,
        .intensity = light.intensity,
        .range = light.range,
        .source_radius = block_light_radius,
    };
    try context.renderer.scenes.setLights(@bitCast(scene), descs);
}

export fn limn_mc_fluid_create(context: *Context, scene: u64, fluid: *const Fluid) u64 {
    var sources: [8]gfx.FluidSource = undefined;
    return handleOf(context.renderer.fluids.create(@bitCast(scene), fluid.desc(&sources)));
}

export fn limn_mc_fluid_set(context: *Context, handle: u64, fluid: *const Fluid) i32 {
    var sources: [8]gfx.FluidSource = undefined;
    return status(context.renderer.fluids.set(@bitCast(handle), fluid.desc(&sources)));
}

export fn limn_mc_fluid_destroy(context: *Context, handle: u64) void {
    context.renderer.fluids.destroy(@bitCast(handle));
}

export fn limn_mc_emitter_create(context: *Context, scene: u64, emitter: *const Emitter) u64 {
    return handleOf(context.renderer.emitters.create(@bitCast(scene), emitter.desc()));
}

export fn limn_mc_emitter_set(context: *Context, handle: u64, emitter: *const Emitter) void {
    context.renderer.emitters.set(@bitCast(handle), emitter.desc());
}

export fn limn_mc_emitter_burst(context: *Context, handle: u64, count: u32) void {
    context.renderer.emitters.burst(@bitCast(handle), count);
}

export fn limn_mc_emitter_destroy(context: *Context, handle: u64) void {
    context.renderer.emitters.destroy(@bitCast(handle));
}

export fn limn_mc_water_create(context: *Context, scene: u64, water: *const Water) u64 {
    return handleOf(context.renderer.waters.create(@bitCast(scene), water.desc()));
}

export fn limn_mc_water_set(context: *Context, handle: u64, water: *const Water) i32 {
    return status(context.renderer.waters.set(@bitCast(handle), water.desc()));
}

export fn limn_mc_water_ripple(context: *Context, handle: u64, x: f32, y: f32, z: f32, radius: f32, depth: f32) void {
    context.renderer.waters.addRipple(@bitCast(handle), .{ x, y, z }, radius, depth);
}

export fn limn_mc_water_destroy(context: *Context, handle: u64) void {
    context.renderer.waters.destroy(@bitCast(handle));
}

export fn limn_mc_sky_create(context: *Context, sky: *const Sky) u64 {
    return handleOf(context.renderer.environments.createSky(skyDesc(sky)));
}

export fn limn_mc_sky_set(context: *Context, handle: u64, sky: *const Sky) void {
    context.renderer.environments.setSky(@bitCast(handle), skyDesc(sky));
}

export fn limn_mc_sky_destroy(context: *Context, handle: u64) void {
    context.renderer.environments.destroy(@bitCast(handle));
}

export fn limn_mc_sky_sun(sky: *const Sky, sun: *Sun) void {
    const matching = gfx.skySun(skyDesc(sky));
    sun.* = .{ .direction = matching.direction, .color = matching.color, .intensity = matching.intensity };
}

export fn limn_mc_image_create(context: *Context, width: u32, height: u32, pixels: [*]const u8, srgb: u32, levels: u32, image: *Image) i32 {
    const size = @as(usize, width) * height * 4;
    const created = context.renderer.images.createLimited(width, height, pixels[0..size], srgb != 0, levels) catch |err| {
        fail(err);
        return -1;
    };
    image.* = .{ .index = created.index, .width = created.width, .height = created.height, .id = created.id };
    return 0;
}

export fn limn_mc_image_update(context: *Context, image: *const Image, x: u32, y: u32, width: u32, height: u32, pixels: [*]const u8) i32 {
    const size = @as(usize, width) * height * 4;
    return status(context.renderer.images.update(imageOf(image), .{ x, y, width, height }, pixels[0..size]));
}

export fn limn_mc_image_destroy(context: *Context, image: *const Image) void {
    context.renderer.images.destroy(imageOf(image));
}

fn imageOf(image: *const Image) gfx.Image {
    return .{ .index = image.index, .width = image.width, .height = image.height, .id = image.id };
}

fn imageOrNull(image: ?*const Image) ?gfx.Image {
    return if (image) |value| imageOf(value) else null;
}

export fn limn_mc_model_create(context: *Context, meshes: [*]const Mesh, count: u32) u64 {
    return handleOf(modelCreate(context, meshes[0..count]));
}

fn modelCreate(context: *Context, meshes: []const Mesh) !gfx.Model {
    const descs = try gpa.alloc(gfx.MeshDesc, meshes.len);
    defer gpa.free(descs);
    for (descs, meshes) |*desc, mesh| {
        const vertices = mesh.vertex_count;
        const material = mesh.material;
        desc.* = .{
            .positions = mesh.positions[0..vertices],
            .normals = if (mesh.normals) |values| values[0..vertices] else null,
            .uvs = if (mesh.uvs) |values| values[0..vertices] else null,
            .colors = if (mesh.colors) |values| values[0..vertices] else null,
            .uvs1 = if (mesh.uvs1) |values| values[0..vertices] else null,
            .indices = mesh.indices[0..mesh.index_count],
            .material = .{
                .base_color = material.base_color,
                .emissive = material.emissive,
                .metallic = material.metallic,
                .roughness = material.roughness,
                .alpha_cutoff = material.alpha_cutoff,
                .alpha_mode = try enumOf(@FieldType(gfx.Material, "alpha_mode"), material.alpha_mode),
                .double_sided = material.flags & Material.double_sided != 0,
                .transmission = material.transmission,
                .ior = material.ior,
                .sway = material.sway,
                .sway_weights = material.sway != 0,
            },
        };
    }
    return context.renderer.models.create(descs);
}

export fn limn_mc_model_destroy(context: *Context, model: u64) i32 {
    return status(context.renderer.models.destroy(@bitCast(model)));
}

export fn limn_mc_model_set_textures(context: *Context, model: u64, material: i32, textures: *const MaterialTextures) i32 {
    return status(context.renderer.materials.setTextures(@bitCast(model), if (material < 0) null else @intCast(material), .{
        .base_color = imageOrNull(textures.base_color),
        .normal = imageOrNull(textures.normal),
        .metallic_roughness = imageOrNull(textures.metallic_roughness),
        .occlusion = imageOrNull(textures.occlusion),
        .emissive = imageOrNull(textures.emissive),
        .sampler = if (textures.flags & MaterialTextures.nearest != 0) .{ .linear = false, .repeat_u = .clamp_to_edge, .repeat_v = .clamp_to_edge } else null,
    }));
}

export fn limn_mc_entity_spawn(context: *Context, scene: u64, model: u64, transform: *const [16]f32) u64 {
    return handleOf(context.renderer.entities.spawn(@bitCast(scene), .{ .model = @bitCast(model), .transform = transform.* }));
}

export fn limn_mc_entity_despawn(context: *Context, entity: u64) void {
    context.renderer.entities.despawn(@bitCast(entity));
}

export fn limn_mc_entity_set_tint(context: *Context, entity: u64, red: f32, green: f32, blue: f32) void {
    context.renderer.entities.setTint(@bitCast(entity), .{ red, green, blue });
}

export fn limn_mc_model_traced(context: *Context, model: u64) u32 {
    const renderer = context.renderer;
    if (!renderer.device.ray_tracing) return 1;
    renderer.lock();
    defer renderer.unlock();
    const entry = renderer.models.table.get(@bitCast(model)) orelse return 1;
    if (entry.state != .ready) return @intFromBool(entry.state == .failed);
    for (entry.meshes) |mesh| if (mesh.blas == null and mesh.skin_offset == null) return 0;
    return 1;
}

export fn limn_mc_entity_set_visible(context: *Context, entity: u64, visible: u32) void {
    context.renderer.entities.setVisible(@bitCast(entity), visible != 0);
}

export fn limn_mc_entity_set_transforms(context: *Context, entities: [*]const u64, transforms: [*]const [16]f32, count: u32) void {
    const handles: [*]const gfx.Entity = @ptrCast(entities);
    context.renderer.entities.setTransforms(handles[0..count], transforms[0..count]);
}

export fn limn_mc_render(context: *Context, frame: *const Frame) i32 {
    return status(onDeepStack(render, .{ context, frame }));
}

fn render(context: *Context, frame: *const Frame) !void {
    const camera = frame.camera;
    _ = try context.renderer.render(.{
        .views = &.{.{
            .scene = if (frame.scene == 0) null else @bitCast(frame.scene),
            .camera = .{
                .position = camera.position,
                .forward = camera.forward,
                .up = camera.up,
                .fov_y = camera.fov_y,
                .near = camera.near,
            },
            .target = .{ .texture = context.target },
            .passes = &.{.{ .stage = .after_tonemap, .context = context, .run = Context.keepDepth }},
            .settings = try frame.settings.resolve(),
        }},
        .delta_time = frame.delta_time,
    });
    if (context.shared != null) try context.renderer.device.waitIdle();
}

export fn limn_mc_read_pixels(context: *Context, pixels: [*]u8, size: usize) i32 {
    return status(readPixels(context, pixels[0..size]));
}

fn readPixels(context: *Context, pixels: []u8) !void {
    if (pixels.len != @as(usize, context.width) * context.height * 4) return error.InvalidSize;
    const read = try context.renderer.device.readTexture(gpa, context.target);
    defer gpa.free(read);
    @memcpy(pixels, read);
}

export fn limn_mc_shared_images(context: *Context, images: *SharedImages) i32 {
    return status(sharedImages(context, images));
}

fn sharedImages(context: *Context, images: *SharedImages) !void {
    const shared = context.shared orelse return error.NotShared;
    const device = context.renderer.device;
    const color = try device.exportTexture(context.target);
    const depth = try device.exportTexture(shared.depth);
    images.* = .{
        .color_handle = color.handle,
        .depth_handle = depth.handle,
        .color_bytes = color.size,
        .depth_bytes = depth.size,
        .width = context.width,
        .height = context.height,
    };
}

export fn limn_mc_diagnostics(context: *Context, values: *[4]u32) void {
    const stats = context.renderer.getStats();
    values.* = .{ context.renderer.blas_pending, stats.path_traced_frames, context.renderer.path_trace_misses, stats.instances };
}

export fn limn_mc_depth_size(context: *Context, size: *[2]u32) void {
    size.* = if (context.depth == null) .{ 0, 0 } else context.depth_size;
}

export fn limn_mc_read_depth(context: *Context, depth: [*]f32, count: usize) i32 {
    return status(readDepth(context, depth[0..count]));
}

fn readDepth(context: *Context, depth: []f32) !void {
    const texture = context.depth orelse return error.NoDepth;
    if (depth.len != @as(usize, context.depth_size[0]) * context.depth_size[1]) return error.InvalidSize;
    const read = try context.renderer.device.readTexture(gpa, texture);
    defer gpa.free(read);
    if (read.len != depth.len * @sizeOf(f32)) return error.UnsupportedDepthFormat;
    @memcpy(std.mem.sliceAsBytes(depth), read);
}

test "a cube is drawn without a window" {
    const context = limn_mc_create(&.{ .width = 160, .height = 90, .flags = 0 }) orelse {
        std.debug.print("limn_mc_create: {s}\n", .{limn_mc_last_error()});
        return error.CreateFailed;
    };
    defer limn_mc_destroy(context);

    var info: Info = undefined;
    limn_mc_info(context, &info);

    var positions: [24][3]f32 = undefined;
    var indices: [36]u32 = undefined;
    for (0..6) |face| {
        const axis = face / 2;
        const sign: f32 = if (face % 2 == 0) 1 else -1;
        const u = (axis + 1) % 3;
        const v = (axis + 2) % 3;
        for (0..4) |corner| {
            var p: [3]f32 = undefined;
            p[axis] = sign;
            p[u] = (if (corner == 1 or corner == 2) @as(f32, 1) else -1) * sign;
            p[v] = if (corner >= 2) 1 else -1;
            positions[face * 4 + corner] = p;
        }
        const base: u32 = @intCast(face * 4);
        indices[face * 6 ..][0..6].* = .{ base, base + 1, base + 2, base, base + 2, base + 3 };
    }
    const model = limn_mc_model_create(context, &.{.{
        .positions = &positions,
        .indices = &indices,
        .vertex_count = positions.len,
        .index_count = indices.len,
        .material = .{ .base_color = .{ 0.9, 0.2, 0.1, 1 } },
    }}, 1);
    try std.testing.expect(model != 0);

    const white = [_]u8{ 255, 255, 255, 255 } ** 4;
    var image: Image = undefined;
    try std.testing.expectEqual(0, limn_mc_image_create(context, 2, 2, &white, 1, 0, &image));
    try std.testing.expectEqual(0, limn_mc_model_set_textures(context, model, -1, &.{ .base_color = &image }));

    const scene = limn_mc_scene_create(context);
    try std.testing.expect(scene != 0);
    const sky_desc = Sky{ .sun_direction = .{ -0.4, -1.0, -0.3 } };
    const sky = limn_mc_sky_create(context, &sky_desc);
    try std.testing.expect(sky != 0);
    limn_mc_scene_set_sky(context, scene, sky, 1);
    var sun: Sun = undefined;
    limn_mc_sky_sun(&sky_desc, &sun);
    limn_mc_scene_set_sun(context, scene, &sun);
    const entity = limn_mc_entity_spawn(context, scene, model, &gfx.math.identity);
    try std.testing.expect(entity != 0);

    const frame = Frame{
        .scene = scene,
        .camera = .{ .position = .{ 3, 2, 4 }, .forward = .{ -3, -2, -4 }, .fov_y = 1.0 },
        .delta_time = 1.0 / 60.0,
    };
    for (0..8) |_| try std.testing.expectEqual(0, limn_mc_render(context, &frame));

    const pixels = try std.testing.allocator.alloc(u8, 160 * 90 * 4);
    defer std.testing.allocator.free(pixels);
    try std.testing.expectEqual(0, limn_mc_read_pixels(context, pixels.ptr, pixels.len));
    const middle = pixels[(45 * 160 + 80) * 4 ..][0..4];
    const corner = pixels[(4 * 160 + 4) * 4 ..][0..4];
    try std.testing.expect(middle[0] > middle[2]);
    try std.testing.expect(corner[2] >= corner[0]);

    var depth_size: [2]u32 = undefined;
    limn_mc_depth_size(context, &depth_size);
    try std.testing.expect(depth_size[0] > 0 and depth_size[1] > 0);
    const depth = try std.testing.allocator.alloc(f32, depth_size[0] * depth_size[1]);
    defer std.testing.allocator.free(depth);
    try std.testing.expectEqual(0, limn_mc_read_depth(context, depth.ptr, depth.len));
    try std.testing.expect(depth[(depth_size[1] / 2) * depth_size[0] + depth_size[0] / 2] > 0);
    try std.testing.expectEqual(0, depth[4 * depth_size[0] + 4]);

    try std.testing.expectEqual(0, limn_mc_resize(context, 64, 64));
    try std.testing.expectEqual(0, limn_mc_render(context, &frame));
    try std.testing.expectEqual(-1, limn_mc_read_pixels(context, pixels.ptr, pixels.len));
    try std.testing.expectEqualStrings("InvalidSize", std.mem.span(limn_mc_last_error()));

    limn_mc_entity_despawn(context, entity);
    try std.testing.expectEqual(0, limn_mc_model_destroy(context, model));
}

test "an atlas region colors a quad" {
    const context = limn_mc_create(&.{ .width = 64, .height = 64, .flags = 0 }) orelse return error.CreateFailed;
    defer limn_mc_destroy(context);

    const atlas = try std.testing.allocator.alloc(u8, 64 * 64 * 4);
    defer std.testing.allocator.free(atlas);
    @memset(atlas, 0);
    for (16..32) |y| for (32..48) |x| {
        atlas[(y * 64 + x) * 4 ..][0..4].* = .{ 255, 0, 0, 255 };
    };
    var image: Image = undefined;
    try std.testing.expectEqual(0, limn_mc_image_create(context, 64, 64, atlas.ptr, 1, 4, &image));

    const positions = [_][3]f32{ .{ -4, 0, -4 }, .{ -4, 0, 4 }, .{ 4, 0, 4 }, .{ 4, 0, -4 } };
    const uvs = [_][2]f32{ .{ 0.5, 0.25 }, .{ 0.5, 0.5 }, .{ 0.75, 0.5 }, .{ 0.75, 0.25 } };
    const colors = [_][4]f32{.{ 1, 1, 1, 1 }} ** 4;
    const indices = [_]u32{ 0, 1, 2, 0, 2, 3 };
    const model = limn_mc_model_create(context, &.{.{
        .positions = &positions,
        .uvs = &uvs,
        .colors = &colors,
        .indices = &indices,
        .vertex_count = 4,
        .index_count = 6,
        .material = .{ .roughness = 0.9 },
    }}, 1);
    try std.testing.expect(model != 0);
    try std.testing.expectEqual(0, limn_mc_model_set_textures(context, model, -1, &.{ .base_color = &image }));

    const scene = limn_mc_scene_create(context);
    const sky_desc = Sky{ .sun_direction = .{ -0.3, -1.0, -0.2 } };
    const sky = limn_mc_sky_create(context, &sky_desc);
    limn_mc_scene_set_sky(context, scene, sky, 1);
    var sun: Sun = undefined;
    limn_mc_sky_sun(&sky_desc, &sun);
    limn_mc_scene_set_sun(context, scene, &sun);
    _ = limn_mc_entity_spawn(context, scene, model, &gfx.math.identity);

    const frame = Frame{
        .scene = scene,
        .camera = .{ .position = .{ 0, 3, 0.01 }, .forward = .{ 0, -1, -0.003 }, .fov_y = 1.0 },
        .delta_time = 1.0 / 60.0,
        .settings = .{ .debug_view = 1 },
    };
    for (0..8) |_| try std.testing.expectEqual(0, limn_mc_render(context, &frame));
    var pixels: [64 * 64 * 4]u8 = undefined;
    try std.testing.expectEqual(0, limn_mc_read_pixels(context, &pixels, pixels.len));
    const middle = pixels[(32 * 64 + 32) * 4 ..][0..4];
    std.debug.print("albedo {any}\n", .{middle.*});
    try std.testing.expect(middle[0] > 200 and middle[1] < 40);
}

test "shared images are exported" {
    const context = limn_mc_create(&.{ .width = 64, .height = 64, .flags = Options.shared_images }) orelse return error.CreateFailed;
    defer limn_mc_destroy(context);
    var images: SharedImages = undefined;
    if (limn_mc_shared_images(context, &images) != 0) return error.SkipZigTest;
    defer _ = std.c.close(@intCast(images.color_handle));
    defer _ = std.c.close(@intCast(images.depth_handle));
    try std.testing.expect(images.color_handle > 0 and images.depth_handle > 0);
    try std.testing.expect(images.color_bytes >= 64 * 64 * 4 and images.depth_bytes >= 64 * 64 * 4);

    const scene = limn_mc_scene_create(context);
    const frame = Frame{
        .scene = scene,
        .camera = .{ .position = .{ 0, 1, 3 }, .forward = .{ 0, 0, -1 }, .fov_y = 1.0 },
        .delta_time = 1.0 / 60.0,
    };
    for (0..4) |_| try std.testing.expectEqual(0, limn_mc_render(context, &frame));
    var pixels: [64 * 64 * 4]u8 = undefined;
    try std.testing.expectEqual(0, limn_mc_read_pixels(context, &pixels, pixels.len));
    try std.testing.expectEqual(0, limn_mc_resize(context, 32, 48));
    try std.testing.expectEqual(0, limn_mc_render(context, &frame));
}

test "the game's options start and render" {
    const context = limn_mc_create(&.{
        .width = 320,
        .height = 180,
        .flags = Options.ray_tracing | Options.shared_images,
    }) orelse return error.CreateFailed;
    defer limn_mc_destroy(context);
    var info: Info = undefined;
    limn_mc_info(context, &info);
    std.debug.print("dlss {d} ray tracing {d}\n", .{ info.dlss_super_resolution, info.ray_tracing });
    const scene = limn_mc_scene_create(context);
    var frame = Frame{
        .scene = scene,
        .camera = .{ .position = .{ 0, 1, 3 }, .forward = .{ 0, 0, -1 }, .fov_y = 1.0 },
        .delta_time = 1.0 / 60.0,
    };
    for (0..4) |_| if (limn_mc_render(context, &frame) != 0) {
        std.debug.print("render: {s}\n", .{limn_mc_last_error()});
        return error.RenderFailed;
    };
    if (info.dlss_super_resolution != 0) {
        frame.settings.upscaling = 5;
        frame.settings.render_scale = 0.5;
    for (0..4) |_| if (limn_mc_render(context, &frame) != 0) {
        std.debug.print("render: {s}\n", .{limn_mc_last_error()});
        return error.RenderFailed;
    };
    }
}

fn createOnThread(result: *?*Context) void {
    result.* = limn_mc_create(&.{ .width = 64, .height = 64, .flags = Options.ray_tracing });
}

test "starting on a thread with a small stack" {
    var result: ?*Context = null;
    const thread = try std.Thread.spawn(.{ .stack_size = 1024 * 1024 }, createOnThread, .{&result});
    thread.join();
    const context = result orelse return error.CreateFailed;
    limn_mc_destroy(context);
}
