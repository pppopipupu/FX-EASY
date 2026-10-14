#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 cardData;

out vec4 fragColor;

const float TAU = 6.28318530718;
/// `GameTime` is where the clock of the world stands in a 24000 tick game day rather than a count of seconds,
/// which is 1200 seconds at twenty ticks per second, so the animation of a card is timed in seconds through
/// this factor. It stands still while the world does, which is the clock a card is allowed to keep.
const float SECONDS_PER_DAY = 1200.0;
/// The tier a card sells, in the order the shop lays its cards out: the red channel of the vertex colour
/// carries this index, the same one `FxShopCard` packs into it.
const float TIER_GO = 0.0;
const float TIER_PLUS = 1.0;
const float TIER_PRO = 2.0;
/// Every amplitude below is the light the effect adds to the card at full strength, as a fraction of white. This
/// pass is additive, so a figure of 0.18 lifts whatever stands under it by that much: how transparent the
/// illustration reads is decided where it is drawn and not here, so the two are tuned apart. The three tiers
/// carry a sweep each - warm over Go, iridescent over Plus, holographic over Pro - and, under it, the figure of
/// the tier: a vortex turning over Go, flames rising over Plus and thunder striking over Pro.

/// A band of light travelling along [axis]: [progress] is where its centre stands, [width] how far it reaches
/// before it fades out, and the falloff is smooth rather than a hard edge, so the band reads as a sheen.
float sweep(float axis, float progress, float width) {
    float distance = (axis - progress) / width;
    return exp(-distance * distance);
}

/// An iridescent colour of the hue [phase]: three channels a third of a cycle apart, which is the palette that
/// cycles through warm, green and blue as the phase runs.
vec3 iridescence(float phase) {
    return 0.5 + 0.5 * cos(TAU * (vec3(0.0, 0.33, 0.67) + phase));
}

/// The iridescence of [phase] as light rather than as paint: a sheen travels over the picture, so the band is
/// mostly white with the hue riding on it, which is what keeps a coloured sweep from reading as a wash of colour
/// over the illustration under it.
vec3 iridescent_light(float phase) {
    return mix(vec3(1.0), iridescence(phase), 0.55);
}

/// A hash of [value] into [0,1), which is what the noise below is built from. The clock of a card runs inside
/// one game day, so the argument stays small and the hash stays stable over a session.
float hash(float value) {
    return fract(sin(value * 127.1) * 43758.5453);
}

/// Value noise along one axis: the hash of the cell [value] stands in, smoothed into the hash of the next, so a
/// figure driven by it wanders in waves instead of stepping from one hash to the next.
float noise(float value) {
    float cell = floor(value);
    float part = fract(value);
    return mix(hash(cell), hash(cell + 1.0), part * part * (3.0 - 2.0 * part));
}

/// The vortex of a Go card: arms of warm light winding around a point a little above the middle of the card. The
/// angle of a texel decides which arm it stands in and the logarithm of its radius how far the arm has wound by
/// the time it reaches there, which is what turns the arms into a spiral rather than a set of rays; the angle is
/// taken in whole turns of two, so the seam where the angle of a texel wraps cancels itself out.
float vortex(vec2 card, float time) {
    vec2 centred = card - vec2(0.5, 0.45);
    float radius = length(centred);
    float angle = atan(centred.y, centred.x);
    float spiral = sin(angle * 2.0 + log(max(radius, 0.06)) * 7.0 - time * 1.3);
    return smoothstep(0.1, 1.0, spiral) * smoothstep(0.03, 0.16, radius)
            * (1.0 - smoothstep(0.35, 0.85, radius));
}

/// The flames of a Plus card: tongues of fire standing along the foot of the card. A column carries a tongue
/// where the noise along it stands high, and how high that noise stands is how far the tongue reaches, so the
/// fire licks up the card and flickers along the foot instead of sliding over it. The two figures are the height
/// of a texel inside its tongue, which is what the colour of the fire follows, and how much light stands there.
vec2 flames(vec2 card, float time) {
    float column = card.x * 9.0 + time * 0.7;
    float wave = noise(column) * 0.6 + noise(column * 2.3 - time * 1.3) * 0.4;
    float reach = 0.12 + smoothstep(0.35, 0.85, wave) * 0.62;
    float fromFoot = 1.0 - card.y;
    float body = 1.0 - fromFoot / max(reach, 0.05);
    float light = smoothstep(0.05, 0.9, body) * smoothstep(0.30, 0.72, wave);
    return vec2(clamp(body, 0.0, 1.0), light);
}

/// The thunder of a Pro card: a bolt striking on a clock of its own, as the light of its core, the light of the
/// halo it throws and the flash that stands while it lasts. The path of the bolt wanders with the number of the
/// strike, so no two strikes are the same shape, and both figures fall off with the distance from that path.
vec3 thunder(vec2 card, float time) {
    float phase = fract(time * 0.19);
    float strike = smoothstep(0.0, 0.02, phase) * (1.0 - smoothstep(0.05, 0.32, phase));
    float seed = floor(time * 0.19);
    float path = 0.5 + (noise(card.y * 5.0 + seed * 17.0) - 0.5) * 0.62
            + (noise(card.y * 21.0 + seed * 31.0) - 0.5) * 0.16;
    float distance = card.x - path;
    return strike * vec3(exp(-distance * distance * 520.0), exp(-distance * distance * 34.0), 1.0);
}

/// The effect of one card, laid over the picture of that card rather than drawn with it: the panel blits the
/// illustration the way it blits any texture, and this pass then adds the sheen of the tier the card sells on
/// top of it. It covers the whole card, so a texel's coordinate is already the coordinate of the card, and the
/// card says which effect in the channels of the vertex colour.
void main() {
    /// Where the illustration stands in the file is what the effect masks itself with: the pass samples the slice
    /// of the file the card is cropped to, so a card whose picture does not reach its edges is lit over its
    /// figure rather than over the whole rectangle.
    vec4 art = texture(Sampler0, vec2(texCoord0.x, texCoord0.y * max(cardData.g, 0.001)));

    float tier = floor(cardData.r * 255.0 + 0.5);
    float intensity = cardData.b;
    float time = GameTime * SECONDS_PER_DAY;
    vec2 card = texCoord0;

    vec3 glow = vec3(0.0);
    if (tier == TIER_GO) {
        float axis = card.x * 0.65 + card.y * 0.35;
        float progress = fract(time * 0.055) * 1.5 - 0.25;
        glow = vec3(1.0, 0.94, 0.86) * sweep(axis, progress, 0.16) * 0.18;
        glow += vec3(1.0, 0.88, 0.70) * vortex(card, time) * 0.13;
    } else if (tier == TIER_PLUS) {
        float axis = card.x * 0.7 + card.y * 0.3;
        float progress = fract(time * 0.10) * 1.5 - 0.25;
        glow = iridescent_light(axis * 0.5 + time * 0.10) * sweep(axis, progress, 0.11) * 0.16;
        glow += iridescent_light(-time * 0.04) * 0.025;
        vec2 fire = flames(card, time);
        glow += mix(vec3(1.0, 0.28, 0.04), vec3(1.0, 0.90, 0.52), fire.x) * fire.y * 0.22;
    } else if (tier == TIER_PRO) {
        float axis = card.x * 0.6 + card.y * 0.4;
        float progress = fract(time * 0.16) * 1.5 - 0.25;
        vec3 hologram = iridescent_light(axis + time * 0.25);
        glow = hologram * sweep(axis, progress, 0.09) * 0.15;
        glow += hologram * sweep(1.0 - axis, fract(progress + 0.5), 0.09) * 0.15;
        glow += vec3(0.35, 0.75, 1.0) * (0.5 + 0.5 * sin(card.y * 160.0 - time * 4.0)) * 0.03;
        glow += vec3(0.45, 0.85, 1.0) * (1.0 - smoothstep(0.0, 0.5, card.y)) * 0.06;
        vec3 strike = thunder(card, time);
        glow += vec3(0.88, 0.95, 1.0) * strike.x * 0.42;
        glow += vec3(0.55, 0.75, 1.0) * strike.y * 0.12;
        glow += vec3(0.72, 0.86, 1.0) * strike.z * 0.05;
    }

    /// The alpha of the target is written as nothing: the pass only adds light, and what stands under it decides
    /// its own transparency.
    fragColor = vec4(glow * intensity * art.a, 0.0) * ColorModulator;
}
