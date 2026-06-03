#version 150

uniform sampler2D DiffuseSampler;

in vec2 texCoord;
in vec2 oneTexel;

uniform vec2 InSize;
uniform float Amount;
uniform float Life;
uniform float BreakTime;
uniform float Seed;
uniform float Flash;
uniform vec2 Center;
uniform float ShardCells;
uniform float ShardMove;
uniform float GapWidth;
uniform float GapDarkness;
uniform float RefractionStrength;
uniform float FragmentTint;
uniform float EdgeVisibility;
uniform float SparkStrength;
uniform float FlashContrast;
uniform float FlashFill;
uniform float CrackVisibility;
uniform float ExitStart;
uniform float ExitShardMove;
uniform float ExitFlash;

out vec4 fragColor;

#define PI 3.14159265359
#define MAX_CELL_COUNT 48

float hash(float n) {
    return fract(sin(n * 17.137 + Seed * 3.911) * 43758.5453123);
}

vec2 hash2(float n) {
    vec2 v = vec2(hash(n) * 2.0 - 1.0, hash(n + 31.7) * 2.0 - 1.0);
    return normalize(v + vec2(0.001, 0.0));
}

float smooth01(float x) {
    x = clamp(x, 0.0, 1.0);
    return x * x * (3.0 - 2.0 * x);
}

vec2 rotate2(vec2 p, float angle) {
    float c = cos(angle);
    float s = sin(angle);
    return vec2(p.x * c - p.y * s, p.x * s + p.y * c);
}

float softInsideUv(vec2 uv) {
    float edge = min(min(uv.x, 1.0 - uv.x), min(uv.y, 1.0 - uv.y));
    return smoothstep(-0.045, 0.022, edge);
}

vec3 sampleScene(vec2 uv) {
    return texture(DiffuseSampler, clamp(uv, vec2(0.0), vec2(1.0))).rgb;
}

vec2 cellPoint(float i, vec2 aspect) {
    float region = hash(i + 7.0);

    if (region < 0.36) {
        float a = hash(i + 13.0) * PI * 2.0;
        float r = pow(hash(i + 17.0), 1.55) * 0.72;
        vec2 p = vec2(cos(a), sin(a)) * r;
        p.x *= aspect.x;
        return p + hash2(i + 19.0) * 0.045;
    }

    float u = hash(i + 23.0) * 2.34 - 1.17;
    float v = hash(i + 29.0) * 2.22 - 1.11;
    vec2 p = vec2(u * aspect.x, v);
    p += hash2(i + 31.0) * vec2(0.050 * aspect.x, 0.050);
    return p;
}

void nearestCells(vec2 p, vec2 aspect, out float nearestId, out float secondId, out vec2 nearestPoint, out vec2 secondPoint, out float nearestD, out float secondD) {
    nearestId = 0.0;
    secondId = 0.0;
    nearestPoint = vec2(0.0);
    secondPoint = vec2(0.0);
    nearestD = 1e9;
    secondD = 1e9;

    for (int i = 0; i < MAX_CELL_COUNT; i++) {
        if (float(i) >= ShardCells) {
            continue;
        }

        float fi = float(i);
        vec2 point = cellPoint(fi, aspect);
        vec2 diff = p - point;
        float d = dot(diff, diff);

        if (d < nearestD) {
            secondD = nearestD;
            secondId = nearestId;
            secondPoint = nearestPoint;
            nearestD = d;
            nearestId = fi;
            nearestPoint = point;
        } else if (d < secondD) {
            secondD = d;
            secondId = fi;
            secondPoint = point;
        }
    }
}

float radialReveal(vec2 p, vec2 aspect, float startLife, float endLife, float softness) {
    float maxRadius = length(vec2(aspect.x, 1.0)) * 0.72;
    float front = mix(0.035, maxRadius * 1.22, smooth01((Life - startLife) / max(endLife - startLife, 0.001)));
    return 1.0 - smoothstep(front, front + softness, length(p));
}

float cellRelease(float id, vec2 point, vec2 aspect) {
    float maxRadius = length(vec2(aspect.x, 1.0)) * 0.72;
    float centerOrder = clamp(length(point) / max(maxRadius, 0.001), 0.0, 1.0);
    float delay = ExitStart + centerOrder * 0.20 + hash(id + 101.0) * 0.10;
    float width = 0.13 + hash(id + 107.0) * 0.10;
    return smooth01((Life - delay) / width);
}

void main() {
    vec2 aspect = vec2(InSize.x / max(InSize.y, 1.0), 1.0);
    vec2 p = (texCoord - Center) * aspect;
    vec3 original = texture(DiffuseSampler, texCoord).rgb;

    float id0;
    float id1;
    vec2 point0;
    vec2 point1;
    float d0;
    float d1;
    nearestCells(p, aspect, id0, id1, point0, point1, d0, d1);

    float dist0 = sqrt(d0);
    float dist1 = sqrt(d1);
    float edgeDistance = dist1 - dist0;
    float crackWidth = (0.010 + hash(id0 + id1 + 61.0) * 0.010) * GapWidth;
    float fineCrack = 1.0 - smoothstep(crackWidth * 0.12, crackWidth, edgeDistance);
    float broadStress = 1.0 - smoothstep(crackWidth, crackWidth * 5.0, edgeDistance);

    float pixelReveal = radialReveal(p, aspect, 0.00, 0.45, 0.16);
    float cellReveal0 = radialReveal(point0, aspect, 0.02, 0.46, 0.20);
    float cellReveal1 = radialReveal(point1, aspect, 0.02, 0.46, 0.20);
    float fractureReveal = min(pixelReveal, min(cellReveal0, cellReveal1));
    float wholeScreenReady = smooth01((Life - 0.38) / 0.18);
    float impact = smooth01((Life - ExitStart) / 0.14);
    float release0 = cellRelease(id0, point0, aspect);
    float release1 = cellRelease(id1, point1, aspect);
    float pairRelease = min(release0, release1);

    vec2 outward = normalize(point0 + hash2(id0 + 127.0) * 0.22 + vec2(0.001, 0.0));
    vec2 tangent = vec2(-outward.y, outward.x);
    float sideScatter = (hash(id0 + 131.0) - 0.5) * 0.64;
    vec2 driftDir = normalize(outward + tangent * sideScatter);

    float entryShift = wholeScreenReady * (0.006 + hash(id0 + 137.0) * 0.010) * ShardMove;
    float breakShift = release0 * release0 * (0.038 + hash(id0 + 139.0) * 0.060) * ExitShardMove;
    vec2 fragmentOffset = driftDir * (entryShift + breakShift);

    float spinSign = hash(id0 + 149.0) < 0.5 ? -1.0 : 1.0;
    float rotation = spinSign * release0 * release0 * (0.08 + hash(id0 + 151.0) * 0.42) * ExitShardMove;
    vec2 local = p - point0;
    vec2 sampleP = point0 + rotate2(local - fragmentOffset, -rotation);

    vec2 splitNormal = normalize(point0 - point1 + vec2(0.001, 0.0));
    float refractionMask = (broadStress * 0.35 + fineCrack * 0.80) * fractureReveal;
    vec2 refract = splitNormal * (0.004 + 0.010 * wholeScreenReady + 0.020 * pairRelease) * RefractionStrength * refractionMask;
    vec2 sampleUv = Center + (sampleP + refract) / aspect;
    float sampleInside = softInsideUv(sampleUv);

    vec3 base = sampleScene(sampleUv);
    vec2 chroma = splitNormal / aspect * oneTexel * (2.0 + 5.5 * wholeScreenReady + 7.5 * pairRelease) * RefractionStrength;
    vec3 red = sampleScene(sampleUv + chroma);
    vec3 blue = sampleScene(sampleUv - chroma);
    vec3 rgb = vec3(red.r, base.g, blue.b);
    rgb = mix(original, rgb, sampleInside);

    float crack = fineCrack * fractureReveal * (1.0 - release0 * 0.34);
    float crackDark = crack * (0.20 + GapDarkness * 0.55) * CrackVisibility * Amount;
    rgb = mix(rgb, rgb * vec3(0.28, 0.30, 0.34), clamp(crackDark, 0.0, 0.50));

    float bevel = broadStress * fractureReveal * (0.22 + 0.44 * release0) * EdgeVisibility * Amount;
    vec3 bevelColor = mix(vec3(0.78, 0.74, 0.70), vec3(0.46, 0.47, 0.52), release0);
    rgb = mix(rgb, bevelColor, clamp(bevel, 0.0, 0.32));

    float tintMask = release0 * fractureReveal * FragmentTint * Amount;
    vec3 tint = mix(vec3(0.82, 0.78, 0.88), vec3(0.92, 0.70, 0.78), hash(id0 + 163.0));
    rgb = mix(rgb, tint, tintMask * 0.20);

    float impactFlash = smoothstep(ExitStart + 0.02, ExitStart + 0.10, Life) * (1.0 - smoothstep(ExitStart + 0.16, ExitStart + 0.30, Life));
    float sparkMask = step(0.987, hash(floor(texCoord.x * 96.0) + floor(texCoord.y * 54.0) * 113.0 + id0 * 17.0));
    rgb = mix(rgb, vec3(1.0), sparkMask * fineCrack * impactFlash * (0.24 + SparkStrength * 0.28) * Amount);
    rgb += vec3(1.0, 0.84, 0.72) * impactFlash * ExitFlash * 0.35 * Amount;

    float strobeWindow = (1.0 - smoothstep(0.0, 0.24, Life)) + impactFlash * 0.62;
    strobeWindow = min(strobeWindow, 1.0) * min(abs(Flash), 1.0);
    float lum = dot(rgb, vec3(0.299, 0.587, 0.114));
    vec3 posterized = Flash >= 0.0 ? vec3(smoothstep(0.42, 0.62, lum)) : vec3(1.0 - smoothstep(0.38, 0.58, lum));
    rgb = mix(rgb, posterized, strobeWindow * 0.78 * FlashContrast);

    float flashFill = min(abs(Flash), 1.0) * (1.0 - smoothstep(0.0, 0.18, Life));
    rgb = mix(rgb, Flash >= 0.0 ? vec3(1.0) : vec3(0.0), flashFill * FlashFill);

    rgb = mix(original, rgb, fractureReveal * Amount);
    fragColor = vec4(clamp(rgb, vec3(0.0), vec3(1.0)), 1.0);
}
