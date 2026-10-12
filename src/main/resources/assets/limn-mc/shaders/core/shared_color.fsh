#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>

uniform sampler2D InSampler;

layout(location = 0) in vec2 clipPosition;

layout(location = 0) out vec4 fragColor;

void main() {
    vec4 source = ModelViewMat * vec4(clipPosition, 0.5, 1.0);
    vec2 uv = source.xy / source.w * 0.5 + 0.5;
    fragColor = vec4(texture(InSampler, vec2(uv.x, 1.0 - uv.y)).rgb, 1.0);
}
