#version 150

float fog_distance(mat4 modelViewMat, vec3 pos, int shape) {
    if (shape == 0) {
        return length((modelViewMat * vec4(pos, 1.0)).xyz);
    } else {
        float distXZ = length((modelViewMat * vec4(pos.x, 0.0, pos.z, 1.0)).xyz);
        float distY = length((modelViewMat * vec4(0.0, pos.y, 0.0, 1.0)).xyz);
        return max(distXZ, distY);
    }
}

vec4 minecraft_sample_lightmap(sampler2D lightMap, ivec2 uv) {
    vec2 normalizedUV = vec2(uv) / 16.0;
    vec2 safeUV = clamp(normalizedUV, vec2(0.5/16.0), vec2(15.5/16.0));
    return texture(lightMap, safeUV);
}

// In: 从模型传入的顶点属性
in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2; // 光照贴图UV
in vec3 Normal;

// Uniforms: 从CPU传入的变量
uniform sampler2D Sampler2; // 光照贴图
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform int FogShape;

// Out: 传递给片元着色器的变量
out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;
out vec4 normal;
out vec3 fPos;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    fPos = (ModelViewMat * vec4(Position, 1.0)).xyz;
    vertexDistance = fog_distance(ModelViewMat, Position, FogShape);
    vertexColor = Color * minecraft_sample_lightmap(Sampler2, UV2);
    texCoord0 = UV0;
    normal = ProjMat * ModelViewMat * vec4(Normal, 0.0);
}