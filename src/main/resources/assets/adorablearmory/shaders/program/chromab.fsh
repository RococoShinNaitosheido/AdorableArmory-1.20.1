#version 150

uniform sampler2D DiffuseSampler;

in vec2 texCoord;
in vec2 oneTexel;

uniform float ShiftX; // 正数 = 红往 -X 蓝往 +X
uniform float ShiftY; // 正数 = 红往 -Y 蓝往 +Y
uniform float Radial; // 0-1 径向增强系数
uniform float Amount; // 0-1 效果强度/淡入淡出

uniform float ChromaTime;  // 秒
uniform float VigWidth;  // vignette 宽度 -0.12~0.25
uniform float VigStrength; // vignette 强度 0-1
uniform float VigPulse; // 呼吸幅度 0-0.2

out vec4 fragColor;

#define SAMPLES 12 // 采样次数-越高越平滑
#define TAU 6.28318530718

void main() {
    vec2 centered = texCoord - vec2(0.5);

    vec2 centeredPx = centered / oneTexel;
    float distPx = length(centeredPx);
    vec2 dirPx = (distPx > 1e-6) ? (centeredPx / distPx) : vec2(0.0);

    float distNorm = distPx * min(oneTexel.x, oneTexel.y);
    float r = distNorm * Radial;
    float radialScale = (1.0 + r * 2.0);

    float shiftPx = (ShiftX + ShiftY) * 0.5;

    vec2 finalShift = dirPx * shiftPx * oneTexel * radialScale;

    vec3 accumColor = vec3(0.0);
    float totalWeight = 0.0;

    for (int i = 0; i < SAMPLES; i++) {
        float t = (float(i) / float(SAMPLES - 1)) - 0.5;
        float blurScale = t * Amount * 1.2;

        vec2 uvR = texCoord - finalShift * (1.0 + blurScale);
        vec2 uvG = texCoord + finalShift * (blurScale * 0.5);
        vec2 uvB = texCoord + finalShift * (1.0 - blurScale);

        uvR = clamp(uvR, vec2(0.0), vec2(1.0));
        uvG = clamp(uvG, vec2(0.0), vec2(1.0));
        uvB = clamp(uvB, vec2(0.0), vec2(1.0));

        float weight = 1.0 - abs(t);

        accumColor.r += texture(DiffuseSampler, uvR).r * weight;
        accumColor.g += texture(DiffuseSampler, uvG).g * weight;
        accumColor.b += texture(DiffuseSampler, uvB).b * weight;

        totalWeight += weight;
    }

    vec3 blurredColor = accumColor / totalWeight;
    vec3 baseRgb = texture(DiffuseSampler, texCoord).rgb;
    vec3 outRgb = mix(baseRgb, blurredColor, clamp(Amount, 0.0, 1.0));

    float edgeDist = min(min(texCoord.x, 1.0 - texCoord.x), min(texCoord.y, 1.0 - texCoord.y));

    float breath = sin(TAU * ChromaTime / 2.2);
    float width = max(1e-4, VigWidth * (1.0 + VigPulse * breath));
    float edgeAlpha = 1.0 - smoothstep(0.0, width, edgeDist);

    float a = clamp(Amount, 0.0, 1.0);
    edgeAlpha *= (a * a) * clamp(VigStrength, 0.0, 1.0);
    edgeAlpha = edgeAlpha * edgeAlpha;

    float osc = 0.5 + 0.5 * cos(TAU * ChromaTime / 4.0);

    vec3 violet = vec3(183.0/255.0, 85.0/255.0, 255.0/255.0);
    vec3 edgeColor = mix(vec3(0.0), violet, osc);

    outRgb = mix(outRgb, edgeColor, edgeAlpha);
    fragColor = vec4(outRgb, 1.0);
}
