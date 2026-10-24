#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

in vec4 vertexColor;
in vec3 modelPosition;
in float sphericalVertexDistance;
in float cylindricalVertexDistance;

out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float fbm(vec2 p) {
    float value = 0.0;
    float amp = 0.5;
    mat2 rot = mat2(0.8, -0.6, 0.6, 0.8);
    for (int i = 0; i < 4; i++) {
        value += amp * noise(p);
        p = rot * p * 2.03 + vec2(1.7, 9.2);
        amp *= 0.5;
    }
    return value;
}

void main() {
    float time = GameTime * 1.4;
    vec2 xz = modelPosition.xz * 0.14;
    float radius = length(xz);
    float angle = atan(xz.y, xz.x);

    float spiral = angle * 3.0 - radius * 2.6 + time * 1.8;
    vec2 swirlUv = vec2(cos(spiral), sin(spiral)) * radius + xz * 1.6 + vec2(time * 0.35, -time * 0.28);

    float cloudBase = fbm(swirlUv);
    float cloudDetail = fbm(swirlUv * 2.4 - vec2(time * 0.9, time * 0.6));
    float armMask = 0.5 + 0.5 * sin(spiral + cloudBase * 3.2);
    float density = mix(cloudBase, cloudDetail, 0.45) * (0.65 + 0.35 * armMask);

    float eyeRim = exp(-pow((radius - 0.55) * 3.2, 2.0));
    float veinField = abs(cloudDetail - 0.5) * 2.0;
    float flash = pow(max(0.0, sin(time * 11.0 + radius * 4.0 + cloudBase * 6.0)), 6.0);
    float lightningVein = smoothstep(0.16, 0.0, veinField) * (0.35 + 0.65 * flash);

    vec3 shadowTint = vertexColor.rgb * (0.32 + 0.88 * density);
    vec3 rimColor = mix(vertexColor.rgb, vec3(0.95, 0.42, 1.0), 0.55) * eyeRim * 0.65;
    vec3 veinColor = vec3(0.78, 0.92, 1.0) * lightningVein * 1.35;

    vec4 color = vec4(shadowTint + rimColor + veinColor,
            clamp(vertexColor.a * (0.58 + 0.42 * density + lightningVein * 0.25), 0.0, 1.0));
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance,
            FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
