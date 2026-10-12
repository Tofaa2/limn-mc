package io.github.tofaa2.limnmc.test;

import io.github.tofaa2.limnmc.render.LimnSettings;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

public final class WorldScreenshotTest implements FabricClientGameTest {
    private static void copyTree(Path from, Path to) {
        try (Stream<Path> paths = Files.walk(from)) {
            for (Path path : (Iterable<Path>) paths::iterator) {
                Path target = to.resolve(from.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static void runSavedWorld(ClientGameTestContext context, Path save) {
        TestWorldSave worldSave;
        try (
            TestSingleplayerContext world = context.worldBuilder()
                .setUseConsistentSettings(false)
                .adjustSettings(settings -> settings.setSeed(System.getenv("LIMN_SEED")))
                .create()
        ) {
            worldSave = world.getWorldSave();
        }
        copyTree(save.resolve("dimensions"), worldSave.getSaveDirectory().resolve("dimensions"));
        try (TestSingleplayerContext world = worldSave.open()) {
            context.runOnClient(minecraft -> {
                minecraft.options.renderDistance().set(16);
                LimnSettings.pathTracing = System.getenv("LIMN_RASTER") == null;
                LimnSettings.pathTracingSamples = 1;
                LimnSettings.pathTracingBounces = 4;
                LimnSettings.bloomPercent = 30;
                LimnSettings.automaticExposure = System.getenv("LIMN_AUTO") != null;
                LimnSettings.globalIllumination = System.getenv("LIMN_NO_GI") == null;
                LimnSettings.upscaling = System.getenv("LIMN_DLSS") == null ? 1 : 5;
                LimnSettings.quality = 3;
                LimnSettings.renderScalePercent = 100;
            });
            world.getServer().runCommand("time set 3000");
            world.getServer().runCommand("weather clear");
            world.getServer().runCommand("gamemode creative @p");
            String[] views = System.getenv("LIMN_VIEWS").split(";");
            for (int view = 0; view < views.length; view++) {
                world.getServer().runCommand("tp @p " + views[view]);
                world.getServer().runCommand("execute at @p run setblock ~ ~-1 ~ barrier");
                if (System.getenv("LIMN_GRASS") != null) {
                    world.getServer().runCommand("execute at @p run fill ~-8 ~-1 ~2 ~8 ~-1 ~24 grass_block");
                    world.getServer().runCommand("execute at @p run fill ~-8 ~ ~2 ~8 ~3 ~24 air");
                    world.getServer().runCommand("execute at @p run fill ~-8 ~ ~2 ~8 ~ ~24 short_grass");
                    if (System.getenv("LIMN_FLOWERS") != null) {
                        world.getServer().runCommand("execute at @p run fill ~-8 ~ ~2 ~8 ~ ~7 air");
                        String[] small = {"allium", "poppy", "blue_orchid", "oxeye_daisy", "cornflower", "lily_of_the_valley", "azure_bluet", "red_tulip", "dandelion"};
                        String[] more = {"wither_rose", "torchflower", "pink_petals[flower_amount=4]", "wildflowers[flower_amount=4]", "leaf_litter[segment_amount=4]", "fern", "oak_sapling", "pink_tulip", "orange_tulip"};
                        String[] tall = {"sunflower", "lilac", "rose_bush", "peony", "tall_grass", "large_fern"};
                        for (int index = 0; index < small.length; index++) {
                            world.getServer().runCommand("execute at @p run setblock ~" + (index - 4) + " ~ ~3 " + small[index]);
                            world.getServer().runCommand("execute at @p run setblock ~" + (index - 4) + " ~ ~4 " + more[index]);
                        }
                        for (int index = 0; index < tall.length; index++) {
                            world.getServer().runCommand("execute at @p run setblock ~" + (index * 2 - 5) + " ~ ~6 " + tall[index] + "[half=lower]");
                            world.getServer().runCommand("execute at @p run setblock ~" + (index * 2 - 5) + " ~1 ~6 " + tall[index] + "[half=upper]");
                        }
                        world.getServer().runCommand("execute at @p run fill ~-8 ~1 ~9 ~-5 ~3 ~10 oak_leaves[persistent=true]");
                    }
                }
                if (System.getenv("LIMN_ROOM") != null && view >= Integer.parseInt(System.getenv("LIMN_ROOM"))) {
                    world.getServer().runCommand("execute at @p run fill ~-4 ~-1 ~-4 ~4 ~4 ~4 stone");
                    world.getServer().runCommand("execute at @p run fill ~-3 ~ ~-3 ~3 ~3 ~3 air");
                    world.getServer().runCommand("execute at @p run setblock ~2 ~ ~2 torch");
                }
                if (System.getenv("LIMN_EARLY") != null) {
                    for (int shot = 0; shot < 6; shot++) {
                        context.waitTicks(shot == 0 ? 20 : 60);
                        context.takeScreenshot("save-" + view + "-early-" + shot);
                    }
                    continue;
                }
                context.waitTicks(view == 0 ? 400 : 200);
                if (System.getenv("LIMN_TIMES") != null) {
                    for (String time : System.getenv("LIMN_TIMES").split(",")) {
                        world.getServer().runCommand("time set " + time);
                        context.waitTicks(160);
                        context.takeScreenshot("save-" + view + "-time-" + time);
                    }
                    world.getServer().runCommand("time set 3000");
                    continue;
                }
                if (System.getenv("LIMN_STILL") != null) {
                    int shots = Integer.parseInt(System.getenv("LIMN_STILL"));
                    for (int shot = 0; shot < shots; shot++) {
                        if (System.getenv("LIMN_REBUILD") != null) {
                            context.waitTicks(1);
                            if (shot % 10 == 5) {
                                world.getServer().runCommand("execute at @p run setblock ~ ~-3 ~4 " + (shot % 20 == 5 ? "stone" : "dirt"));
                            }
                        } else {
                            context.waitTicks(5);
                        }
                        context.takeScreenshot("save-" + view + "-still-" + String.format("%03d", shot));
                    }
                    continue;
                }
                if (System.getenv("LIMN_NIGHT") != null) {
                    world.getServer().runCommand("time set night");
                    for (int shot = 0; shot < 4; shot++) {
                        context.waitTicks(100);
                        context.takeScreenshot("save-" + view + "-night-" + shot);
                    }
                    if (System.getenv("LIMN_NIGHT").equals("sunset")) {
                        world.getServer().runCommand("time set 3000");
                        continue;
                    }
                    context.takeScreenshot("save-" + view + "-night");
                    context.runOnClient(minecraft -> LimnSettings.globalIllumination = false);
                    context.waitTicks(100);
                    context.takeScreenshot("save-" + view + "-night-no-gi");
                    context.runOnClient(minecraft -> {
                        LimnSettings.globalIllumination = true;
                        LimnSettings.pathTracing = false;
                    });
                    context.waitTicks(100);
                    context.takeScreenshot("save-" + view + "-night-raster");
                    context.runOnClient(minecraft -> LimnSettings.pathTracing = true);
                    world.getServer().runCommand("time set 3000");
                    continue;
                }
                context.takeScreenshot("save-" + view + "-traced");
                context.waitTicks(15);
                context.takeScreenshot("save-" + view + "-traced-later");
                if (System.getenv("LIMN_QUICK") != null) {
                    context.runOnClient(minecraft -> LimnSettings.enabled = false);
                    context.waitTicks(40);
                    context.takeScreenshot("save-" + view + "-vanilla");
                    context.runOnClient(minecraft -> LimnSettings.enabled = true);
                    continue;
                }
                context.runOnClient(minecraft -> LimnSettings.volumetricWater = false);
                context.waitTicks(100);
                context.takeScreenshot("save-" + view + "-no-water");
                context.runOnClient(minecraft -> {
                    LimnSettings.volumetricWater = true;
                    LimnSettings.pathTracingDenoise = false;
                });
                context.waitTicks(60);
                context.takeScreenshot("save-" + view + "-raw");
                context.runOnClient(minecraft -> {
                    LimnSettings.pathTracingDenoise = true;
                    LimnSettings.pathTracingClamp = 0.5F;
                });
                context.waitTicks(100);
                context.takeScreenshot("save-" + view + "-clamped");
                context.runOnClient(minecraft -> {
                    LimnSettings.pathTracingClamp = 3.0F;
                    LimnSettings.pathTracing = false;
                });
                context.waitTicks(60);
                context.takeScreenshot("save-" + view + "-raster");
                context.runOnClient(minecraft -> LimnSettings.enabled = false);
                context.waitTicks(40);
                context.takeScreenshot("save-" + view + "-vanilla");
                context.runOnClient(minecraft -> {
                    LimnSettings.enabled = true;
                    LimnSettings.pathTracing = true;
                });
            }
        }
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        if (System.getenv("LIMN_SAVE") != null) {
            runSavedWorld(context, Path.of(System.getenv("LIMN_SAVE")));
            return;
        }
        try (
            TestSingleplayerContext world = context.worldBuilder()
                .setUseConsistentSettings(false)
                .adjustSettings(settings -> settings.setSeed("limn"))
                .create()
        ) {
            context.runOnClient(minecraft -> {
                minecraft.options.renderDistance().set(12);
                LimnSettings.pathTracing = false;
                LimnSettings.pathTracingSamples = 2;
                LimnSettings.quality = 3;
                LimnSettings.upscaling = 1;
                LimnSettings.renderScalePercent = 100;
            });
            world.getServer().runCommand("time set 3000");
            world.getServer().runCommand("weather clear");
            world.getServer().runCommand("gamemode creative @p");
            if ("holes".equals(System.getenv("LIMN_TEST"))) {
                if (System.getenv("LIMN_NO_ENTITIES") != null) {
                    context.runOnClient(minecraft -> LimnSettings.limnEntities = false);
                }
                if (System.getenv("LIMN_NO_WATER") != null) {
                    context.runOnClient(minecraft -> LimnSettings.volumetricWater = false);
                }
                context.runOnClient(minecraft -> LimnSettings.pathTracing = true);
                world.getServer().runCommand("tp @p 0 110 0 -90 30");
                context.waitTicks(100);
                for (int step = 0; step < (System.getenv("LIMN_NO_FLY") == null ? 80 : 0); step++) {
                    world.getServer().runCommand("tp @p " + (step * 6) + " 110 " + (step % 20 * 3) + " " + (step * 37 % 360 - 180) + " 30");
                    if (step % 4 == 0 && System.getenv("LIMN_NO_FILL") == null) {
                        world.getServer().runCommand("execute at @p run fill ~-3 ~-40 ~-3 ~3 ~-1 ~3 air");
                    }
                    context.waitTicks(6);
                }
                for (int spot = System.getenv("LIMN_NO_FLY") == null ? 0 : 2; spot < (System.getenv("LIMN_NO_FLY") == null ? 4 : 3); spot++) {
                    world.getServer().runCommand("tp @p " + (spot * 120) + " 110 20 -90 35");
                    context.waitTicks(120);
                    context.takeScreenshot("holes-" + spot + "-traced");
                    context.runOnClient(minecraft -> LimnSettings.pathTracing = false);
                    context.waitTicks(40);
                    context.takeScreenshot("holes-" + spot + "-raster");
                    context.runOnClient(minecraft -> LimnSettings.enabled = false);
                    context.waitTicks(40);
                    context.takeScreenshot("holes-" + spot + "-vanilla");
                    context.runOnClient(minecraft -> LimnSettings.enabled = true);
                    context.runOnClient(minecraft -> LimnSettings.pathTracing = true);
                    context.waitTicks(100);
                    context.takeScreenshot("holes-" + spot + "-retraced");
                }
                return;
            }
            world.getServer().runCommand("tp @p 64 200 64");
            context.waitTicks(140);
            world.getServer().runCommand("execute at @p run fill ~-12 ~-1 ~-2 ~12 ~-1 ~10 grass_block");
            world.getServer().runCommand("execute at @p run fill ~-12 ~ ~6 ~12 ~4 ~8 stone");
            world.getServer().runCommand("execute at @p run fill ~-6 ~1 ~6 ~-2 ~2 ~6 dirt");
            world.getServer().runCommand("execute at @p run fill ~-12 ~ ~-2 ~12 ~6 ~5 air");
            world.getServer().runCommand("execute at @p run setblock ~3 ~ ~4 campfire");
            world.getServer().runCommand("execute at @p run summon horse ~-2.5 ~ ~3 {NoAI:1b,Variant:769,Rotation:[90f,0f]}");
            world.getServer().runCommand("execute at @p run tp @p ~ ~ ~ 0 0");
            context.waitTicks(300);
            context.takeScreenshot("dlss-upscaled");
            context.runOnClient(minecraft -> LimnSettings.pathTracing = true);
            context.waitTicks(200);
            context.takeScreenshot("dlss-path-traced-still");
            context.getInput().holdKey(options -> options.keyLeft);
            context.waitTicks(12);
            context.takeScreenshot("dlss-path-traced-strafing");
            context.getInput().releaseKey(options -> options.keyLeft);
            world.getServer().runCommand("execute at @p run tp @p ~ ~ ~ 120 -10");
            context.waitTicks(8);
            context.takeScreenshot("dlss-path-traced-just-turned");
            world.getServer().runCommand("tp @p 264 200 64");
            context.waitTicks(100);
            world.getServer().runCommand("fill 224 196 24 304 196 104 sand");
            world.getServer().runCommand("fill 224 197 24 304 198 104 water");
            world.getServer().runCommand("fill 262 196 62 266 199 66 stone");
            world.getServer().runCommand("fill 280 197 80 281 201 81 oak_log");
            world.getServer().runCommand("fill 266 200 66 266 201 66 oak_log");
            world.getServer().runCommand("time set 3000");
            world.getServer().runCommand("tp @p 264.5 200 64.5 -45 25");
            context.waitTicks(200);
            context.takeScreenshot("lake-path-traced");
            context.runOnClient(minecraft -> LimnSettings.pathTracing = false);
            context.waitTicks(100);
            context.takeScreenshot("lake-raster");
            world.getServer().runCommand("tp @p 464 90 64");
            context.waitTicks(100);
            world.getServer().runCommand("fill 424 60 24 504 60 104 sand");
            world.getServer().runCommand("fill 424 61 24 504 62 104 water");
            world.getServer().runCommand("fill 424 63 24 504 66 104 air");
            world.getServer().runCommand("fill 424 67 24 504 70 104 air");
            world.getServer().runCommand("fill 424 71 24 504 74 104 air");
            world.getServer().runCommand("fill 462 60 62 466 63 66 stone");
            world.getServer().runCommand("fill 480 61 80 481 66 81 oak_log");
            world.getServer().runCommand("time set 3000");
            world.getServer().runCommand("tp @p 464.5 64 64.5 -45 25");
            context.waitTicks(200);
            context.takeScreenshot("sea-raster");
            context.runOnClient(minecraft -> LimnSettings.pathTracing = true);
            context.waitTicks(200);
            context.takeScreenshot("sea-path-traced");
        }
    }
}
