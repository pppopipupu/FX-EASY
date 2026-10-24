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

float valueNoise(vec2 p) {
    vec2 cell = floor(p);
    vec2 local = fract(p);
    vec2 blend = local * local * (3.0 - 2.0 * local);
    float a = hash(cell);
    float b = hash(cell + vec2(1.0, 0.0));
    float c = hash(cell + vec2(0.0, 1.0));
    float d = hash(cell + vec2(1.0, 1.0));
    return mix(mix(a, b, blend.x), mix(c, d, blend.x), blend.y);
}

float fbm(vec2 p) {
    float sum = 0.0;
    float amplitude = 0.5;
    for (int octave = 0; octave < 4; octave++) {
        sum += valueNoise(p) * amplitude;
        p *= 2.03;
        amplitude *= 0.5;
    }
    return sum;
}

void main() {
    vec3 n = normalize(modelPosition);
    vec3 viewPos = (ModelViewMat * vec4(modelPosition, 1.0)).xyz;
    vec3 vn = normalize(mat3(ModelViewMat) * n);
    float fresnel = pow(1.0 - abs(dot(vn, normalize(-viewPos))), 2.6);

    vec2 swirlUv = vec2(atan(n.z, n.x) * 1.4 + GameTime * 0.6, n.y * 3.0 - GameTime * 1.1);
    float swirl = fbm(swirlUv);
    float streak = smoothstep(0.62, 0.98, swirl);
    float horizon = smoothstep(0.25, 0.95, fresnel);
    vec3 rim = mix(vec3(0.55, 0.18, 1.0), vec3(0.95, 0.85, 1.0), streak);

    vec4 color = vec4(vertexColor.rgb * 0.06 + rim * horizon * (0.9 + 0.6 * streak),
            clamp(vertexColor.a * (0.55 + 0.45 * horizon + 0.25 * streak), 0.0, 1.0));
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance,
            FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
