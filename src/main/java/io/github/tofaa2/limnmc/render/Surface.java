package io.github.tofaa2.limnmc.render;

public record Surface(float roughness, float metallic, float emission, boolean water) {
    public static final Surface DEFAULT = new Surface(0.9F, 0.0F, 0.0F, false);

    private static final String[] METALS = {
        "iron_block", "gold_block", "netherite_block", "copper", "iron_bars", "iron_door", "iron_trapdoor", "chain", "anvil", "cauldron", "hopper",
        "heavy_core", "lightning_rod", "bell",
    };
    private static final String[] GLOSSY = {
        "glass", "ice", "polished", "quartz_block", "obsidian", "diamond_block", "emerald_block", "lapis_block", "redstone_block", "glazed",
        "amethyst_block", "slime", "honey", "prismarine_bricks", "smooth",
    };
    private static final String[] SATIN = {"concrete", "planks", "terracotta", "bricks", "tiles", "purpur", "end_stone", "basalt_top"};

    public static Surface of(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        if (name.startsWith("water_")) {
            return new Surface(0.04F, 0.0F, 0.0F, true);
        }
        float emission = emission(name);
        if (contains(name, METALS) && !name.contains("ore")) {
            return new Surface(name.contains("oxidized") || name.contains("weathered") ? 0.6F : 0.3F, 1.0F, emission, false);
        }
        if (contains(name, GLOSSY) && !name.contains("powder")) {
            return new Surface(0.15F, 0.0F, emission, false);
        }
        if (contains(name, SATIN) && !name.contains("powder")) {
            return new Surface(0.6F, 0.0F, emission, false);
        }
        return emission > 0.0F ? new Surface(0.9F, 0.0F, emission, false) : DEFAULT;
    }

    private static float emission(String name) {
        if (name.startsWith("lava_") || name.equals("glowstone") || name.equals("sea_lantern") || name.contains("froglight")) {
            return 1.0F;
        }
        if (name.equals("shroomlight") || name.startsWith("fire_") || name.startsWith("soul_fire_") || name.equals("beacon") || name.equals("end_rod")) {
            return 1.0F;
        }
        if (name.equals("redstone_lamp_on") || name.equals("jack_o_lantern") || name.endsWith("_front_on") || name.startsWith("campfire_fire")) {
            return 0.8F;
        }
        if (name.equals("torch") || name.equals("soul_torch") || name.equals("lantern") || name.equals("soul_lantern") || name.equals("magma")) {
            return 0.5F;
        }
        if (name.contains("glow_lichen") || name.startsWith("sculk") || name.equals("crying_obsidian") || name.contains("portal")) {
            return 0.3F;
        }
        return 0.0F;
    }

    private static boolean contains(String name, String[] parts) {
        for (String part : parts) {
            if (name.contains(part)) {
                return true;
            }
        }
        return false;
    }
}
