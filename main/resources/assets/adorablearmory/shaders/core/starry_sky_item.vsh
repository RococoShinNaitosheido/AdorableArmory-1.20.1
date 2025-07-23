#version 150

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in vec2 UV1;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

uniform float CamYaw;
uniform float CamPitch;

out vec3 vDir;
out vec4 texProj0;
out vec3 worldPos;

mat3 rotY(float a) {
    float s = sin(a), c = cos(a);
    return mat3(c, 0.0, s, 0.0, 1.0, 0.0, -s, 0.0, c);
}

mat3 rotX(float a) {
    float s = sin(a), c = cos(a);
    return mat3(1.0, 0.0, 0.0, 0.0, c, s, 0.0, -s, c);
}

void main() {
    vec4 viewPos = ModelViewMat * vec4(Position,1.0);
    vec4 clipPos = ProjMat * viewPos;
    gl_Position = clipPos;
    texProj0 = vec4(clipPos.xy * 0.5 + clipPos.w * 0.5, clipPos.zw);

    vec3 cameraForward = vec3(0.0, 0.0, -1.0);
    mat3 cameraRotation = rotX(-CamPitch) * rotY(-CamYaw);
    vDir = cameraRotation * cameraForward;

    worldPos = Position;
}
