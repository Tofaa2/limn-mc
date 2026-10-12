package io.github.tofaa2.limnmc.mixin;

import com.mojang.renderpearl.backend.vulkan.VulkanFeatureSets;
import com.mojang.renderpearl.backend.vulkan.init.FeatureSet;
import io.github.tofaa2.limnmc.render.SharedImport;
import java.util.Set;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(VulkanFeatureSets.class)
abstract class VulkanFeatureSetsMixin {
    @Unique
    private static final FeatureSet LIMN_SHARED_IMAGES = new FeatureSet("Limn shared images", Set.of(SharedImport.EXTENSION), Set.of());

    @Inject(method = "optionalFeatureSets", at = @At("RETURN"))
    private static void limn$addSharedImages(CallbackInfoReturnable<Set<FeatureSet>> callback) {
        callback.getReturnValue().add(LIMN_SHARED_IMAGES);
    }
}
