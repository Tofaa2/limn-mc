const std = @import("std");

const dlss_sdk_version = "v310.9.1";

pub fn build(b: *std.Build) void {
    const target = b.standardTargetOptions(.{});
    const optimize = b.standardOptimizeOption(.{});

    const dlss_sdk = b.pathFromRoot("dlss_sdk");
    const dlss_libraries = switch (target.result.os.tag) {
        .windows => "lib/Windows_x86_64",
        else => "lib/Linux_x86_64",
    };
    const wants_dlss = b.option(bool, "dlss", "Build NVIDIA DLSS in (default: true where `zig build dlss-sdk` has fetched the SDK)") orelse true;
    const dlss = wants_dlss and target.result.os.tag == .linux and present(b, &.{ dlss_sdk, "include", "nvsdk_ngx.h" }) and present(b, &.{ dlss_sdk, dlss_libraries });
    const fetch_dlss = b.addSystemCommand(&.{ "git", "clone", "--depth", "1", "--branch", dlss_sdk_version, "--filter=blob:none", "--sparse", "https://github.com/NVIDIA/DLSS", dlss_sdk });
    const trim_dlss = b.addSystemCommand(&.{ "git", "-C", dlss_sdk, "sparse-checkout", "set", "include", "lib/Linux_x86_64" });
    trim_dlss.step.dependOn(&fetch_dlss.step);
    b.step("dlss-sdk", "Fetch NVIDIA's DLSS SDK into dlss_sdk, under NVIDIA's license").dependOn(&trim_dlss.step);

    const limn = b.dependency("limn", .{
        .target = target,
        .optimize = optimize,
        .dlss = dlss,
        .dlss_sdk = @as([]const u8, dlss_sdk),
    });

    const module = b.createModule(.{
        .root_source_file = b.path("src/root.zig"),
        .target = target,
        .optimize = optimize,
        .link_libc = true,
    });
    module.addImport("limn", limn.module("limn"));

    const library = b.addLibrary(.{ .name = "limn_mc", .root_module = module, .linkage = .dynamic, .use_llvm = true });
    b.installArtifact(library);

    const tests = b.addTest(.{ .root_module = module, .use_llvm = true });
    b.step("test", "Drive the C ABI without a window").dependOn(&b.addRunArtifact(tests).step);
}

fn present(b: *std.Build, parts: []const []const u8) bool {
    b.build_root.handle.access(b.graph.io, b.pathResolve(parts), .{}) catch return false;
    return true;
}
