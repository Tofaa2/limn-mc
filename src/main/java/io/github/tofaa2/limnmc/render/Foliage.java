package io.github.tofaa2.limnmc.render;

public final class Foliage {
    public static final byte NONE = 0;
    public static final byte PLANT = 1;
    public static final byte UPPER_PLANT = 2;
    public static final byte LEAVES = 3;
    public static final byte GROUND = 4;

    private static final String[] GROUND_COVER = {"pink_petals", "pink_petals_stem", "wildflowers", "wildflowers_stem", "leaf_litter"};

    private static final String[] PLANTS = {
        "short_grass", "tall_grass_bottom", "fern", "large_fern_bottom", "short_dry_grass", "tall_dry_grass", "bush", "firefly_bush", "dead_bush",
        "dandelion", "poppy", "blue_orchid", "allium", "azure_bluet", "oxeye_daisy", "cornflower", "lily_of_the_valley", "wither_rose", "torchflower",
        "open_eyeblossom", "closed_eyeblossom", "sunflower_bottom", "lilac_bottom", "rose_bush_bottom", "peony_bottom", "pitcher_crop_bottom",
        "cactus_flower", "nether_sprouts", "warped_roots",
        "crimson_roots", "seagrass", "tall_seagrass_bottom", "mangrove_propagule", "crimson_fungus", "warped_fungus",
    };
    private static final String[] UPPER_PLANTS = {
        "tall_grass_top", "large_fern_top", "sunflower_top", "sunflower_front", "sunflower_back", "lilac_top", "rose_bush_top", "peony_top",
        "pitcher_crop_top", "tall_seagrass_top",
    };
    private static final String[] GROWING = {"wheat_stage", "carrots_stage", "potatoes_stage", "beetroots_stage", "sweet_berry_bush_stage", "torchflower_crop_stage"};

    public static byte of(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        if (name.endsWith("_leaves")) {
            return LEAVES;
        }
        if (name.endsWith("_tulip") || name.endsWith("_sapling")) {
            return PLANT;
        }
        for (String plant : GROUND_COVER) {
            if (name.equals(plant)) {
                return GROUND;
            }
        }
        for (String plant : PLANTS) {
            if (name.equals(plant)) {
                return PLANT;
            }
        }
        for (String plant : UPPER_PLANTS) {
            if (name.equals(plant)) {
                return UPPER_PLANT;
            }
        }
        for (String plant : GROWING) {
            if (name.startsWith(plant)) {
                return PLANT;
            }
        }
        return NONE;
    }

    private Foliage() {
    }
}
