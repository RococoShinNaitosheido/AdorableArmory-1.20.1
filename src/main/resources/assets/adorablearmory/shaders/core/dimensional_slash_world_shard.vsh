#version 150

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 vUv;
out vec4 vMeta;
out vec3 vNormal;
out vec3 vViewPos;

void main() {
    vUv = UV0;
    vMeta = Color;
    vec4 viewPos = ModelViewMat * vec4(Position, 1.0);
    vViewPos = viewPos.xyz;
    vNormal = normalize(mat3(ModelViewMat) * Normal);
    gl_Position = ProjMat * viewPos;
}
