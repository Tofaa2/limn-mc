package io.github.tofaa2.limnmc.render;

import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

final class LimnPipelines {
    static final RenderPipeline SHARED_COLOR = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
        .withLocation(id("pipeline/shared_color"))
        .withVertexShader(id("core/flipped_quad"))
        .withFragmentShader(id("core/shared_color"))
        .withColorTargetState(ColorTargetState.DEFAULT)
        .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
        .withBindGroupLayout(BindGroupLayouts.IN_SAMPLER)
        .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
        .build();

    static final RenderPipeline SHARED_DEPTH = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
        .withLocation(id("pipeline/shared_depth"))
        .withVertexShader(id("core/flipped_quad"))
        .withFragmentShader(id("core/shared_depth"))
        .withBindGroupLayout(BindGroupLayouts.IN_SAMPLER)
        .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
        .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
        .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, true))
        .build();

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("limn-mc", path);
    }

    private LimnPipelines() {
    }
}
