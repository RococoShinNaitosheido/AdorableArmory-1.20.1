#version 150

uniform sampler2D SceneTex;
uniform vec2 ScreenSize;
uniform vec2 TexelSize;
uniform float RefractionStrength;
uniform float EdgeStrength;
uniform float MirrorStrength;
uniform float Time;

in vec2 vUv;
in vec4 vMeta;
in vec3 vNormal;
in vec3 vViewPos;

out vec4 fragColor;

vec3 sampleScene(vec2 uv) {
    vec2 safeUv = clamp(uv, TexelSize * 1.5, vec2(1.0) - TexelSize * 1.5);
    return texture(SceneTex, safeUv).rgb;
}

vec3 sampleChroma(vec2 uv, vec2 offset) {
    vec3 color;
    color.r = sampleScene(uv + offset * 1.45).r;
    color.g = sampleScene(uv + offset * 0.40).g;
    color.b = sampleScene(uv - offset * 1.20).b;
    return color;
}

float luminance(vec3 color) {
    return dot(color, vec3(0.2126, 0.7152, 0.0722));
}

void main() {
    float edge = clamp(vMeta.r, 0.0, 1.0);
    float shade = clamp(vMeta.g, 0.0, 1.0);
    float phase = clamp(vMeta.b, 0.0, 1.0);
    float alphaIn = clamp(vMeta.a, 0.0, 1.0);
    if (alphaIn <= 0.0001) {
        discard;
    }

    vec2 screenUv = gl_FragCoord.xy / max(ScreenSize, vec2(1.0));
    vec2 local = vUv - vec2(0.5);
    vec3 viewRay = normalize(vViewPos);
    vec3 viewToCamera = -viewRay;
    vec3 normal = normalize(vNormal);
    normal = faceforward(normal, viewRay, normal);

    float facing = clamp(dot(normal, viewToCamera), 0.0, 1.0);
    float grazing = pow(1.0 - facing, 2.0);
    float sideFace = smoothstep(0.88, 1.0, edge) * smoothstep(0.48, 1.0, shade);
    float rim = smoothstep(0.18, 0.78, edge);

    float shardNoise = sin((local.x + phase) * 41.0 + Time * 0.11) * 0.5
            + sin((local.y - phase) * 57.0 - Time * 0.07) * 0.5;
    vec2 refractDir = normalize(local + normal.xy * 0.45 + vec2(0.001, 0.0));
    vec2 refractionOffset = refractDir * TexelSize * RefractionStrength
            * (0.72 + rim * 1.35 + shardNoise * 0.16);

    vec3 refracted = sampleChroma(screenUv, refractionOffset);

    vec3 reflectedRay = reflect(viewRay, normal);
    vec2 reflectionDir = reflectedRay.xy / max(abs(reflectedRay.z) + 0.35, 0.35);
    vec2 reflectionOffset = reflectionDir * TexelSize
            * (18.0 + edge * 38.0 + grazing * 44.0)
            * MirrorStrength;
    vec3 reflected = sampleScene(screenUv + reflectionOffset);
    reflected = mix(reflected, sampleScene(screenUv + reflectionOffset * 0.55 + normal.xy * TexelSize * 10.0), 0.34);

    float mirrorMix = clamp((0.28 + grazing * 0.46 + rim * 0.20 + sideFace * 0.18) * MirrorStrength, 0.0, 0.86);
    vec3 glass = mix(refracted, reflected, mirrorMix);
    float luma = luminance(glass);
    glass = mix(glass, vec3(luma), 0.08 + shade * 0.12);

    vec3 rimColor = mix(vec3(0.76, 0.84, 1.0), vec3(1.0), rim);
    glass += rimColor * rim * EdgeStrength * (0.18 + shade * 0.22 + grazing * 0.32);
    glass = mix(glass, vec3(luma) * 0.34 + rimColor * 0.34, sideFace * 0.48);
    glass *= 0.82 + shade * 0.18 + facing * 0.08;

    float alpha = clamp(alphaIn * (0.50 + rim * 0.38 + sideFace * 0.18), 0.0, 1.0);
    fragColor = vec4(clamp(glass, vec3(0.0), vec3(1.0)), alpha);
}
