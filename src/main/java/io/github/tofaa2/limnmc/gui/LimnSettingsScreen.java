package io.github.tofaa2.limnmc.gui;

import io.github.tofaa2.limnmc.render.LimnSettings;
import java.util.function.IntConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;

public final class LimnSettingsScreen extends OptionsSubScreen {
    private boolean terrainChanged;

    public LimnSettingsScreen(Screen lastScreen, Options options) {
        super(lastScreen, options, Component.translatable("limn-mc.settings.title"));
    }

    @Override
    protected void addOptions() {
        list.addSmall(
            toggle("enabled", LimnSettings.enabled, value -> {
                LimnSettings.enabled = value;
                terrainChanged = true;
            }),
            choice("quality", LimnSettings.QUALITY_NAMES, LimnSettings.quality + 1, value -> LimnSettings.quality = value - 1),
            toggle("rayTracing", LimnSettings.rayTracing, value -> LimnSettings.rayTracing = value),
            toggle("pathTracing", LimnSettings.pathTracing, value -> LimnSettings.pathTracing = value),
            range("pathTracingBounces", 1, 16, LimnSettings.pathTracingBounces, value -> LimnSettings.pathTracingBounces = value),
            range("pathTracingSamples", 1, 16, LimnSettings.pathTracingSamples, value -> LimnSettings.pathTracingSamples = value),
            choice("upscaling", LimnSettings.UPSCALING_NAMES, LimnSettings.upscaling, value -> LimnSettings.upscaling = value),
            range("renderScalePercent", 25, 100, LimnSettings.renderScalePercent, value -> LimnSettings.renderScalePercent = value),
            toggle("frameGeneration", LimnSettings.frameGeneration, value -> LimnSettings.frameGeneration = value),
            toggle("shadows", LimnSettings.shadows, value -> LimnSettings.shadows = value),
            range("shadowDistance", 16, 512, LimnSettings.shadowDistance, value -> LimnSettings.shadowDistance = value),
            toggle("ambientOcclusion", LimnSettings.ambientOcclusion, value -> LimnSettings.ambientOcclusion = value),
            toggle("globalIllumination", LimnSettings.globalIllumination, value -> LimnSettings.globalIllumination = value),
            toggle("reflections", LimnSettings.reflections, value -> LimnSettings.reflections = value),
            toggle("volumetricClouds", LimnSettings.volumetricClouds, value -> LimnSettings.volumetricClouds = value),
            toggle("volumetricParticles", LimnSettings.volumetricParticles, value -> LimnSettings.volumetricParticles = value),
            toggle("volumetricWater", LimnSettings.volumetricWater, value -> {
                LimnSettings.volumetricWater = value;
                terrainChanged = true;
            }),
            toggle("physicallyBasedSurfaces", LimnSettings.physicallyBasedSurfaces, value -> {
                LimnSettings.physicallyBasedSurfaces = value;
                terrainChanged = true;
            }),
            toggle("plantSway", LimnSettings.plantSway, value -> {
                LimnSettings.plantSway = value;
                terrainChanged = true;
            }),
            toggle("limnEntities", LimnSettings.limnEntities, value -> LimnSettings.limnEntities = value),
            toggle("distanceHaze", LimnSettings.distanceHaze, value -> LimnSettings.distanceHaze = value),
            range("bloomPercent", 0, 100, LimnSettings.bloomPercent, value -> LimnSettings.bloomPercent = value),
            toggle("automaticExposure", LimnSettings.automaticExposure, value -> LimnSettings.automaticExposure = value),
            range("exposureSeconds", 1, 15, LimnSettings.exposureSeconds, value -> LimnSettings.exposureSeconds = value),
            choice("debugView", LimnSettings.DEBUG_VIEW_NAMES, LimnSettings.debugView, value -> LimnSettings.debugView = value)
        );
    }

    @Override
    public void removed() {
        LimnSettings.save();
        if (terrainChanged && Minecraft.getInstance().level != null) {
            Minecraft.getInstance().levelExtractor.allChanged();
        }
        super.removed();
    }

    private static OptionInstance<Boolean> toggle(String name, boolean value, java.util.function.Consumer<Boolean> apply) {
        return OptionInstance.createBoolean("limn-mc.settings." + name, value, apply::accept);
    }

    private static OptionInstance<Integer> range(String name, int minimum, int maximum, int value, IntConsumer apply) {
        return new OptionInstance<>(
            "limn-mc.settings." + name,
            OptionInstance.noTooltip(),
            Options::genericValueLabel,
            new OptionInstance.IntRange(minimum, maximum),
            value,
            apply::accept
        );
    }

    private static OptionInstance<Integer> choice(String name, String[] names, int value, IntConsumer apply) {
        return new OptionInstance<>(
            "limn-mc.settings." + name,
            OptionInstance.noTooltip(),
            (caption, index) -> Options.genericValueLabel(caption, Component.literal(names[index])),
            new OptionInstance.IntRange(0, names.length - 1),
            value,
            apply::accept
        );
    }
}
