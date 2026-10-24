#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

in vec4 vertexColor;
in float sphericalVertexDistance;
in float cylindricalVertexDistance;

out vec4 fragColor;

void main() {
    float flicker = 0.70 + 0.30 * sin(GameTime * 84.0 + gl_FragCoord.x * 0.31 + gl_FragCoord.y * 0.17);
    float flicker2 = 0.86 + 0.14 * sin(GameTime * 31.0 - gl_FragCoord.y * 0.23);
    vec4 color = vertexColor;
    color.rgb *= flicker * flicker2;
    color.a *= 0.82 + 0.18 * sin(GameTime * 47.0 + gl_FragCoord.x * 0.11);
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance,
            FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
