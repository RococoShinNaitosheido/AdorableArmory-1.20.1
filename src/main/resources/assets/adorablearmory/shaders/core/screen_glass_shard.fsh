#version 150

uniform sampler2D SceneTex;
uniform sampler2D LiveSceneTex;
uniform vec2 ScreenSize;
uniform vec2 TexelSize;
uniform float Progress;
uniform float ChromaStrength;
uniform float EdgeVisibility;
uniform float MirrorStrength;

in vec2 vUv;
in vec4 vMeta;

out vec4 fragColor;

vec3 sampleScene(vec2 uv) {
    return texture(SceneTex, clamp(uv, vec2(0.0), vec2(1.0))).rgb;
}

vec3 sampleLiveScene(vec2 uv) {
    return texture(LiveSceneTex, clamp(uv, vec2(0.0), vec2(1.0))).rgb;
}

float luminance(vec3 color) {
    return dot(color, vec3(0.2126, 0.7152, 0.0722));
}

vec3 sampleChroma(vec2 uv, vec2 dir, float amount) {
    vec2 offset = dir * TexelSize * amount;
    float r = sampleScene(uv + offset).r;
    float g = sampleScene(uv).g;
    float b = sampleScene(uv - offset).b;
    return vec3(r, g, b);
}

vec3 sampleLiveChroma(vec2 uv, vec2 dir, float amount) {
    vec2 offset = dir * TexelSize * amount;
    float r = sampleLiveScene(uv + offset).r;
    float g = sampleLiveScene(uv).g;
    float b = sampleLiveScene(uv - offset).b;
    return vec3(r, g, b);
}

float schlickFresnel(float cosTheta, float f0) {
    float oneMinus = 1.0 - clamp(cosTheta, 0.0, 1.0);
    return f0 + (1.0 - f0) * oneMinus * oneMinus * oneMinus * oneMinus * oneMinus;
}

void main() {
    float edge = clamp(vMeta.r, 0.0, 1.0);
    float shade = clamp(vMeta.g, 0.0, 1.0);
    float lift = clamp(vMeta.b, 0.0, 1.0);
    float alpha = clamp(vMeta.a, 0.0, 1.0);

    if (alpha <= 0.002) {
        discard;
    }

    vec2 aspectDir = (vUv - vec2(0.5)) * vec2(ScreenSize.x / max(ScreenSize.y, 1.0), 1.0);
    vec2 dir = normalize(aspectDir + vec2(0.001, 0.0));
    vec2 fragUv = gl_FragCoord.xy * TexelSize;
    vec2 motionParallax = fragUv - vUv;
    vec2 normalXY = clamp(dir * (0.055 + edge * 0.105) + motionParallax * vec2(1.18, 0.92) * (0.34 + lift * 0.28), vec2(-0.42), vec2(0.42));
    vec3 normal = normalize(vec3(normalXY, 1.0 - shade * 0.26));
    vec3 viewDir = vec3(0.0, 0.0, 1.0);
    float cosTheta = clamp(dot(normal, viewDir), 0.0, 1.0);
    float baseFresnel = schlickFresnel(cosTheta, 0.04);
    float edgeFresnel = pow(edge, 1.85) * (0.46 + lift * 0.34);
    float sideFace = smoothstep(0.88, 1.0, edge) * smoothstep(0.48, 1.0, shade);
    float fresnel = clamp(baseFresnel + edgeFresnel + sideFace * 0.18, 0.0, 1.0);

    vec2 refractDir = normalize(normal.xy + dir * 0.22 + vec2(0.001, 0.0));
    vec2 refractionUv = vUv + normal.xy * TexelSize * (4.5 + lift * 10.5 + edge * 8.0 + sideFace * 8.0);
    float chroma = (0.42 + lift * 1.10 + edge * 0.95 + sideFace * 0.55) * ChromaStrength * (0.40 + Progress * 0.42);

    vec3 scene = sampleScene(vUv);
    vec3 dispersed = sampleChroma(refractionUv, refractDir, chroma);
    float dispersedLum = luminance(dispersed);
    float brightPlate = smoothstep(0.62, 1.0, dispersedLum);
    vec3 compressed = dispersed / (1.0 + dispersedLum * (0.32 + brightPlate * 0.46));

    vec3 color = mix(scene, compressed, 0.30 + edge * 0.15 + lift * 0.05);
    vec3 clearTint = mix(vec3(0.72, 0.86, 0.96), vec3(0.88, 0.96, 1.0), lift);
    color = mix(color, clearTint, 0.024 + lift * 0.024 + edge * 0.018);

    float glassLum = luminance(color);
    color = mix(color, vec3(glassLum), shade * 0.022);
    color *= 1.0 - shade * (0.035 + lift * 0.020);

    float glancing = pow(clamp(dot(normal.xy, normalize(vec2(-0.64, -0.42))) * 0.5 + 0.5, 0.0, 1.0), 5.5);
    vec3 incident = normalize(vec3(dir * 0.34 + motionParallax * 1.15, -1.0));
    vec3 reflected = reflect(incident, normal);
    vec2 reflectionDir = normalize(reflected.xy + motionParallax * vec2(1.10, 0.88) + vec2(0.001, 0.0));
    vec2 reflectionUv = fragUv + motionParallax * (0.18 + lift * 0.12) + reflectionDir * TexelSize * (9.0 + lift * 16.0 + edge * 20.0 + sideFace * 14.0);
    vec3 liveReflection = sampleLiveChroma(reflectionUv, reflectionDir, ChromaStrength * (0.18 + edge * 0.30 + lift * 0.22 + sideFace * 0.18));
    float reflectionLum = luminance(liveReflection);
    vec3 reflectionSpec = liveReflection / (1.0 + reflectionLum * 0.36);
    reflectionSpec += vec3(0.72, 0.88, 1.0) * glancing * fresnel * 0.10;
    float reflectionMix = clamp((0.035 + fresnel * 0.58 + glancing * 0.12 + lift * 0.035) * MirrorStrength * (0.62 + EdgeVisibility * 0.58), 0.0, 0.58);
    color = mix(color, reflectionSpec, reflectionMix);

    vec3 rim = mix(vec3(0.66, 0.82, 1.0), vec3(0.98, 0.92, 0.78), lift);
    vec3 clearSpec = mix(vec3(0.40, 0.76, 1.0), vec3(1.0, 0.68, 0.96), lift);
    color += rim * fresnel * EdgeVisibility * (0.16 + Progress * 0.10);
    color += clearSpec * glancing * fresnel * EdgeVisibility * 0.13;
    color = mix(color, color * vec3(0.58, 0.62, 0.68), edge * shade * 0.055);

    glassLum = luminance(color);
    vec3 sideGlass = vec3(glassLum) * 0.17 + rim * (0.28 + lift * 0.22);
    color = mix(color, sideGlass, sideFace * 0.52);
    color += rim * sideFace * (0.055 + EdgeVisibility * 0.13);

    float maxFaceBrightness = 0.76 + edge * 0.24 + lift * 0.08;
    float maxChannel = max(max(color.r, color.g), color.b);
    color *= min(1.0, maxFaceBrightness / max(maxChannel, 0.001));

    float faceCoverage = 0.12 + edge * 0.38 + fresnel * 0.22 + sideFace * 0.10;
    float brightAlphaCut = 1.0 - brightPlate * 0.20;
    float finalAlpha = alpha * faceCoverage * brightAlphaCut;

    fragColor = vec4(clamp(color, vec3(0.0), vec3(1.0)), finalAlpha);
}
