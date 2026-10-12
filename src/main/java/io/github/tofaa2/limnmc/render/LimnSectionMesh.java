package io.github.tofaa2.limnmc.render;

public interface LimnSectionMesh {
    long limn$model();

    void limn$setModel(long model);

    long limn$entity();

    void limn$setEntity(long entity);

    BlockLights.Emitters limn$emitters();

    long limn$seaModel();

    void limn$setSeaModel(long model);

    long limn$seaEntity();

    void limn$setSeaEntity(long entity);
}
