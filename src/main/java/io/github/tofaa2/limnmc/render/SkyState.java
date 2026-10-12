package io.github.tofaa2.limnmc.render;

import io.github.tofaa2.limnmc.mixin.GameRendererAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.world.attribute.EnvironmentAttributeProbe;
import net.minecraft.world.attribute.EnvironmentAttributes;

public record SkyState(float sunAngle, float moonAngle, float starBrightness, float rain, float cloudHeight) {
    private static final float RADIANS = (float) (Math.PI / 180.0);

    public static SkyState current(float partialTicks, float cloudHeight) {
        Minecraft minecraft = Minecraft.getInstance();
        EnvironmentAttributeProbe probe = ((GameRendererAccessor) minecraft.gameRenderer).limn$mainCamera().attributeProbe();
        return new SkyState(
            probe.getValue(EnvironmentAttributes.SUN_ANGLE, partialTicks) * RADIANS,
            probe.getValue(EnvironmentAttributes.MOON_ANGLE, partialTicks) * RADIANS,
            probe.getValue(EnvironmentAttributes.STAR_BRIGHTNESS, partialTicks),
            minecraft.level == null ? 0.0F : minecraft.level.getRainLevel(partialTicks),
            cloudHeight
        );
    }
}
