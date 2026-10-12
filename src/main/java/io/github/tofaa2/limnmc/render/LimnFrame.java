package io.github.tofaa2.limnmc.render;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;

public final class LimnFrame {
    private static final Matrix4f VIEW_SWAY = new Matrix4f();
    private static final Matrix4f LEVEL_PROJECTION = new Matrix4f();
    private static boolean drawn;
    private static boolean capturing;

    public static boolean drawn() {
        return drawn;
    }

    public static void setDrawn(boolean value) {
        drawn = value;
    }

    public static boolean capturing() {
        return capturing;
    }

    public static void setCapturing(boolean value) {
        capturing = value;
    }

    public static Matrix4fc viewSway() {
        return VIEW_SWAY;
    }

    public static Matrix4fc levelProjection() {
        return LEVEL_PROJECTION;
    }

    public static void setLevelProjection(Matrix4fc projection) {
        LEVEL_PROJECTION.set(projection);
    }

    public static void setViewSway(Matrix4fc sway) {
        VIEW_SWAY.set(sway);
    }

    private LimnFrame() {
    }
}
