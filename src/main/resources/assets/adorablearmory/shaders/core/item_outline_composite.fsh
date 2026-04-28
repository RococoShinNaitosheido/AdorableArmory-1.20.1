#version 150

uniform sampler2D OutlineSampler;
uniform sampler2D OutlineDepthSampler;
uniform sampler2D MainDepthSampler;

uniform float AlphaThreshold;
uniform float MaxSearchRadius;
uniform float DepthEpsilon;
uniform float EnableDepthOcclusion;
uniform float UseFirstPersonHandFastPath;

out vec4 fragColor;

const int MAX_RADIUS_PX = 8;
const int NO_DISTANCE = 2147483647;

const int FAST_OFFSET_COUNT = 196;
const ivec3 FAST_OFFSETS[FAST_OFFSET_COUNT] = ivec3[](ivec3(0, -1, 1), ivec3(-1, 0, 1), ivec3(1, 0, 1), ivec3(0, 1, 1), ivec3(-1, -1, 2), ivec3(1, -1, 2), ivec3(-1, 1, 2), ivec3(1, 1, 2), ivec3(0, -2, 4), ivec3(-2, 0, 4), ivec3(2, 0, 4), ivec3(0, 2, 4), ivec3(-1, -2, 5), ivec3(1, -2, 5), ivec3(-2, -1, 5), ivec3(2, -1, 5), ivec3(-2, 1, 5), ivec3(2, 1, 5), ivec3(-1, 2, 5), ivec3(1, 2, 5), ivec3(-2, -2, 8), ivec3(2, -2, 8), ivec3(-2, 2, 8), ivec3(2, 2, 8), ivec3(0, -3, 9), ivec3(-3, 0, 9), ivec3(3, 0, 9), ivec3(0, 3, 9), ivec3(-1, -3, 10), ivec3(1, -3, 10), ivec3(-3, -1, 10), ivec3(3, -1, 10), ivec3(-3, 1, 10), ivec3(3, 1, 10), ivec3(-1, 3, 10), ivec3(1, 3, 10), ivec3(-2, -3, 13), ivec3(2, -3, 13), ivec3(-3, -2, 13), ivec3(3, -2, 13), ivec3(-3, 2, 13), ivec3(3, 2, 13), ivec3(-2, 3, 13), ivec3(2, 3, 13), ivec3(0, -4, 16), ivec3(-4, 0, 16), ivec3(4, 0, 16), ivec3(0, 4, 16), ivec3(-1, -4, 17), ivec3(1, -4, 17), ivec3(-4, -1, 17), ivec3(4, -1, 17), ivec3(-4, 1, 17), ivec3(4, 1, 17), ivec3(-1, 4, 17), ivec3(1, 4, 17), ivec3(-3, -3, 18), ivec3(3, -3, 18), ivec3(-3, 3, 18), ivec3(3, 3, 18), ivec3(-2, -4, 20), ivec3(2, -4, 20), ivec3(-4, -2, 20), ivec3(4, -2, 20), ivec3(-4, 2, 20), ivec3(4, 2, 20), ivec3(-2, 4, 20), ivec3(2, 4, 20), ivec3(0, -5, 25), ivec3(-3, -4, 25), ivec3(3, -4, 25), ivec3(-4, -3, 25), ivec3(4, -3, 25), ivec3(-5, 0, 25), ivec3(5, 0, 25), ivec3(-4, 3, 25), ivec3(4, 3, 25), ivec3(-3, 4, 25), ivec3(3, 4, 25), ivec3(0, 5, 25), ivec3(-1, -5, 26), ivec3(1, -5, 26), ivec3(-5, -1, 26), ivec3(5, -1, 26), ivec3(-5, 1, 26), ivec3(5, 1, 26), ivec3(-1, 5, 26), ivec3(1, 5, 26), ivec3(-2, -5, 29), ivec3(2, -5, 29), ivec3(-5, -2, 29), ivec3(5, -2, 29), ivec3(-5, 2, 29), ivec3(5, 2, 29), ivec3(-2, 5, 29), ivec3(2, 5, 29), ivec3(-4, -4, 32), ivec3(4, -4, 32), ivec3(-4, 4, 32), ivec3(4, 4, 32), ivec3(-3, -5, 34), ivec3(3, -5, 34), ivec3(-5, -3, 34), ivec3(5, -3, 34), ivec3(-5, 3, 34), ivec3(5, 3, 34), ivec3(-3, 5, 34), ivec3(3, 5, 34), ivec3(0, -6, 36), ivec3(-6, 0, 36), ivec3(6, 0, 36), ivec3(0, 6, 36), ivec3(-1, -6, 37), ivec3(1, -6, 37), ivec3(-6, -1, 37), ivec3(6, -1, 37), ivec3(-6, 1, 37), ivec3(6, 1, 37), ivec3(-1, 6, 37), ivec3(1, 6, 37), ivec3(-2, -6, 40), ivec3(2, -6, 40), ivec3(-6, -2, 40), ivec3(6, -2, 40), ivec3(-6, 2, 40), ivec3(6, 2, 40), ivec3(-2, 6, 40), ivec3(2, 6, 40), ivec3(-4, -5, 41), ivec3(4, -5, 41), ivec3(-5, -4, 41), ivec3(5, -4, 41), ivec3(-5, 4, 41), ivec3(5, 4, 41), ivec3(-4, 5, 41), ivec3(4, 5, 41), ivec3(-3, -6, 45), ivec3(3, -6, 45), ivec3(-6, -3, 45), ivec3(6, -3, 45), ivec3(-6, 3, 45), ivec3(6, 3, 45), ivec3(-3, 6, 45), ivec3(3, 6, 45), ivec3(0, -7, 49), ivec3(-7, 0, 49), ivec3(7, 0, 49), ivec3(0, 7, 49), ivec3(-1, -7, 50), ivec3(1, -7, 50), ivec3(-5, -5, 50), ivec3(5, -5, 50), ivec3(-7, -1, 50), ivec3(7, -1, 50), ivec3(-7, 1, 50), ivec3(7, 1, 50), ivec3(-5, 5, 50), ivec3(5, 5, 50), ivec3(-1, 7, 50), ivec3(1, 7, 50), ivec3(-4, -6, 52), ivec3(4, -6, 52), ivec3(-6, -4, 52), ivec3(6, -4, 52), ivec3(-6, 4, 52), ivec3(6, 4, 52), ivec3(-4, 6, 52), ivec3(4, 6, 52), ivec3(-2, -7, 53), ivec3(2, -7, 53), ivec3(-7, -2, 53), ivec3(7, -2, 53), ivec3(-7, 2, 53), ivec3(7, 2, 53), ivec3(-2, 7, 53), ivec3(2, 7, 53), ivec3(-3, -7, 58), ivec3(3, -7, 58), ivec3(-7, -3, 58), ivec3(7, -3, 58), ivec3(-7, 3, 58), ivec3(7, 3, 58), ivec3(-3, 7, 58), ivec3(3, 7, 58), ivec3(-5, -6, 61), ivec3(5, -6, 61), ivec3(-6, -5, 61), ivec3(6, -5, 61), ivec3(-6, 5, 61), ivec3(6, 5, 61), ivec3(-5, 6, 61), ivec3(5, 6, 61), ivec3(0, -8, 64), ivec3(-8, 0, 64), ivec3(8, 0, 64), ivec3(0, 8, 64));

int decodeRadiusPx(vec4 seed) {
    return clamp(int(floor(seed.a * 8.0 + 0.5)), 1, MAX_RADIUS_PX);
}

bool seedCoversDistance(vec4 seed, int distSq) {
    int r = decodeRadiusPx(seed);
    return distSq <= (r * r);
}

void main() {
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    ivec2 size = textureSize(OutlineSampler, 0);

    if (pixel.x < 0 || pixel.y < 0 || pixel.x >= size.x || pixel.y >= size.y) {
        discard;
    }

    float currentAlpha = texelFetch(OutlineSampler, pixel, 0).a;
    if (currentAlpha > AlphaThreshold) {
        discard;
    }

    bool depthOcclusionEnabled = EnableDepthOcclusion > 0.5;
    float sceneDepth = 1.0;
    if (depthOcclusionEnabled) {
        sceneDepth = texelFetch(MainDepthSampler, pixel, 0).r;
    }

    int searchRadius = int(clamp(round(MaxSearchRadius), 1.0, float(MAX_RADIUS_PX)));
    int searchRadiusSq = searchRadius * searchRadius;

    int minDistanceSq = NO_DISTANCE;
    vec4 bestSeed = vec4(0.0);
    ivec2 bestSeedPixel = ivec2(-1, -1);

    if (UseFirstPersonHandFastPath > 0.5) {
        int i = 0;

        while (i < FAST_OFFSET_COUNT) {
            ivec3 firstEntry = FAST_OFFSETS[i];
            int distSq = firstEntry.z;

            if (distSq > searchRadiusSq) {
                break;
            }
            if (distSq > minDistanceSq) {
                break;
            }

            bool foundAtThisDistance = false;

            while (i < FAST_OFFSET_COUNT && FAST_OFFSETS[i].z == distSq) {
                if (!foundAtThisDistance) {
                    ivec2 samplePixel = pixel + FAST_OFFSETS[i].xy;

                    if (samplePixel.x >= 0 && samplePixel.y >= 0 &&
                    samplePixel.x < size.x && samplePixel.y < size.y) {

                        vec4 seed = texelFetch(OutlineSampler, samplePixel, 0);

                        if (seed.a > AlphaThreshold && seedCoversDistance(seed, distSq)) {
                            if (depthOcclusionEnabled) {
                                float seedDepth = texelFetch(OutlineDepthSampler, samplePixel, 0).r;
                                if (seedDepth > sceneDepth + DepthEpsilon) {
                                    i++;
                                    continue;
                                }
                            }
                            minDistanceSq = distSq;
                            bestSeed = seed;
                            bestSeedPixel = samplePixel;
                            foundAtThisDistance = true;
                        }
                    }
                }

                i++;
            }

            if (foundAtThisDistance) {
                break;
            }
        }
    } else {
        for (int y = -searchRadius; y <= searchRadius; y++) {
            int yy = y * y;

            if (yy >= minDistanceSq) {
                continue;
            }

            for (int x = -searchRadius; x <= searchRadius; x++) {
                if (x == 0 && y == 0) {
                    continue;
                }

                int distSq = x * x + yy;
                if (distSq >= minDistanceSq) {
                    continue;
                }

                ivec2 samplePixel = pixel + ivec2(x, y);
                if (samplePixel.x < 0 || samplePixel.y < 0 ||
                samplePixel.x >= size.x || samplePixel.y >= size.y) {
                    continue;
                }

                vec4 seed = texelFetch(OutlineSampler, samplePixel, 0);
                if (seed.a <= AlphaThreshold) {
                    continue;
                }

                if (!seedCoversDistance(seed, distSq)) {
                    continue;
                }

                if (depthOcclusionEnabled) {
                    float seedDepth = texelFetch(OutlineDepthSampler, samplePixel, 0).r;
                    if (seedDepth > sceneDepth + DepthEpsilon) {
                        continue;
                    }
                }

                minDistanceSq = distSq;
                bestSeed = seed;
                bestSeedPixel = samplePixel;
            }
        }
    }

    if (minDistanceSq == NO_DISTANCE) {
        discard;
    }

    if (depthOcclusionEnabled) {
        float bestSeedDepth = texelFetch(OutlineDepthSampler, bestSeedPixel, 0).r;

        if (bestSeedDepth > sceneDepth + DepthEpsilon) {
            discard;
        }
    }

    fragColor = vec4(bestSeed.rgb, 1.0);
}
