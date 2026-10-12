package io.github.tofaa2.limnmc;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.tofaa2.limnmc.gui.LimnSettingsScreen;
import io.github.tofaa2.limnmc.render.LimnSettings;
import net.fabricmc.api.ClientModInitializer;
import io.github.tofaa2.limnmc.render.LimnWorldRenderer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;

public final class LimnMod implements ClientModInitializer {
    public static final String MOD_ID = "limn-mc";

    @Override
    public void onInitializeClient() {
        LimnSettings.load();
        ClientLifecycleEvents.CLIENT_STOPPING.register(minecraft -> LimnWorldRenderer.shutdown());
        KeyMapping openSettings = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("limn-mc.key.settings", InputConstants.KEY_O, KeyMapping.Category.MISC)
        );
        ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
            while (openSettings.consumeClick()) {
                minecraft.gui.setScreen(new LimnSettingsScreen(minecraft.gui.screen(), minecraft.options));
            }
        });
    }
}
