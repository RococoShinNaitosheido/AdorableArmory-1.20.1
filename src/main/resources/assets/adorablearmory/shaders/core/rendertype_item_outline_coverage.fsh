#version 150

uniform sampler2D Sampler0;

in vec2 texCoord0;
out vec4 fragColor;

const float SOURCE_ALPHA_THRESHOLD = 0.01;

void main() {
    float alphaMask = texture(Sampler0, texCoord0).a;
    if (alphaMask <= SOURCE_ALPHA_THRESHOLD) {
        discard;
    }

    fragColor = vec4(0.0, 0.0, 0.0, 1.0);
}