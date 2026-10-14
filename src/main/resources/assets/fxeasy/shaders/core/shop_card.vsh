#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec2 UV0;
in vec4 Color;

out vec2 texCoord0;
/// The vertex colour carries what the card says about itself rather than a tint, because the GUI blit path hands
/// a draw nothing else: `core/shop_card` reads the tier out of the red channel, the slice of the file the card
/// shows out of the green one, and how strongly the effect is drawn out of the blue one. The alpha is not read:
/// the illustration is drawn by the panel itself, and this pass only adds the effect over it.
out vec4 cardData;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    texCoord0 = UV0;
    cardData = Color;
}
