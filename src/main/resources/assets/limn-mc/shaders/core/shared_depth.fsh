#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>

uniform sampler2D InSampler;

layout(location = 0) in vec2 clipPosition;

void main() {
    vec4 source = ModelViewMat * vec4(clipPosition, 0.5, 1.0);
    vec2 uv = clamp(source.xy / source.w * 0.5 + 0.5, 0.0, 1.0);
    float limnDepth = texture(InSampler, vec2(uv.x, 1.0 - uv.y) * ColorModulator.zw).r;
    float behind = limnDepth - min(fwidth(limnDepth), limnDepth * 0.02);
    gl_FragDepth = clamp(ColorModulator.x + ColorModulator.y * behind, 0.0, 1.0);
}
