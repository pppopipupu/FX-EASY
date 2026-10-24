#version 330

#moj_import <minecraft:globals.glsl>

#define PI 3.14159265359

uniform sampler2D InSampler;

in vec2 texCoord;
out vec4 fragColor;

vec4 getColor(vec2 uv, float time) {
    float waveTime = sin(PI * time) * 1.5;
    float waveOffsetY = sin(uv.x * 10.0 + waveTime * 10.0) * 0.08;
    uv.y += waveOffsetY;

    vec2 colorShiftOffset = vec2(sin(waveTime * 2.8), cos(waveTime * 4.0)) * 0.010;
    float glitchTime = waveTime * 20.0;
    float glitchLine = floor(uv.y * 30.0);
    float glitchValue = sin(glitchTime + glitchLine * 10.0);
    float glitchTrigger = step(0.9, fract(glitchTime * 0.1));
    float glitchOffsetX = (glitchValue - 0.5) * 0.08 * glitchTrigger;

    float r = texture(InSampler, uv + colorShiftOffset + vec2(glitchOffsetX, 0.0)).r;
    float g = texture(InSampler, uv).g;
    float b = texture(InSampler, uv - colorShiftOffset - vec2(glitchOffsetX, 0.0)).b;
    float a = texture(InSampler, uv).a;
    return vec4(r, g, b, a);
}

void main() {
    float time = GameTime;
    float pulse = sin(PI * time);
    vec4 baseColor = getColor(texCoord, time);

    float bloomIntensity = 5.0 - pulse * 4.0;
    float bloomThreshold = 0.65 - pulse / 4.0;
    vec4 bloomColor = vec4(0.0);

    vec2 offsets[4];
    offsets[0] = vec2(1.0, 1.0);
    offsets[1] = vec2(-1.0, 1.0);
    offsets[2] = vec2(1.0, -1.0);
    offsets[3] = vec2(-1.0, -1.0);

    for (int i = 0; i < 4; i++) {
        vec4 sampleColor = getColor(texCoord + normalize(offsets[i]) * 0.05, time);
        float brightness = dot(sampleColor.rgb, vec3(0.2126, 0.7152, 0.0722));
        if (brightness > bloomThreshold) {
            bloomColor += sampleColor;
        }
    }

    float baseBrightness = dot(baseColor.rgb, vec3(0.2126, 0.7152, 0.0722));
    if (baseBrightness > bloomThreshold) {
        bloomColor += baseColor;
    }
    bloomColor /= 5.0;

    fragColor = baseColor + bloomColor * bloomIntensity;
}
