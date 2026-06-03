#version 150

uniform sampler2D DiffuseSampler;
uniform vec2 TexelSize;
uniform float Progress;
uniform float Time;
uniform float ChromaStrength;
uniform float ChromaPull;
uniform float UomStrength;
uniform float UomRate;
uniform float UomEdgeWeight;
uniform float UomContrast;
uniform float UomContraction;
uniform float UomRadialBlur;
uniform float UomBlurThreshold;
uniform float UomBeamIntensity;
uniform float UomThresholdStrength;
uniform float UomCenterFlash;
uniform float UomInvertStrobe;
uniform float UomMonoStrobe;
uniform float UomWhiteFlash;
uniform float UomVignetteStrength;
uniform float EdgeStart;
uniform float EdgeEnd;

in vec2 texCoord;

out vec4 fragColor;

vec3 sampleScene(vec2 uv) {
    return texture(DiffuseSampler, clamp(uv, vec2(0.0), vec2(1.0))).rgb;
}

float luminance(vec3 color) {
    return dot(color, vec3(0.299, 0.587, 0.114));
}

void main() {
    vec2 center = vec2(0.5);
    vec2 aspect = vec2(1.15, 1.0);
    vec2 d = texCoord - center;
    float dist = length(d * aspect);
    vec2 dir = dist > 1.0e-5 ? normalize(d) : vec2(0.0);
    vec3 originalScene = sampleScene(texCoord);
    float edge = smoothstep(EdgeStart, EdgeEnd, dist);
    edge = edge * edge;

    float uom = clamp(UomStrength, 0.0, 1.45);
    float edgeUom = mix(1.0, edge, clamp(UomEdgeWeight, 0.0, 1.0));
    float flashGate = step(0.50, fract(Time * UomRate));
    float fastGate = step(0.74, fract(Time * UomRate * 2.0 + 0.17));

    float zoomPulse = 0.76 + 0.24 * flashGate;
    float zoom = UomContraction * uom * edgeUom * zoomPulse;
    vec2 zoomUv = center + d * (1.0 + zoom);
    float chromaFade = 1.0 - smoothstep(0.0, 0.62, Progress);
    vec2 pull = -dir * ChromaPull * edge * chromaFade;

    float radialLength = UomRadialBlur * (0.45 + edgeUom * 1.55);
    vec3 radialColor = vec3(0.0);
    vec3 beamColor = vec3(0.0);
    float weightSum = 0.0;
    float beamWeightSum = 0.0;

    for (int i = 0; i < 21; i++) {
        float tap = float(i) / 20.0;
        float centered = tap - 0.5;
        float signedTap = centered < 0.0 ? centered * 1.72 : centered * 0.62;
        float offset = signedTap * radialLength;
        float weight = 1.0 - abs(centered) * 1.18;
        weight = max(weight, 0.075);
        vec2 uv = zoomUv + dir * offset + pull;
        vec2 chroma = dir * (TexelSize * ChromaStrength * edge + radialLength * (0.028 + tap * 0.020));
        float red = sampleScene(uv - chroma * 1.24).r;
        float green = sampleScene(uv).g;
        float blue = sampleScene(uv + chroma * 1.24).b;
        vec3 sampleColor = vec3(red, green, blue);
        float brightGate = smoothstep(UomBlurThreshold, 1.0, luminance(sampleColor));
        float beamWeight = weight * brightGate * (0.35 + abs(centered) * 1.30);
        radialColor += sampleColor * weight;
        beamColor += sampleColor * beamWeight;
        weightSum += weight;
        beamWeightSum += beamWeight;
    }

    radialColor /= max(weightSum, 0.0001);
    beamColor /= max(beamWeightSum, 0.0001);

    vec3 base = sampleScene(zoomUv);
    float radialMix = clamp(uom * (0.42 + edgeUom * 0.78), 0.0, 1.0);
    vec3 color = mix(base, radialColor, radialMix);

    float bright = smoothstep(UomBlurThreshold, 1.0, luminance(beamColor));
    color += beamColor * UomBeamIntensity * (0.35 + edgeUom * 0.85 + bright * 0.55);
    float contrast = 1.0 + UomContrast * uom * (0.58 + bright * 0.62);
    color = (color - 0.5) * contrast + 0.5;

    float centerFlash = exp(-dist * dist * 15.0) * UomCenterFlash * (0.72 + flashGate * 0.28);
    float beamGate = smoothstep(0.001, 0.020, abs(UomBeamIntensity));
    float rayFlash = bright * uom * edgeUom * (0.28 + radialMix * 0.46) * beamGate;
    color += vec3(centerFlash + rayFlash);

    float angle = atan(d.y, d.x);
    float hardStreak = 0.5 + 0.5 * sin(angle * 18.0 + Time * UomRate * 0.72);
    float impactLum = luminance(color) + bright * 0.38 + rayFlash * 0.52 + hardStreak * edgeUom * 0.07;
    float hardThreshold = 0.52 - flashGate * 0.16 - uom * 0.075;
    vec3 mangaMono = vec3(step(hardThreshold, impactLum));

    float invertPulse = step(0.50, fract(Time * UomRate + 0.35));
    mangaMono = mix(mangaMono, vec3(1.0) - mangaMono, clamp(UomInvertStrobe * invertPulse, 0.0, 1.0));

    float centerInk = exp(-dist * dist * 30.0) * uom * (0.54 + flashGate * 0.34);
    float verticalInk = exp(-abs(d.x) * 26.0) * smoothstep(0.04, 0.46, abs(d.y)) * uom;
    float radialInk = (1.0 - bright) * exp(-dist * dist * 9.0) * uom * 0.20;
    mangaMono = mix(mangaMono, vec3(0.0), clamp(centerInk + verticalInk * 0.68 + radialInk, 0.0, 1.0));

    float monoAmount = clamp(uom * UomMonoStrobe * UomThresholdStrength * (0.76 + flashGate * 0.24), 0.0, 1.0);
    color = mix(color, mangaMono, monoAmount);

    float whiteAmount = clamp(UomWhiteFlash * fastGate * uom * (0.24 + edgeUom * 0.34 + bright * 0.25), 0.0, 1.0);
    color = mix(color, vec3(1.0), whiteAmount);

    float burstDriver = clamp(abs(UomWhiteFlash) + abs(UomMonoStrobe) + abs(UomCenterFlash) + abs(UomThresholdStrength) + abs(UomInvertStrobe), 0.0, 1.0);
    float burstActive = smoothstep(0.001, 0.030, uom) * smoothstep(0.001, 0.050, burstDriver);
    float burstProgress = smoothstep(0.04, 0.86, Progress);
    float burstFade = 1.0 - smoothstep(0.60, 0.98, Progress);
    float horizontalDistance = abs(d.x) * 1.16;
    float verticalDistance = abs(d.y);
    float slashReach = mix(0.05, 0.72, burstProgress);
    float slashWidth = mix(0.045, 0.16, burstProgress);
    float slashBand = 1.0 - smoothstep(slashWidth, slashWidth + 0.18, verticalDistance);
    float burstWave = 1.0 - smoothstep(0.0, mix(0.08, 0.16, burstProgress), abs(horizontalDistance - slashReach));
    burstWave *= slashBand;
    float burstCore = 1.0 - smoothstep(slashReach, slashReach + 0.14, horizontalDistance);
    burstCore *= 1.0 - smoothstep(slashWidth * 1.35, slashWidth + 0.22, verticalDistance);
    burstCore *= 0.58 + (1.0 - burstProgress) * 0.24;
    float burstMask = clamp((burstWave + burstCore) * burstActive * burstFade, 0.0, 1.0);

    float dispersionPixels = (8.0 + burstWave * 44.0 + burstCore * 22.0) * burstActive * burstFade * (0.42 + verticalDistance * 1.15 + horizontalDistance * 0.28);
    float burstXSign = d.x < 0.0 ? -1.0 : 1.0;
    float burstYSign = d.y < 0.0 ? -1.0 : 1.0;
    vec2 burstDir = normalize(vec2(burstXSign * 0.32, burstYSign * (1.0 + burstCore * 0.30)));
    vec2 burstOffset = burstDir * TexelSize * dispersionPixels;
    vec3 burstChroma = vec3(
        sampleScene(texCoord - burstOffset * 1.55).r,
        sampleScene(texCoord + burstOffset * 0.20).g,
        sampleScene(texCoord + burstOffset * 1.55).b
    );
    vec3 burstScene = mix(originalScene, burstChroma, clamp(0.20 + burstWave * 0.62 + burstCore * 0.24, 0.0, 0.86));
    vec3 burstTint = mix(vec3(0.36, 0.68, 1.0), vec3(1.0, 0.50, 0.92), clamp(bright * 0.60 + flashGate * 0.24, 0.0, 1.0));
    burstScene += burstTint * burstWave * burstActive * burstFade * 0.16;
    color = mix(color, burstScene, burstMask);

    vec2 border = abs(d) * 2.0;
    float edgeBand = max(border.x, border.y);
    float frameVignette = smoothstep(0.42, 0.98, edgeBand);
    float cornerVignette = smoothstep(0.62, 1.26, length(border));
    float radialVignette = smoothstep(0.36, 0.92, dist);
    float vignette = clamp(max(frameVignette, max(cornerVignette * 0.92, radialVignette * radialVignette * 0.46)), 0.0, 1.0);

    float sceneLum = max(luminance(base), luminance(color));
    float daylightBoost = smoothstep(0.36, 0.94, sceneLum);
    float breakPressure = clamp(uom * (0.70 + edgeUom * 0.30), 0.0, 1.0);
    float multiplyAmount = clamp(UomVignetteStrength * breakPressure * (0.82 + daylightBoost * 0.72) * vignette, 0.0, 0.94);
    vec3 multiplyShadow = mix(vec3(1.0), vec3(0.018, 0.016, 0.026), multiplyAmount);
    color *= multiplyShadow;

    fragColor = vec4(clamp(color, vec3(0.0), vec3(1.0)), 1.0);
}
