#version 330

#moj_import <minecraft:light.glsl>
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:sample_lightmap.glsl>
#moj_import <fxeasy:warp.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
/// `VertexConsumer.addVertex` bakes the pose into `Position`, so a shader only sees world or GUI units and can
/// never recover the quad's own grid from it. The renderer therefore writes that 0..1 coordinate into the
/// overhead layer, which this item does not use, at 8 bits per axis; the rim is its only consumer.
in ivec2 UV1;
in vec3 Normal;
in ivec2 UV2;

uniform sampler2D Sampler2;

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;
out float warpRadius;

void main() {
    warpRadius = warp_radius(vec2(UV1) * (1.0 / 255.0));

    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);

    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, Normal, Color) * sample_lightmap(Sampler2, UV2);

    texCoord0 = UV0;
}
