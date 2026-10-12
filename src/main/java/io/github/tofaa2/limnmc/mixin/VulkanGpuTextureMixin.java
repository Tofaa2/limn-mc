package io.github.tofaa2.limnmc.mixin;

import com.mojang.renderpearl.backend.vulkan.VulkanDevice;
import com.mojang.renderpearl.backend.vulkan.VulkanGpuTexture;
import io.github.tofaa2.limnmc.render.SharedImport;
import java.nio.LongBuffer;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.vma.Vma;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.util.vma.VmaAllocationInfo;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkExternalMemoryImageCreateInfo;
import org.lwjgl.vulkan.VkImageCreateInfo;
import org.lwjgl.vulkan.VkImportMemoryFdInfoKHR;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;
import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VulkanGpuTexture.class)
abstract class VulkanGpuTextureMixin {
    @Unique
    private static final int OPAQUE_FD = 1;
    @Unique
    private static final int DEVICE_LOCAL = 1;

    @Shadow
    @Final
    private VulkanDevice device;
    @Shadow
    @Final
    private long vkImage;
    @Unique
    private long limn$importedMemory;

    @Redirect(
        method = "<init>",
        at = @At(
            value = "INVOKE",
            target = "Lorg/lwjgl/util/vma/Vma;vmaCreateImage(JLorg/lwjgl/vulkan/VkImageCreateInfo;Lorg/lwjgl/util/vma/VmaAllocationCreateInfo;Ljava/nio/LongBuffer;Lorg/lwjgl/PointerBuffer;Lorg/lwjgl/util/vma/VmaAllocationInfo;)I"
        )
    )
    private int limn$importShared(
        long allocator,
        VkImageCreateInfo imageInfo,
        VmaAllocationCreateInfo allocationInfo,
        LongBuffer image,
        PointerBuffer allocation,
        VmaAllocationInfo allocationResult
    ) {
        SharedImport.Request request = SharedImport.takePending();
        if (request == null) {
            return Vma.vmaCreateImage(allocator, imageInfo, allocationInfo, image, allocation, allocationResult);
        }
        VkDevice vkDevice = device.vkDevice();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            imageInfo.pNext(VkExternalMemoryImageCreateInfo.calloc(stack).sType$Default().handleTypes(OPAQUE_FD));
            imageInfo.usage(SharedImport.EXPORTED_USAGE);
            int created = VK10.vkCreateImage(vkDevice, imageInfo, null, image);
            if (created != VK10.VK_SUCCESS) {
                return created;
            }
            VkMemoryRequirements requirements = VkMemoryRequirements.calloc(stack);
            VK10.vkGetImageMemoryRequirements(vkDevice, image.get(0), requirements);
            VkPhysicalDeviceMemoryProperties properties = VkPhysicalDeviceMemoryProperties.calloc(stack);
            VK10.vkGetPhysicalDeviceMemoryProperties(vkDevice.getPhysicalDevice(), properties);
            int memoryType = -1;
            for (int pass = 0; pass < 2 && memoryType < 0; pass++) {
                for (int index = 0; index < properties.memoryTypeCount(); index++) {
                    boolean allowed = (requirements.memoryTypeBits() & 1 << index) != 0;
                    boolean local = (properties.memoryTypes(index).propertyFlags() & DEVICE_LOCAL) != 0;
                    if (allowed && (local || pass == 1)) {
                        memoryType = index;
                        break;
                    }
                }
            }
            VkImportMemoryFdInfoKHR imported = VkImportMemoryFdInfoKHR.calloc(stack).sType$Default().handleType(OPAQUE_FD).fd((int) request.handle());
            VkMemoryAllocateInfo memoryInfo = VkMemoryAllocateInfo.calloc(stack)
                .sType$Default()
                .pNext(imported)
                .allocationSize(request.bytes())
                .memoryTypeIndex(memoryType);
            LongBuffer memory = stack.callocLong(1);
            int allocated = VK10.vkAllocateMemory(vkDevice, memoryInfo, null, memory);
            if (allocated != VK10.VK_SUCCESS) {
                VK10.vkDestroyImage(vkDevice, image.get(0), null);
                return allocated;
            }
            limn$importedMemory = memory.get(0);
            allocation.put(0, 0L);
            return VK10.vkBindImageMemory(vkDevice, image.get(0), limn$importedMemory, 0);
        }
    }

    @Inject(method = "destroy", at = @At("HEAD"), cancellable = true)
    private void limn$destroyImported(CallbackInfo callback) {
        if (limn$importedMemory != 0) {
            VK10.vkDestroyImage(device.vkDevice(), vkImage, null);
            VK10.vkFreeMemory(device.vkDevice(), limn$importedMemory, null);
            limn$importedMemory = 0;
            callback.cancel();
        }
    }
}
