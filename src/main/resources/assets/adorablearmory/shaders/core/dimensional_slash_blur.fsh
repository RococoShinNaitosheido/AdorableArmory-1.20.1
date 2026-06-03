#version 150

uniform sampler2D DiffuseSampler;
uniform vec2 TexelSize;
uniform vec2 Direction;
uniform float Radius;
uniform float Intensity;

in vec2 texCoord;

out vec4 fragColor;

vec4 sampleBloom(vec2 uv) {
    return texture(DiffuseSampler, clamp(uv, vec2(0.0), vec2(1.0)));
}

void main() {
    vec2 delta = Direction * TexelSize * Radius;
    vec4 color = sampleBloom(texCoord) * 0.2270270270;
    color += sampleBloom(texCoord + delta * 1.3846153846) * 0.3162162162;
    color += sampleBloom(texCoord - delta * 1.3846153846) * 0.3162162162;
    color += sampleBloom(texCoord + delta * 3.2307692308) * 0.0702702703;
    color += sampleBloom(texCoord - delta * 3.2307692308) * 0.0702702703;
    fragColor = vec4(color.rgb * Intensity, color.a);
}
