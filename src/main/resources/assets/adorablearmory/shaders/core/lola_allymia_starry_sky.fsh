#version 150

mat2 mat2_rotate_z(float radians) {
    return mat2(
        cos(radians), -sin(radians),
        sin(radians), cos(radians)
    );
}

mat2 rot(float a) {
    float c = cos(a), s = sin(a);
    return mat2(c,-s,s,c);
}

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform float GameTime;
uniform int EndPortalLayers;
uniform float StarScale; // 整体层缩放倍数
uniform float Opacity; // 控制亮度与alpha

uniform float RainbowSpeed; // 彩虹 hue 随时间的推进速率 增大->更快颜色滚动
uniform float RainbowScale; // 颜色在空间中变化的波长/频率
uniform float RainbowMix; // 0 = 原 COLORS - 1 = 全彩

in vec4 texProj0;
out vec4 fragColor;

const vec3 COLORS[16] = vec3[16](
    vec3(0.045, 0.180, 0.220),
    vec3(0.025, 0.160, 0.150),
    vec3(0.055, 0.190, 0.180),
    vec3(0.075, 0.200, 0.190),
    vec3(0.095, 0.210, 0.160),
    vec3(0.085, 0.140, 0.220),
    vec3(0.120, 0.180, 0.280),
    vec3(0.140, 0.250, 0.140),
    vec3(0.160, 0.220, 0.320),
    vec3(0.150, 0.180, 0.300),
    vec3(0.200, 0.230, 0.240),
    vec3(0.110, 0.380, 0.360),
    vec3(0.280, 0.220, 0.320),
    vec3(0.080, 0.480, 0.490),
    vec3(0.320, 0.580, 0.460),
    vec3(0.140, 0.480, 0.880)
);

const mat4 SCALE_TRANSLATE = mat4(
    0.5, 0.0, 0.0, 0.25,
    0.0, 0.5, 0.0, 0.25,
    0.0, 0.0, 1.0, 0.0,
    0.0, 0.0, 0.0, 1.0
);

// 彩虹
vec3 rainbow(float h) {
    float x = fract(h);
    const float TAU = 6.2831853;
    return 0.5 + 0.5 * cos(TAU * (x + vec3(0.0, 0.3333333, 0.6666667)));
}

mat4 end_portal_layer(float layer) {
    float t = mod(GameTime * 0.032, 100.0);
    float layerTimeOffset = layer * 0.5;

    float tx = sin(t + layerTimeOffset) * 2.0 + layer * 0.3;
    float ty = cos(t + layerTimeOffset) * 2.0 + layer * 0.2;

    float rotationSpeed = 0.1 + layer * 0.05;
    float rot = radians(t * rotationSpeed + layer * 45.0);
    mat2 R = mat2_rotate_z(rot);

    float baseScale = 1.5 + sin(layer * 0.5) * 0.3;
    float sc = baseScale * StarScale;

    mat4 RS = mat4(
        sc * R[0][0], sc * R[0][1], 0.0, 0.0,
        sc * R[1][0], sc * R[1][1], 0.0, 0.0,
        0.0, 0.0, 1.0, 0.0,
        0.0, 0.0, 0.0, 1.0
    );

    mat4 T = mat4(
        1.0,0.0,0.0,tx,
        0.0,1.0,0.0,ty,
        0.0,0.0,1.0,0.0,
        0.0,0.0,0.0,1.0
    );
    return RS * T * SCALE_TRANSLATE;
}

void main(){
    vec2 projUV = texProj0.xy / texProj0.w;
    float baseHue = GameTime * RainbowSpeed + dot(projUV, vec2(1.0)) * RainbowScale;

    // 基底层 - Sampler0
    vec3 baseSample = textureProj(Sampler0, texProj0).rgb;
    vec3 baseRainbow = rainbow(baseHue);
    vec3 baseTint = mix(COLORS[0], baseRainbow, RainbowMix);
    vec3 color = baseSample * baseTint;

    // 层叠
    int n = min(EndPortalLayers, 15);
    for(int i = 0; i < n; i++) {
        vec4 coord = texProj0 * end_portal_layer(float(i + 1));

        if (coord.w > 0.0) {
            vec3 Sample = textureProj(Sampler1, coord).rgb;

            // 层 Hue 偏移 - 每层 0.08 位差
            vec2 layerUV = coord.xy / coord.w;
            float h = baseHue + float(i) * 0.08 + dot(layerUV, vec2(0.5)) * RainbowScale * 0.25;
            vec3 rb = rainbow(h);
            vec3 tint = mix(COLORS[i+1], rb, RainbowMix);

            color += Sample * tint * max(0.0, 1.0 - float(i) * 0.1);
        }
    }

    vec3 finalColor = color * 2.0; finalColor = clamp(finalColor, 0.0, 1.0);
    finalColor *= Opacity;
    fragColor = vec4(finalColor, Opacity);
}
