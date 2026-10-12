#version 330
#extension GL_ARB_separate_shader_objects : require

layout(location = 0) out vec2 clipPosition;

void main() {
    vec2 uv = vec2((gl_VertexIndex << 1) & 2, gl_VertexIndex & 2);
    clipPosition = uv * 2.0 - 1.0;
    gl_Position = vec4(clipPosition, 0.0, 1.0);
}
