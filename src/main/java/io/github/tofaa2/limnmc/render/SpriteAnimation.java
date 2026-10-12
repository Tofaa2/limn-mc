package io.github.tofaa2.limnmc.render;

import static java.lang.foreign.ValueLayout.JAVA_INT_UNALIGNED;

import io.github.tofaa2.limnmc.mixin.AnimatedTextureAccessor;
import io.github.tofaa2.limnmc.mixin.FrameInfoAccessor;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.List;

final class SpriteAnimation {
    private final int cellX;
    private final int cellY;
    private final int padding;
    private final int width;
    private final int height;
    private final int[] source;
    private final int sourceWidth;
    private final int[] frameIndex;
    private final int[] frameTime;
    private final int frameRowSize;
    private final float emission;
    private final MemorySegment color;
    private final MemorySegment glow;
    private int frame;
    private int subFrame;

    SpriteAnimation(
        Arena arena, Object animatedTexture, int cellX, int cellY, int padding, int width, int height, int[] source, int sourceWidth, float emission
    ) {
        AnimatedTextureAccessor animation = (AnimatedTextureAccessor) animatedTexture;
        List<?> frames = animation.limn$frames();
        this.frameIndex = new int[frames.size()];
        this.frameTime = new int[frames.size()];
        for (int index = 0; index < frames.size(); index++) {
            FrameInfoAccessor info = (FrameInfoAccessor) (Object) frames.get(index);
            frameIndex[index] = info.limn$index();
            frameTime[index] = info.limn$time();
        }
        this.frameRowSize = animation.limn$frameRowSize();
        this.cellX = cellX;
        this.cellY = cellY;
        this.padding = padding;
        this.width = width;
        this.height = height;
        this.source = source;
        this.sourceWidth = sourceWidth;
        this.emission = emission;
        long bytes = (long) cellWidth() * cellHeight() * 4;
        this.color = arena.allocate(bytes, 4);
        this.glow = emission > 0.0F ? arena.allocate(bytes, 4) : null;
    }

    void tick(LimnWorldRenderer renderer) {
        if (frameIndex.length < 2) {
            return;
        }
        subFrame++;
        if (subFrame < frameTime[frame]) {
            return;
        }
        int previous = frameIndex[frame];
        frame = (frame + 1) % frameIndex.length;
        subFrame = 0;
        if (frameIndex[frame] == previous) {
            return;
        }
        int frameX = frameIndex[frame] % frameRowSize * width;
        int frameY = frameIndex[frame] / frameRowSize * height;
        int sourceHeight = source.length / sourceWidth;
        if (frameX + width > sourceWidth || frameY + height > sourceHeight) {
            return;
        }
        for (int y = 0; y < cellHeight(); y++) {
            int sourceY = frameY + Math.clamp(y - padding, 0, height - 1);
            for (int x = 0; x < cellWidth(); x++) {
                int texel = source[sourceY * sourceWidth + frameX + Math.clamp(x - padding, 0, width - 1)];
                long at = ((long) y * cellWidth() + x) * 4;
                color.set(JAVA_INT_UNALIGNED, at, texel);
                if (glow != null) {
                    glow.set(JAVA_INT_UNALIGNED, at, BlockAtlasPixels.scale(texel, emission));
                }
            }
        }
        renderer.updateAtlas(cellX, cellY, cellWidth(), cellHeight(), color, glow);
    }

    private int cellWidth() {
        return width + padding * 2;
    }

    private int cellHeight() {
        return height + padding * 2;
    }
}
