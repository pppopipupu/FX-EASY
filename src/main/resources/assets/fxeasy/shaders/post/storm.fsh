#version 330

#moj_import <minecraft:globals.glsl>

uniform sampler2D InSampler;

in vec2 texCoord;
out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

void main() {
    float time = GameTime * 1.8;
    vec2 centered = texCoord - vec2(0.5);
    float dist = length(centered);
    float edgeMask = smoothstep(0.18, 0.78, dist);

    float windWave = sin(dist * 18.0 - time * 6.5 + atan(centered.y, centered.x) * 3.0);
    vec2 ripple = normalize(centered + vec2(0.0001)) * windWave * 0.0045 * edgeMask;
    vec2 uv = clamp(texCoord + ripple, 0.002, 0.998);

    float flash = pow(max(0.0, sin(time * 3.4) * sin(time * 7.9 + 1.3)), 8.0);
    vec2 chromaOffset = centered * (0.006 + flash * 0.010) * edgeMask;

    float r = texture(InSampler, clamp(uv + chromaOffset, 0.002, 0.998)).r;
    float g = texture(InSampler, uv).g;
    float b = texture(InSampler, clamp(uv - chromaOffset, 0.002, 0.998)).b;
    vec3 color = vec3(r, g, b);

    vec2 rainUv = vec2(uv.x * 42.0 + uv.y * 14.0 - time * 2.2, uv.y * 9.0 + time * 11.0);
    float rainStreak = smoothstep(0.92, 0.99, sin(rainUv.x * 3.14159) * hash(floor(rainUv))) * edgeMask;
    color += vec3(0.48, 0.72, 1.0) * rainStreak * 0.14;

    float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
    vec3 bloom = max(vec3(0.0), color - vec3(0.58)) * (0.35 + flash * 0.85);
    color += bloom + vec3(0.55, 0.38, 0.88) * flash * 0.12;

    vec3 stormTint = mix(vec3(0.16, 0.03, 0.24), vec3(0.38, 0.04, 0.16), 0.5 + 0.5 * sin(time * 1.7));
    float vignette = smoothstep(0.32, 0.92, dist);
    color = mix(color, mix(color * 0.58, stormTint, 0.42), vignette * 0.65);

    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
