#version 150

in vec3 Position;
in vec4 Color;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 vUv;
out vec4 vMeta;

void main() {
    vUv = UV0;
    vMeta = Color;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
