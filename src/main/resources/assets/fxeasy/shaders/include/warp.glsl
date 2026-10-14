#version 330

const float WARP_TAU = 6.28318530718;
/// Cycles per second, the same rate `FxPhoneRenderer` animates the displacement and the refraction with.
const float WARP_CYCLES = 0.8;

/// The rim's clock. The displacement and the refraction are computed by
/// `com.pppopipupu.fxeasy.client.FxPhoneRenderer` on a clock of their own; nothing else in the shaders depends on
/// this phase.
float warp_phase(float gameTime) {
    return gameTime * WARP_TAU * WARP_CYCLES;
}

/// Distance from the centre of the quad in its own 0..1 coordinate, which is where the rim fades in. The renderer
/// hands that coordinate to the vertex shader through the overhead layer.
float warp_radius(vec2 position) {
    return length(position - vec2(0.5));
}
