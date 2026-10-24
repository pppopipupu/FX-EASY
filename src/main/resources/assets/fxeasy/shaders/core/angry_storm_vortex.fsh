#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

in vec4 vertexColor;
in vec3 modelPosition;
in float sphericalVertexDistance;
in float cylindricalVertexDistance;

out vec4 fragColor;

void main() {
    float time = GameTime * 2.6;
    float radialAngle = atan(modelPosition.z, modelPosition.x);
    float helix = sin(modelPosition.y * 5.5 + radialAngle * 4.0 - time * 3.8);
    float plasmaBand = smoothstep(-0.2, 0.85, helix);

    float streakFlow = fract(modelPosition.y * 2.8 - time * 1.65 + sin(radialAngle * 3.0) * 0.25);
    float streak = smoothstep(0.18, 0.5, streakFlow) * (1.0 - smoothstep(0.5, 0.82, streakFlow));

    float crackle = 0.72 + 0.28 * sin(time * 24.0 + modelPosition.y * 19.0 + radialAngle * 7.0);
    float coreHot = smoothstep(0.75, 1.0, plasmaBand * crackle);

    vec3 plasmaGlow = mix(vertexColor.rgb, vec3(0.88, 0.96, 1.0), coreHot * 0.62 + streak * 0.28);
    float alphaPulse = 0.76 + 0.24 * sin(time * 4.2 + modelPosition.y * 3.1);

    vec4 color = vec4(plasmaGlow * (0.78 + 0.42 * plasmaBand + 0.30 * streak) * crackle,
            clamp(vertexColor.a * alphaPulse * (0.70 + 0.30 * plasmaBand), 0.0, 1.0));
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance,
            FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
