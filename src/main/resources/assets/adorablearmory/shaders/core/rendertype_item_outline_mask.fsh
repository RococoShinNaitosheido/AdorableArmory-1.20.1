#version 150

uniform sampler2D Sampler0;

in vec2 texCoord0;
flat in ivec3 outlineColor;
flat in int outlineRadius;

out vec4 fragColor;

const float SOURCE_ALPHA_THRESHOLD = 0.01;
const float MAX_OUTLINE_RADIUS = 8.0;

void main() {
    float alphaMask = texture(Sampler0, texCoord0).a;
    if (alphaMask <= SOURCE_ALPHA_THRESHOLD) {
        discard;
    }

    vec3 rgb = vec3(outlineColor) / 255.0;

    float finalRadius = clamp(float(outlineRadius), 1.0, MAX_OUTLINE_RADIUS);
    float encodedRadius = finalRadius / MAX_OUTLINE_RADIUS;

    fragColor = vec4(rgb, encodedRadius);
}
