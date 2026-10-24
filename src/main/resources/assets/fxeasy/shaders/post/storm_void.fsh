#version 330

#moj_import <minecraft:globals.glsl>

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

in vec2 texCoord;
out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

void main() {
    float time = GameTime * 1.4;
    vec2 centered = texCoord - vec2(0.5);
    float dist = length(centered);
    vec2 dir = centered / max(dist, 0.0001);
    float pulse = 0.5 + 0.5 * sin(time * 1.3);

    float well = smoothstep(0.15, 0.75, dist) * 0.012;
    vec2 texel = 0.5 / max(InSize, vec2(1.0));
    vec2 uv = clamp(texCoord - dir * well * (1.0 + 0.4 * pulse), texel, vec2(1.0) - texel);

    vec3 color = texture(InSampler, uv).rgb;

    float aspect = OutSize.x / max(OutSize.y, 1.0);
    float angle = atan(centered.y, centered.x);
    vec2 debrisUv = vec2(angle * 7.324 * aspect + dist * 4.0, dist * 9.0 + time * 0.85);
    float debrisNoise = hash(floor(debrisUv));
    float debrisReach = smoothstep(0.04, 0.30, dist) * (1.0 - smoothstep(0.42, 0.74, dist));
    float debris = smoothstep(0.70, 0.99, debrisNoise) * debrisReach;
    color += mix(vec3(0.52, 0.20, 1.0), vec3(0.92, 0.88, 1.0), debrisNoise) * debris * 0.42;

    float hole = 1.0 - smoothstep(0.015, 0.15, dist);
    color *= 1.0 - hole * 0.92;
    color += vec3(0.45, 0.16, 1.0) * (1.0 - smoothstep(0.10, 0.28, dist)) * (1.0 - hole) * (0.16 + 0.12 * pulse);

    float crush = smoothstep(0.32, 0.92, dist);
    float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
    color = mix(color, vec3(luma * 0.42), crush * 0.62);
    color = mix(color, vec3(0.035, 0.0, 0.07), crush * 0.45);
    color *= 1.0 + 0.06 * sin(time * 1.1);

    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
