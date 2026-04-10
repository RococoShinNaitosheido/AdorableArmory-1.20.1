#version 150

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 texCoord0;
flat out ivec3 outlineColor;
flat out int outlineRadius;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    texCoord0 = UV0;
    outlineColor = ivec3(UV1.x & 255, UV1.y & 255, UV2.x & 255);
    outlineRadius = UV2.y & 255;
}
