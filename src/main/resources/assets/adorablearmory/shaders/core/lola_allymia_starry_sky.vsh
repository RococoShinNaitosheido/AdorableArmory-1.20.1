#version 150

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in vec2 UV1;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec4 texProj0;

void main() {
    vec4 clipPos = ProjMat * ModelViewMat * vec4(Position, 1.0);
    texProj0 = vec4(clipPos.xy * 0.5 + clipPos.w * 0.5, clipPos.zw); // vanilla style
    gl_Position = clipPos;
}
