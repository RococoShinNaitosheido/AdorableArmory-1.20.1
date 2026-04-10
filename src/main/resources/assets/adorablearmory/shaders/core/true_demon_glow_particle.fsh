#version 150

in vec4 vertexColor;
in vec2 texCoords;

out vec4 fragColor;

uniform float radius;   // 实心区半径 [UV 空间] - 越大中心越大
uniform float feather;  // 边缘过渡宽度 - 越大越软 - 发光显得更宽
uniform float gamma;    // 过渡更柔软 - 过渡更硬
uniform float exposure; // 整体亮度

// 高斯模糊
float gaussian(float d, float sigma) {
    return exp(-(d * d) / (2.0 * sigma * sigma));
}

void main() {
    vec2 centered = texCoords - 0.5;
    float d = length(centered);

    float maxR = 0.5;
    float dn = d / maxR;

    float sigma = max(feather, 1e-4);
    float core = radius;

    float t = max(dn - core, 0.0);
    float g = gaussian(t, sigma);

    float a = pow(clamp(g, 0.0, 1.0), 1.0 / max(gamma, 1e-4));

    if (dn > 1.0) {
        fragColor = vec4(0.0);
        return;
    }

    float alphaCombined = a * vertexColor.a;
    fragColor = vec4(vertexColor.rgb * exposure, alphaCombined);

    float n = fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453);
    fragColor.rgb += (n - 0.5) * (1.0 / 255.0);
    fragColor.rgb = clamp(fragColor.rgb, 0.0, 1.0);
}