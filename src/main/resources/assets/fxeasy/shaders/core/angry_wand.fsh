#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

in vec4 vertexColor;
in vec3 modelPosition;
in float sphericalVertexDistance;
in float cylindricalVertexDistance;

out vec4 fragColor;

const vec3 ARCANE = vec3(0.20, 0.82, 1.0);
const vec3 EMBER = vec3(1.0, 0.16, 0.04);

void main() {
    float pulse = 0.5 + 0.5 * sin(GameTime * 3.2);
    float flow = fract(modelPosition.y * 18.0 - GameTime * 0.45);
    float band = smoothstep(0.38, 0.5, flow) * (1.0 - smoothstep(0.5, 0.62, flow));
    float edge = smoothstep(0.46, 0.5, abs(fract(modelPosition.y * 9.0 + GameTime * 0.22) - 0.5));
    vec3 glow = mix(EMBER, ARCANE, 0.5 + 0.5 * sin(GameTime * 2.1 + modelPosition.y * 14.0));
    vec4 color = vertexColor;
    color.rgb += glow * (band * 0.65 + edge * 0.18) * (0.65 + 0.35 * pulse);
    color.a = clamp(color.a + band * 0.12, 0.0, 1.0);
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance,
            FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
