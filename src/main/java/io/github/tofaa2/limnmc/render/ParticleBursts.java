package io.github.tofaa2.limnmc.render;

import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import io.github.tofaa2.limnmc.nativeapi.LimnAbi;
import io.github.tofaa2.limnmc.nativeapi.LimnContext;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

public final class ParticleBursts {
    public enum Look {
        DUST, SPARK, DROPLET, EMBER
    }

    private static final int SLOTS = 20;
    private static final int BUSY_FRAMES = 120;
    private static final int CAPACITY = 1024;
    private static final float MERGE_DISTANCE_SQUARED = 4.0F;

    private static final class Slot {
        long handle;
        Look look;
        int color;
        long busyUntil;
        int pending;
        float x;
        float y;
        float z;
    }

    private final Slot[] slots = new Slot[SLOTS];
    private final MemorySegment emitter;
    private long frame;

    ParticleBursts(Arena arena) {
        this.emitter = arena.allocate(LimnAbi.Emitter.SIZE, 4);
        for (int index = 0; index < SLOTS; index++) {
            slots[index] = new Slot();
        }
    }

    boolean add(Look look, int color, float x, float y, float z) {
        Slot free = null;
        for (Slot slot : slots) {
            boolean busy = slot.busyUntil >= frame || slot.pending > 0;
            if (busy && slot.look == look && slot.color == color) {
                float dx = slot.x - x;
                float dy = slot.y - y;
                float dz = slot.z - z;
                if (slot.pending == 0 || dx * dx + dy * dy + dz * dz < MERGE_DISTANCE_SQUARED) {
                    if (slot.pending == 0) {
                        slot.x = x;
                        slot.y = y;
                        slot.z = z;
                    }
                    slot.pending++;
                    return true;
                }
            } else if (!busy && free == null) {
                free = slot;
            }
        }
        if (free == null) {
            return false;
        }
        free.look = look;
        free.color = color;
        free.x = x;
        free.y = y;
        free.z = z;
        free.pending = 1;
        return true;
    }

    void flush(LimnContext context, long scene, int originX, int originY, int originZ) {
        frame++;
        for (Slot slot : slots) {
            if (slot.pending == 0) {
                continue;
            }
            write(slot, originX, originY, originZ);
            if (slot.handle == 0) {
                slot.handle = context.createEmitter(scene, emitter);
            } else {
                context.updateEmitter(slot.handle, emitter);
            }
            context.burstEmitter(slot.handle, Math.min(slot.pending, CAPACITY));
            slot.pending = 0;
            slot.busyUntil = frame + BUSY_FRAMES;
        }
    }

    private void write(Slot slot, int originX, int originY, int originZ) {
        float red = linear(slot.color >> 16 & 0xFF);
        float green = linear(slot.color >> 8 & 0xFF);
        float blue = linear(slot.color & 0xFF);
        set(LimnAbi.Emitter.POSITION, slot.x - originX, slot.y - originY, slot.z - originZ);
        emitter.set(JAVA_FLOAT, LimnAbi.Emitter.RATE, 0.0F);
        set(LimnAbi.Emitter.DIRECTION, 0.0F, 1.0F, 0.0F);
        emitter.set(JAVA_INT, LimnAbi.Emitter.CAPACITY, CAPACITY);
        switch (slot.look) {
            case DUST -> {
                emitter.set(JAVA_FLOAT, LimnAbi.Emitter.RADIUS, 0.4F);
                set(LimnAbi.Emitter.LIFETIME, 0.5F, 1.1F);
                emitter.set(JAVA_FLOAT, LimnAbi.Emitter.SPREAD, (float) Math.PI);
                set(LimnAbi.Emitter.SPEED, 0.4F, 2.0F);
                set(LimnAbi.Emitter.GRAVITY, 0.0F, -14.0F, 0.0F);
                emitter.set(JAVA_FLOAT, LimnAbi.Emitter.DRAG, 0.6F);
                set(LimnAbi.Emitter.PARTICLE_SIZE, 0.11F, 0.06F);
                set(LimnAbi.Emitter.COLOR_START, red, green, blue, 1.0F);
                set(LimnAbi.Emitter.COLOR_END, red, green, blue, 0.9F);
                emitter.set(JAVA_FLOAT, LimnAbi.Emitter.STRETCH, 0.0F);
                emitter.set(JAVA_FLOAT, LimnAbi.Emitter.BOUNCE, 0.25F);
                emitter.set(JAVA_INT, LimnAbi.Emitter.FLAGS, LimnAbi.Emitter.LIT | LimnAbi.Emitter.COLLIDE);
            }
            case SPARK -> {
                emitter.set(JAVA_FLOAT, LimnAbi.Emitter.RADIUS, 0.35F);
                set(LimnAbi.Emitter.LIFETIME, 0.25F, 0.6F);
                emitter.set(JAVA_FLOAT, LimnAbi.Emitter.SPREAD, (float) Math.PI);
                set(LimnAbi.Emitter.SPEED, 1.0F, 4.5F);
                set(LimnAbi.Emitter.GRAVITY, 0.0F, -5.0F, 0.0F);
                emitter.set(JAVA_FLOAT, LimnAbi.Emitter.DRAG, 1.5F);
                set(LimnAbi.Emitter.PARTICLE_SIZE, 0.07F, 0.02F);
                set(LimnAbi.Emitter.COLOR_START, red * 6.0F, green * 6.0F, blue * 6.0F, 1.0F);
                set(LimnAbi.Emitter.COLOR_END, red * 2.0F, green * 2.0F, blue * 2.0F, 0.0F);
                emitter.set(JAVA_FLOAT, LimnAbi.Emitter.STRETCH, 0.02F);
                emitter.set(JAVA_FLOAT, LimnAbi.Emitter.BOUNCE, 0.4F);
                emitter.set(JAVA_INT, LimnAbi.Emitter.FLAGS, LimnAbi.Emitter.ADDITIVE);
            }
            case DROPLET -> {
                emitter.set(JAVA_FLOAT, LimnAbi.Emitter.RADIUS, 0.2F);
                set(LimnAbi.Emitter.LIFETIME, 0.4F, 0.8F);
                emitter.set(JAVA_FLOAT, LimnAbi.Emitter.SPREAD, 0.9F);
                set(LimnAbi.Emitter.SPEED, 1.2F, 3.0F);
                set(LimnAbi.Emitter.GRAVITY, 0.0F, -16.0F, 0.0F);
                emitter.set(JAVA_FLOAT, LimnAbi.Emitter.DRAG, 0.1F);
                set(LimnAbi.Emitter.PARTICLE_SIZE, 0.05F, 0.03F);
                set(LimnAbi.Emitter.COLOR_START, red, green, blue, 0.75F);
                set(LimnAbi.Emitter.COLOR_END, red, green, blue, 0.4F);
                emitter.set(JAVA_FLOAT, LimnAbi.Emitter.STRETCH, 0.02F);
                emitter.set(JAVA_FLOAT, LimnAbi.Emitter.BOUNCE, 0.1F);
                emitter.set(JAVA_INT, LimnAbi.Emitter.FLAGS, LimnAbi.Emitter.LIT | LimnAbi.Emitter.COLLIDE);
            }
            case EMBER -> {
                emitter.set(JAVA_FLOAT, LimnAbi.Emitter.RADIUS, 0.25F);
                set(LimnAbi.Emitter.LIFETIME, 0.8F, 1.8F);
                emitter.set(JAVA_FLOAT, LimnAbi.Emitter.SPREAD, 0.7F);
                set(LimnAbi.Emitter.SPEED, 2.0F, 5.0F);
                set(LimnAbi.Emitter.GRAVITY, 0.0F, -9.0F, 0.0F);
                emitter.set(JAVA_FLOAT, LimnAbi.Emitter.DRAG, 0.3F);
                set(LimnAbi.Emitter.PARTICLE_SIZE, 0.12F, 0.04F);
                set(LimnAbi.Emitter.COLOR_START, red * 8.0F, green * 8.0F, blue * 8.0F, 1.0F);
                set(LimnAbi.Emitter.COLOR_END, red * 3.0F, green * 1.5F, blue, 0.0F);
                emitter.set(JAVA_FLOAT, LimnAbi.Emitter.STRETCH, 0.0F);
                emitter.set(JAVA_FLOAT, LimnAbi.Emitter.BOUNCE, 0.3F);
                emitter.set(JAVA_INT, LimnAbi.Emitter.FLAGS, LimnAbi.Emitter.ADDITIVE | LimnAbi.Emitter.COLLIDE);
            }
        }
    }

    private static float linear(int channel) {
        return (float) Math.pow(channel / 255.0, 2.2);
    }

    private void set(long offset, float... values) {
        for (int index = 0; index < values.length; index++) {
            emitter.set(JAVA_FLOAT, offset + index * 4L, values[index]);
        }
    }
}
