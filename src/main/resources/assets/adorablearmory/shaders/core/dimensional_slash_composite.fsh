#version 150

uniform sampler2D DiffuseSampler;
uniform float Intensity;
uniform float Alpha;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec4 bloom = texture(DiffuseSampler, clamp(texCoord, vec2(0.0), vec2(1.0)));
    float brightness = max(max(bloom.r, bloom.g), bloom.b);
    float alpha = clamp(brightness * Alpha, 0.0, 1.0);
    fragColor = vec4(bloom.rgb * Intensity, alpha);
}
