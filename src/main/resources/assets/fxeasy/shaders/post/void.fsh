#version 330

#moj_import <minecraft:globals.glsl>

#define PI 3.14159265359

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

in vec2 texCoord;
out vec4 fragColor;

void main() {
    float time = GameTime;
    float pulse = sin(PI * time);
    float glitchStrength = pulse * 0.015;
    float glitchOffset = sin(texCoord.y * 250.0 + time * 100.0) * cos(time * 30.0) * glitchStrength;
    vec2 glitchCoord = texCoord + vec2(glitchOffset, 0.0);

    vec4 baseColor = texture(InSampler, glitchCoord);
    float luminance = dot(baseColor.rgb, vec3(0.299, 0.587, 0.114));
    vec3 finalColor = vec3(luminance);

    vec3 bloomColor = vec3(0.0);
    vec2 pixelSize = 1.0 / InSize;
    float bloomIntensity = 1.8 * pulse;

    for (int x = -2; x <= 2; x++) {
        for (int y = -2; y <= 2; y++) {
            vec2 offset = vec2(float(x), float(y)) * pixelSize;
            vec4 sampleColor = texture(InSampler, glitchCoord + offset);
            float sampleLuminance = dot(sampleColor.rgb, vec3(0.299, 0.587, 0.114));
            if (sampleLuminance > 0.5) {
                bloomColor += sampleColor.rgb;
            }
        }
    }

    bloomColor /= 5.0;
    finalColor += bloomColor * bloomIntensity;
    fragColor = vec4(finalColor, baseColor.a);
}
