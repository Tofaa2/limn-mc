package io.github.tofaa2.limnmc.render;

import static java.lang.foreign.ValueLayout.JAVA_INT_UNALIGNED;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.tofaa2.limnmc.mixin.SpriteContentsAccessor;
import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

public record BlockAtlasPixels(
    Arena arena,
    MemorySegment color,
    MemorySegment surface,
    MemorySegment emission,
    MemorySegment normal,
    boolean hasNormals,
    byte[] water,
    byte[] foliage,
    List<SpriteAnimation> animations,
    int width,
    int height
) {
    private static final int FLAT_NORMAL = 0xFFFF8080;

    private static volatile BlockAtlasPixels latest;

    public static BlockAtlasPixels latest() {
        return latest;
    }

    public void tickAnimations() {
        LimnWorldRenderer renderer = LimnWorldRenderer.active();
        if (renderer != null) {
            for (SpriteAnimation animation : animations) {
                animation.tick(renderer);
            }
        }
    }

    public boolean isWater(float u, float v) {
        int x = Math.clamp((int) (u * width), 0, width - 1);
        int y = Math.clamp((int) (v * height), 0, height - 1);
        return water[y * width + x] != 0;
    }

    public byte foliageAt(float u, float v) {
        int x = Math.clamp((int) (u * width), 0, width - 1);
        int y = Math.clamp((int) (v * height), 0, height - 1);
        return foliage[y * width + x];
    }

    public static void capture(int width, int height, Collection<TextureAtlasSprite> sprites) {
        Arena arena = Arena.ofShared();
        long bytes = (long) width * height * 4;
        MemorySegment color = arena.allocate(bytes, 4);
        MemorySegment surface = arena.allocate(bytes, 4);
        MemorySegment emission = arena.allocate(bytes, 4);
        MemorySegment normal = arena.allocate(bytes, 4);
        byte[] water = new byte[width * height];
        byte[] foliage = new byte[width * height];
        int defaultSurface = pack(255, Math.round(Surface.DEFAULT.roughness() * 255.0F), 0);
        for (long at = 0; at < bytes; at += 4) {
            surface.set(JAVA_INT_UNALIGNED, at, defaultSurface);
            normal.set(JAVA_INT_UNALIGNED, at, FLAT_NORMAL);
        }
        boolean hasNormals = false;
        List<SpriteAnimation> animations = new ArrayList<>();
        for (TextureAtlasSprite sprite : sprites) {
            NativeImage image = ((SpriteContentsAccessor) sprite.contents()).limn$originalImage();
            if (image.format() != NativeImage.Format.RGBA) {
                continue;
            }
            Identifier name = sprite.contents().name();
            Surface heuristic = Surface.of(name.getPath());
            Object animated = ((SpriteContentsAccessor) sprite.contents()).limn$animatedTexture();
            if (animated != null) {
                int padding = Math.round(sprite.getU0() * width) - sprite.getX();
                int inset = Math.min(padding, 1);
                animations.add(new SpriteAnimation(
                    arena,
                    animated,
                    sprite.getX() + padding - inset,
                    sprite.getY() + padding - inset,
                    inset,
                    sprite.contents().width(),
                    sprite.contents().height(),
                    image.getPixelsABGR(),
                    image.getWidth(),
                    heuristic.emission()
                ));
            }
            try (NativeImage specular = companion(name, "_s"); NativeImage normals = companion(name, "_n")) {
                hasNormals |= normals != null;
                writeSprite(new Target(color, surface, emission, normal, water, foliage, width, height), sprite, image, heuristic, specular, normals);
            }
        }
        BlockAtlasPixels previous = latest;
        latest = new BlockAtlasPixels(arena, color, surface, emission, normal, hasNormals, water, foliage, animations, width, height);
        LimnWorldRenderer renderer = LimnWorldRenderer.active();
        if (renderer != null) {
            renderer.useAtlas(latest);
        }
        if (previous != null) {
            previous.arena.close();
        }
    }

    private record Target(MemorySegment color, MemorySegment surface, MemorySegment emission, MemorySegment normal, byte[] water, byte[] foliage, int width, int height) {
    }

    private static NativeImage companion(Identifier sprite, String suffix) {
        Identifier location = Identifier.fromNamespaceAndPath(sprite.getNamespace(), "textures/" + sprite.getPath() + suffix + ".png");
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(location);
        if (resource.isEmpty()) {
            return null;
        }
        try (InputStream stream = resource.get().open()) {
            return NativeImage.read(stream);
        } catch (IOException exception) {
            return null;
        }
    }

    private static void writeSprite(Target target, TextureAtlasSprite sprite, NativeImage image, Surface heuristic, NativeImage specular, NativeImage normals) {
        int spriteWidth = sprite.contents().width();
        int spriteHeight = sprite.contents().height();
        int padding = Math.round(sprite.getU0() * target.width) - sprite.getX();
        int[] source = withFilledTransparency(image.getPixelsABGR());
        int sourceWidth = image.getWidth();
        int[] specularPixels = specular == null ? null : specular.getPixelsABGR();
        int[] normalPixels = normals == null ? null : normals.getPixelsABGR();
        byte plant = Foliage.of(sprite.contents().name().getPath());
        int heuristicSurface = pack(255, Math.round(heuristic.roughness() * 255.0F), Math.round(heuristic.metallic() * 255.0F));
        for (int cellY = 0; cellY < spriteHeight + padding * 2; cellY++) {
            int atlasY = sprite.getY() + cellY;
            if (atlasY < 0 || atlasY >= target.height) {
                continue;
            }
            int sourceY = Math.clamp(cellY - padding, 0, spriteHeight - 1);
            for (int cellX = 0; cellX < spriteWidth + padding * 2; cellX++) {
                int atlasX = sprite.getX() + cellX;
                if (atlasX < 0 || atlasX >= target.width) {
                    continue;
                }
                int sourceX = Math.clamp(cellX - padding, 0, spriteWidth - 1);
                int texel = source[sourceY * sourceWidth + sourceX];
                long at = ((long) atlasY * target.width + atlasX) * 4;
                target.color.set(JAVA_INT_UNALIGNED, at, texel);
                target.water[atlasY * target.width + atlasX] = (byte) (heuristic.water() ? 1 : 0);
                target.foliage[atlasY * target.width + atlasX] = plant;
                float glow = heuristic.emission();
                int surface = heuristicSurface;
                if (specularPixels != null) {
                    int value = sample(specularPixels, specular, sourceX, sourceY, spriteWidth, spriteHeight);
                    float smoothness = (value & 0xFF) / 255.0F;
                    int alpha = value >>> 24;
                    surface = pack(255, Math.round((1.0F - smoothness) * (1.0F - smoothness) * 255.0F), ((value >>> 8) & 0xFF) >= 230 ? 255 : 0);
                    glow = alpha == 255 ? 0.0F : alpha / 254.0F;
                }
                target.surface.set(JAVA_INT_UNALIGNED, at, surface);
                target.emission.set(JAVA_INT_UNALIGNED, at, scale(texel, glow));
                if (normalPixels != null) {
                    target.normal.set(JAVA_INT_UNALIGNED, at, tangentNormal(sample(normalPixels, normals, sourceX, sourceY, spriteWidth, spriteHeight)));
                }
            }
        }
    }

    private static int sample(int[] pixels, NativeImage image, int x, int y, int spriteWidth, int spriteHeight) {
        int sampleX = Math.min(x * image.getWidth() / spriteWidth, image.getWidth() - 1);
        int sampleY = Math.min(y * image.getWidth() / spriteWidth, image.getHeight() - 1);
        return pixels[sampleY * image.getWidth() + sampleX];
    }

    private static int tangentNormal(int value) {
        float x = (value & 0xFF) / 127.5F - 1.0F;
        float y = ((value >>> 8) & 0xFF) / 127.5F - 1.0F;
        float z = (float) Math.sqrt(Math.max(1.0F - x * x - y * y, 0.0F));
        return pack(value & 0xFF, (value >>> 8) & 0xFF, Math.round((z * 0.5F + 0.5F) * 255.0F));
    }

    static int[] withFilledTransparency(int[] pixels) {
        long red = 0;
        long green = 0;
        long blue = 0;
        int opaque = 0;
        for (int pixel : pixels) {
            if (pixel >>> 24 != 0) {
                red += pixel & 0xFF;
                green += pixel >>> 8 & 0xFF;
                blue += pixel >>> 16 & 0xFF;
                opaque++;
            }
        }
        if (opaque == 0 || opaque == pixels.length) {
            return pixels;
        }
        int fill = (int) (blue / opaque) << 16 | (int) (green / opaque) << 8 | (int) (red / opaque);
        for (int index = 0; index < pixels.length; index++) {
            if (pixels[index] >>> 24 == 0) {
                pixels[index] = fill;
            }
        }
        return pixels;
    }

    static int scale(int texel, float factor) {
        return pack(Math.round((texel & 0xFF) * factor), Math.round(((texel >>> 8) & 0xFF) * factor), Math.round(((texel >>> 16) & 0xFF) * factor));
    }

    private static int pack(int red, int green, int blue) {
        return 0xFF000000 | blue << 16 | green << 8 | red;
    }
}
