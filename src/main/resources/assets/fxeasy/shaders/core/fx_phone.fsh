#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <fxeasy:warp.glsl>

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in float warpRadius;

out vec4 fragColor;

const vec3 RIM_COOL = vec3(0.25, 0.85, 1.0);
const vec3 RIM_WARM = vec3(0.62, 0.36, 1.0);

void main() {
    vec4 color = texture(Sampler0, texCoord0);
#ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
#endif

    color *= vertexColor * ColorModulator;

    float phase = warp_phase(GameTime);
    float rim = smoothstep(0.42, 0.70, warpRadius);
    vec3 rimColor = mix(RIM_COOL, RIM_WARM, 0.5 + 0.5 * sin(phase));
    color.rgb += rimColor * rim * (0.14 + 0.06 * sin(phase * 2.0));

    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
