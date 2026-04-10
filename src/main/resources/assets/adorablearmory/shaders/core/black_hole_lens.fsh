#version 150

uniform sampler2D SceneTex; // 屏幕纹理

uniform vec2 ScreenSize; // 屏幕分辨率
uniform vec2 CenterPx;  // 黑洞中心坐标
uniform float RadiusPx; // 透镜半径
uniform float StrengthPx; // 核心引力度

uniform float SofteningPx; // 核心点柔化度
uniform float RingRadiusPx;
uniform float RingWidthPx;
uniform float RingStrengthPx;
uniform float RingTwistPx;
uniform float DispersionPx;

uniform float LensDepth01; // 深度

out vec4 fragColor;

float gaussian(float x) {
    return exp(-x * x);
}

float rand(vec2 co) {
    return fract(sin(dot(co, vec2(12.9898, 78.233))) * 43758.5453);
}

vec2 rand2(vec2 p) {
    return vec2(rand(p), rand(p + vec2(19.19, 7.17)));
}

vec3 sampleDispersion(vec2 uv, vec2 dispVec) {
    uv = clamp(uv, vec2(0.0), vec2(1.0));
    float r = texture(SceneTex, clamp(uv + dispVec, vec2(0.0), vec2(1.0))).r;
    float g = texture(SceneTex, uv).g;
    float b = texture(SceneTex, clamp(uv - dispVec, vec2(0.0), vec2(1.0))).b;
    return vec3(r, g, b);
}

vec3 palette(int idx) {
    vec3 p[8] = vec3[8](
        vec3(1.000000, 0.411765, 0.705882), //
        vec3(1.000000, 0.078431, 0.576471), //
        vec3(0.815686, 0.125490, 0.564706), //
        vec3(1.000000, 0.000000, 1.000000), //
        vec3(0.933333, 0.509804, 0.933333), //
        vec3(0.854902, 0.439216, 0.839216), //
        vec3(0.729412, 0.333333, 0.827451), //
        vec3(0.580392, 0.000000, 0.827451)  //
    );
    return p[idx & 7];
}

void main() {
    vec2 fragBottom = gl_FragCoord.xy;
    vec2 fragTop = vec2(fragBottom.x, ScreenSize.y - fragBottom.y);

    vec2 baseUv = clamp(fragBottom / ScreenSize, vec2(0.0), vec2(1.0));
    vec3 baseColor = texture(SceneTex, baseUv).rgb;

    vec2 dPx = fragTop - CenterPx;
    float dist = length(dPx);

    float aa = max(fwidth(dist), 1.0);
    float mask = 1.0 - smoothstep(RadiusPx - aa, RadiusPx + aa, dist);

    if (mask <= 0.0) {
        fragColor = vec4(baseColor, 1.0);
        return;
    }

    float normalizedDist = dist / max(RadiusPx, 1.0);
    float edgeFade = 1.0 - smoothstep(0.35, 1.0, normalizedDist);
    float effectFade = edgeFade * mask;

    gl_FragDepth = LensDepth01;

    vec2 dir = (dist > 0.001) ? (dPx / dist) : vec2(1.0, 0.0);
    vec2 perp = vec2(-dir.y, dir.x);

    float soft = max(SofteningPx, 1.0);
    float r2 = dist * dist + soft * soft;

    float baseDeflect = (-1.5 * StrengthPx) * (RadiusPx * RadiusPx) / r2;
    baseDeflect = clamp(baseDeflect, -0.95 * RadiusPx, 0.95 * RadiusPx);

    float ringW = max(RingWidthPx, 1.0);
    float ringX = (dist - RingRadiusPx) / ringW;
    float ringWeight = gaussian(ringX);

    float ringDeflect = RingStrengthPx * ringWeight;
    float ringTwist = RingTwistPx * ringWeight;

    float centerDamp = smoothstep(0.0, 10.0, dist);
    float totalDeflect = (baseDeflect * centerDamp + ringDeflect) * effectFade;

    float totalTwist = ringTwist * effectFade;
    float maxOff = 0.95 * RadiusPx;
    totalDeflect = clamp(totalDeflect, -maxOff, maxOff);
    totalTwist = clamp(totalTwist, -maxOff, maxOff);

    vec2 offsetTop = dir * totalDeflect + perp * totalTwist;
    vec2 sampleTop = fragTop + offsetTop;

    vec2 sampleBottom = vec2(sampleTop.x, ScreenSize.y - sampleTop.y);
    vec2 sampleUv = clamp(sampleBottom / ScreenSize, vec2(0.0), vec2(1.0));

    float dispFade = pow(clamp(1.0 - normalizedDist, 0.0, 1.0), 0.4); // 中心到边缘的衰减曲线
    float disp = DispersionPx * (0.4 + 6.0 * ringWeight) * dispFade * mask; // 光环区域增幅

    // 计算模糊强度-距离中心越近-模糊程度越高
    float blurStrength = 1.0 - smoothstep(0.0, RadiusPx * 0.98, dist); // 覆盖到接近边缘
    blurStrength *= mask;
    blurStrength = pow(clamp(blurStrength, 0.0, 1.0), 0.75);

    vec2 dispVec = (vec2(dir.x, -dir.y) * disp) / ScreenSize;
    vec3 color;

    if (blurStrength < 0.01) {
        color = sampleDispersion(sampleUv, dispVec);
    } else {

        float cellPx = mix(3.0, 44.0, pow(clamp(blurStrength, 0.0, 1.0), 1.15));
        cellPx = clamp(cellPx, 3.0, 64.0);

        vec2 pPx = sampleUv * ScreenSize;
        vec2 g = floor(pPx / cellPx);
        vec2 f = fract(pPx / cellPx);

        float bestD = 1e9;
        float secondD = 1e9;
        vec2 bestCell = vec2(0.0);
        vec2 bestP = vec2(0.5);

        for (int j = -1; j <= 1; j++) {
            for (int i = -1; i <= 1; i++) {
                vec2 cell = vec2(float(i), float(j));
                vec2 id = g + cell;

                vec2 jitter = rand2(id) - vec2(0.5);
                vec2 p = vec2(0.5) + 0.75 * jitter;

                vec2 r = cell + p - f;
                float d = dot(r, r);

                if (d < bestD) {
                    secondD = bestD;
                    bestD = d;
                    bestCell = cell;
                    bestP = p;
                } else if (d < secondD) {
                    secondD = d;
                }
            }
        }

        vec2 centerPx = (g + bestCell + bestP) * cellPx;
        vec2 centerUv = clamp(centerPx / ScreenSize, vec2(0.0), vec2(1.0));

        float tapUv = (0.20 * cellPx) / max(ScreenSize.x, ScreenSize.y);

        vec3 c0 = sampleDispersion(centerUv, dispVec);
        vec3 c1 = sampleDispersion(centerUv + vec2( tapUv, 0.0), dispVec);
        vec3 c2 = sampleDispersion(centerUv + vec2(-tapUv, 0.0), dispVec);
        vec3 c3 = sampleDispersion(centerUv + vec2(0.0,  tapUv), dispVec);
        vec3 c4 = sampleDispersion(centerUv + vec2(0.0, -tapUv), dispVec);

        vec3 glassColor = c0 * 0.40 + (c1 + c2 + c3 + c4) * 0.15;

        float cellNoise = rand(g + bestCell + vec2(13.7, 9.2));
        glassColor *= (0.88 + 0.22 * cellNoise);

        float r = rand(g + bestCell);
        int idx = int(floor(r * 8.0));
        idx = clamp(idx, 0, 7);

        vec3 tint = palette(idx);

        float lum = dot(glassColor, vec3(0.2126, 0.7152, 0.0722));
        vec3 stained = tint * lum;

        float tintAmount = mix(0.45, 0.90, blurStrength);
        glassColor = mix(glassColor, stained, tintAmount);

        float glow = 1.0; // 基础光度
        float glowByBlur = pow(clamp(blurStrength, 0.0,1.0), 0.6); // 越靠中越亮
        vec3 emissive = tint * glow * (0.35 + 0.65 * glowByBlur);

        glassColor += emissive;
        glassColor = clamp(glassColor, 0.0, 1.0);

        float d1 = sqrt(bestD);
        float d2 = sqrt(secondD);
        float border = d2 - d1;

        float lineWidth = mix(0.20, 0.09, blurStrength);
        float aaLine = max(fwidth(border), 0.002);
        float line = 1.0 - smoothstep(lineWidth - aaLine, lineWidth + aaLine, border);

        vec3 lead = vec3(0.05, 0.03, 0.06);
        float lineStrength = 0.65 * blurStrength;
        glassColor = mix(glassColor, lead, line * lineStrength);

        vec3 baseDistorted = sampleDispersion(sampleUv, dispVec);
        float mixK = smoothstep(0.06, 0.85, blurStrength);
        color = mix(baseDistorted, glassColor, mixK);
    }

    color += ringWeight * 0.10 * effectFade * vec3(1.0, 0.0, 1.0);

    float core = 1.0 - normalizedDist;
    color *= (1.0 - 0.32 * core * core * effectFade);

    vec3 outColor = mix(baseColor, color, mask);
    fragColor = vec4(outColor, 1.0);
}