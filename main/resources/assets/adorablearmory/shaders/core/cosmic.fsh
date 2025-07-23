#version 150

float fog_distance(mat4 modelViewMat, vec3 pos, int shape) {
    if (shape == 0) {
        return length((modelViewMat * vec4(pos, 1.0)).xyz);
    } else {
        float distXZ = length((modelViewMat * vec4(pos.x, 0.0, pos.z, 1.0)).xyz);
        float distY = length((modelViewMat * vec4(0.0, pos.y, 0.0, 1.0)).xyz);
        return max(distXZ, distY);
    }
}

mat4 rotationMatrix(vec3 axis, float angle) {
    axis = normalize(axis);
    float s = sin(angle);
    float c = cos(angle);
    float oc = 1.0 - c;

    return mat4(oc * axis.x * axis.x + c, oc * axis.x * axis.y - axis.z * s, oc * axis.z * axis.x + axis.y * s, 0.0,
                oc * axis.x * axis.y + axis.z * s,  oc * axis.y * axis.y + c, oc * axis.y * axis.z - axis.x * s, 0.0,
                oc * axis.z * axis.x - axis.y * s,  oc * axis.y * axis.z + axis.x * s, oc * axis.z * axis.z + c, 0.0,
                0.0, 0.0, 0.0, 1.0);
}
// HSV->RGB
vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0/3.0, 1.0/3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

vec4 linear_fog(vec4 inColor, float vertexDistance, float fogStart, float fogEnd, vec4 fogColor) {
    if (vertexDistance <= fogStart) {
        return inColor;
    }
    float fogValue = vertexDistance < fogEnd ? smoothstep(fogStart, fogEnd, vertexDistance) : 1.0;
    return vec4(mix(inColor.rgb, fogColor.rgb, fogValue * fogColor.a), inColor.a);
}

#define M_PI 3.1415926535897932384626433832795
const int cosmiccount = 10;
const int cosmicoutof = 50;
const float lightmix = 0.20f;

// Uniforms: CPU传入的变量
uniform sampler2D Sampler0; // 主纹理，包含cosmic符号图集和遮罩

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

uniform float time; // 游戏时间
uniform float yaw; // 玩家水平朝向
uniform float pitch; // 玩家垂直朝向
uniform float externalScale; // 外部缩放因子
uniform float opacity; // 透明度

// 存储cosmic符号UV坐标的数组
uniform mat2 cosmicuvs[cosmiccount];

// In: 顶点着色器传入变量
in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in vec4 normal;
in vec3 fPos; // 片元在视图空间中的位置

// Out: 输出到屏幕的最终颜色
out vec4 fragColor;

void main (void) {

    vec4 mask = texture(Sampler0, texCoord0.xy);
    if (mask.a < 0.0001) discard;

    float oneOverExternalScale = 1.0 / externalScale;
    int uvtiles = 16;

    // 1. 设置动态背景颜色
    vec4 col = vec4(0.01, 0.005, 0.02, 0.9);
    float pulse = mod(time, 400.0) / 400.0;
    col.g = sin(pulse * M_PI * 2.0) * 0.075 + 0.225;
    col.b = cos(pulse * M_PI * 2.0) * 0.05 + 0.3;

    // 2. 计算从摄像机到当前片元的观察方向向量
    vec4 dir = normalize(vec4(-fPos, 0.0));

    // 3. 根据玩家的朝向(yaw, pitch)旋转方向向量，实现天空盒技术。
    float sb = sin(pitch);
    float cb = cos(pitch);
    dir = normalize(vec4(dir.x, dir.y * cb - dir.z * sb, dir.y * sb + dir.z * cb, 0.0));

    float sa = sin(-yaw);
    float ca = cos(-yaw);
    dir = normalize(vec4(dir.z * sa + dir.x * ca, dir.y, dir.z * ca - dir.x * sa, 0.0));

    vec4 ray;

    // 4. 多层视差循环
    for (int i = 0; i < 16; i++) {
        int mult = 16 - i;

        // 生成伪随机数，用于旋转和颜色
        int j = i + 7;
        float rand1 = float((j * j * 4321 + j * 8) * 2);
        int k = j + 1;
        float rand2 = float((k * k * k * 239 + k * 37) * 3);
        float rand3 = rand1 * 347.4 + rand2 * 63.4;

        // 使用随机数创建旋转矩阵，并应用到方向向量上
        vec3 axis = normalize(vec3(sin(rand1), sin(rand2) , cos(rand3)));
        ray = dir * rotationMatrix(axis, mod(rand3, 2.0 * M_PI));

        // 将方向向量转换为球形UV坐标
        float rawu = 0.5 + (atan(ray.z, ray.x) / (2.0 * M_PI));
        float rawv = 0.5 + (asin(ray.y) / M_PI);

        // 根据层数和时间对UV进行缩放和偏移，制造视差滚动的效果
        float scale = float(mult) * 0.5 + 1.8264;
        float u = rawu * scale * externalScale;
        float v = (rawv + time * 0.0003420 * oneOverExternalScale) * scale * 0.50 * externalScale;

        vec2 tex = vec2(u, v);

        // 计算当前UV所在的瓦片(tile)位置
        int tu = int(mod(floor(u * float(uvtiles)), float(uvtiles)));
        int tv = int(mod(floor(v * float(uvtiles)), float(uvtiles)));

        // 使用瓦片位置生成伪随机的符号、旋转和翻转
        int position = ((1777541 * tu) + (7649689 * tv) + (361273 * (i+31)) + 1723609 ) ^ 50943779;
        int symbol = int(mod(float(position), float(cosmicoutof)));
        int rotation = int(mod(pow(float(tu), float(tv)) + float(tu) + 3.0 + float(tv*i), 8.0));
        bool flip = false;
        if (rotation >= 4) {
            rotation -= 4;
            flip = true;
        }

        // 如果随机结果是一个cosmic符号，则进行绘制
        if (symbol >= 0 && symbol < cosmiccount) {
            // 获取瓦片的局部UV坐标(0.0 to 1.0)
            float ru = clamp(mod(u, 1.0) * float(uvtiles) - float(tu), 0.0, 1.0);
            float rv = clamp(mod(v, 1.0) * float(uvtiles) - float(tv), 0.0, 1.0);
            ru = (ru - 0.5) / 1.0 + 0.5;
            rv = (rv - 0.5) / 1.0 + 0.5;
            if (flip) ru = 1.0 - ru;

            // 在[0,1]范围
            float oru = ru;
            float orv = rv;

            // 对局部UV进行旋转
            if (rotation == 1) { oru = 1.0 - rv; orv = ru; }
            else if (rotation == 2) { oru = 1.0 - ru; orv = 1.0 - rv; }
            else if (rotation == 3) { oru = rv; orv = 1.0 - ru; }

            // 从uniform数组中获取该符号的UV边界
            float umin = cosmicuvs[symbol][0][0];
            float umax = cosmicuvs[symbol][1][0];
            float vmin = cosmicuvs[symbol][0][1];
            float vmax = cosmicuvs[symbol][1][1];

            // 使用局部UV和边界，插值计算出在主纹理图集上的最终采样坐标
            vec2 cosmictex;
            cosmictex.x = umin * (1.0 - oru) + umax * oru;
            cosmictex.y = vmin * (1.0 - orv) + vmax * orv;

            // 采样 - 获取符号的纹理颜色
            vec4 tcol = texture(Sampler0, cosmictex);

            // 计算Alpha值，使其在两极平滑淡出
            float a = tcol.r * (0.5 + (1.0 / float(mult)) * 1.0) * (1.0 - smoothstep(0.20, 0.48, abs(rawv - 0.5)));

            // 彩虹渐变参数
            float hueSpeed = 0.00246;  // 色相变化速度
            float hueSpread = 0.0924; // 层间色相差值
            float saturation = 0.32; // 饱和度
            float brightness = 1; // 亮度

            // 计算彩虹色相
            float hue = mod(time * hueSpeed + i * hueSpread, 1);
            vec3 rainbowColor = hsv2rgb(vec3(hue, saturation, brightness));
            // 应用透明度并叠加到背景
            col = col + vec4(rainbowColor, 1.0) * a;
        }
    }

    vec3 shade = vertexColor.rgb * (lightmix) + vec3(1.0 - lightmix);
    col.rgb *= shade;

    // 应用遮罩和透明度
    col.a *= mask.r * opacity;

    // 将颜色限制在0-1范围内，并应用雾效
    col = clamp(col, 0.0, 1.0);
    fragColor = linear_fog(col * ColorModulator, vertexDistance, FogStart, FogEnd, FogColor);
}