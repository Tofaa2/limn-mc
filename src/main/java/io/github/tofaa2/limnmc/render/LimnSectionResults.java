package io.github.tofaa2.limnmc.render;

public interface LimnSectionResults {
    long limn$takeModel();

    void limn$setModel(long model);

    BlockLights.Emitters limn$emitters();

    long limn$takeSeaModel();

    void limn$setSeaModel(long model);

    void limn$setEmitters(BlockLights.Emitters emitters);
}
