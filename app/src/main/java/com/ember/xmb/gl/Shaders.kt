package com.ember.xmb.gl

/**
 * The XMB background, as two small programs rather than one big one.
 *
 * ### Why the work is split the way it is
 *
 * The first version evaluated everything per pixel, which forced the whole thing to be
 * rendered at a fraction of native resolution and stretched back up. That was the wrong
 * trade, because almost none of the expensive work actually varies per pixel:
 *
 *  - The six `hsl2rgb` conversions depend only on the hue and the time-of-day shade.
 *    They are **constant for the entire frame**, so they now happen once on the CPU and
 *    arrive as uniforms.
 *  - The nine `sin` calls that shape the ribbon depend only on x. They are **constant
 *    down each column**, so they now happen in [BAND_VERTEX] over a strip of vertical
 *    slices, and the rasteriser interpolates the result. A sine whose period is ~1200px,
 *    sampled every ~5px and interpolated linearly, is off by well under a hundredth of a
 *    pixel — and on a Utgard GPU the vertex unit was sitting idle anyway.
 *
 * What is left per pixel is one smoothstep and two exps per band, over only the sliver
 * of screen that band covers. That is cheap enough to run at full native resolution,
 * which is the entire point: there is no offscreen buffer and no upscale any more.
 *
 * ### Why there is no `uTime`, and no large number anywhere in a fragment shader
 *
 * Mali-400/450 has no `highp` in fragment shaders at all, so every fragment value is
 * fp16. The absolute spacing of fp16 grows with magnitude: around 1000 it is 0.5, which
 * is why the old `d = abs(y - cy)` — a difference of two ~1000-magnitude numbers —
 * quantised every ribbon edge to half-pixel steps no matter how small the difference
 * came out. Subtracting first, in the vertex shader's `highp`, and passing the small
 * result is what fixes it: near the edges `d` is on the order of the band thickness, and
 * fp16 resolves that to a thousandth of a pixel.
 *
 * `uTime` was an earlier instance of the same mistake. The CPU wraps each band's phase
 * into [0, 2pi) in double precision and uploads that, so the shader only ever adds small
 * numbers.
 *
 * Shimmer was removed outright: three layers x nine cells of hashing per pixel was the
 * dominant cost in the frame, and on a real panel it read as a static dot grid.
 */
internal object Shaders {

    // ---------------------------------------------------------------- backdrop

    const val BACKDROP_VERTEX = """
attribute vec2 aPos;
varying vec2 vUV;
void main() {
    // y flipped so that vUV.y = 0 is the top of the screen, matching the layout.
    vUV = vec2(aPos.x * 0.5 + 0.5, 0.5 - aPos.y * 0.5);
    gl_Position = vec4(aPos, 0.0, 1.0);
}
"""

    /**
     * Vertical gradient plus the warm floor glow — and the dither, which is the only
     * place it belongs.
     *
     * The dither used to be applied inside the low-resolution pass, where the bilinear
     * upscale promptly averaged it away; it was doing nothing. It also used a hash that
     * multiplied the fragment coordinate by ~123, which overflows the fp16 ceiling of
     * 65504 past about x=530, so on anything but the smallest render target it was
     * feeding `fract()` an infinity.
     *
     * A 4x4 ordered (Bayer) pattern avoids both problems. Every intermediate is under
     * 16, and it reads as a clean texture rather than grain. The one assumption is that
     * `mod(gl_FragCoord.xy, 4.0)` is exact, which holds while the surface is under
     * 2048px, since fp16 represents integers exactly only to there. Both target panels
     * are 720p, so there is plenty of headroom.
     */
    const val BACKDROP_FRAGMENT = """
precision mediump float;

uniform vec3 uBg0;   // shadow, hue - 28
uniform vec3 uBg1;   // core, hue
uniform vec3 uTint;  // highlight, hue + 22

varying vec2 vUV;

// 2x2 base matrix [[0,2],[3,1]], written as arithmetic because GLSL ES 2.0 will not let
// a fragment shader index an array by a computed value.
float bayer2(float i, float j) {
    return i * 3.0 + j * 2.0 - i * j * 4.0;
}

// Recursive construction: M4[y][x] = 4 * M2[y&1][x&1] + M2[y>>1][x>>1].
// The +0.5 centres the result on 0.5 so the dither adds no DC shift.
float bayer4(vec2 c) {
    vec2 p = mod(c, 4.0);
    float x0 = mod(p.x, 2.0);
    float y0 = mod(p.y, 2.0);
    float x1 = floor(p.x * 0.5);
    float y1 = floor(p.y * 0.5);
    return (4.0 * bayer2(y0, x0) + bayer2(y1, x1) + 0.5) * (1.0 / 16.0);
}

void main() {
    float vy = vUV.y;

    vec3 col = mix(uBg0, uBg1, smoothstep(0.0, 0.55, vy));
    col      = mix(col, uBg0, smoothstep(0.55, 1.0, vy));

    // warm floor glow low on the screen
    vec2 g = vec2((vUV.x - 0.5) / 0.70, (vy - 1.12) / 0.48);
    col += uTint * exp(-dot(g, g)) * 0.95;

    // Half an LSB, which is what it takes to dissolve the contour steps an 8-bit
    // gradient shows across a lightness ramp this shallow.
    col += (bayer4(gl_FragCoord.xy) - 0.5) * (1.0 / 255.0);

    gl_FragColor = vec4(col, 1.0);
}
"""

    // -------------------------------------------------------------------- band

    /**
     * One ribbon band, drawn as a strip of vertical slices that **follows its own
     * centreline**.
     *
     * Two things fall out of letting the geometry track the band rather than covering
     * the screen. The fragment shader only runs on the pixels the ribbon can actually
     * reach, which is roughly a sixth of the screen per band; and `vBand.x` — the
     * distance from the centreline — spans only the band's own envelope, so it stays
     * small enough for fp16 to resolve finely. A full-screen quad would have made that
     * varying span the whole height again, which is the bug this is avoiding.
     *
     * Within a slice the rasteriser interpolates both the vertex position and the
     * varying linearly, so the difference of the two is exactly `y - cy_linear(x)`. No
     * seam appears at the triangle split, because that expression is planar across the
     * quad.
     *
     * Vertex shaders are `highp` by default in GLSL ES 2.0 even where fragment shaders
     * have no `highp` at all, so the large values — the axis, the centreline, the
     * viewport height — are safe here and nowhere else.
     */
    const val BAND_VERTEX = """
attribute vec2 aPos;   // x: 0..1 across the screen; y: -1 above the band, +1 below

uniform vec2  uRes;    // viewport size in pixels
uniform float uAxis;   // category axis, as a FRACTION of height
uniform vec4  uGeom;   // offset, amplitude, half-thickness, frequency (pixel units)
uniform vec3  uPhase;  // travel, counter-travel, twist - pre-wrapped on the CPU

varying vec4 vBand;    // distance from centreline, half-thickness, facing, softness

void main() {
    float x  = aPos.x * uRes.x;
    float fr = uGeom.w;

    // A second, slower oscillator drives the sheet's half-thickness, so it narrows
    // almost to a line where it turns edge-on and swells where it faces us. That twist
    // is what makes it read as a surface rather than a stripe.
    float cy = uAxis * uRes.y + uGeom.x
             + sin(x * fr + uPhase.x) * uGeom.y
             + sin(x * fr * 0.47 - uPhase.y) * uGeom.y * 0.55;

    float tw = abs(sin(x * fr * 0.5 + uPhase.z));
    float ht = uGeom.z * (0.16 + 0.84 * tw);
    float w  = ht * 0.24 + 1.0;

    // exp(-4.5 * 4.5) is about 1e-9, so the envelope clips nothing visible.
    float d = aPos.y * (ht + 4.5 * w);

    vBand = vec4(d, ht, 0.22 + 0.78 * tw, w);

    float y = cy + d;
    gl_Position = vec4(aPos.x * 2.0 - 1.0, 1.0 - 2.0 * (y / uRes.y), 0.0, 1.0);
}
"""

    /**
     * Drawn additively over the backdrop. A lit upper boundary and a darker lower one
     * give the sheet a front and a back; brightness tracks the same twist value that
     * drives the thickness.
     */
    const val BAND_FRAGMENT = """
precision mediump float;

uniform vec3  uBody;
uniform vec3  uTopEdge;
uniform vec3  uBotEdge;
uniform float uAlpha;

varying vec4 vBand;

void main() {
    float d  = vBand.x;
    float ht = vBand.y;
    float w  = vBand.w;

    float body = 1.0 - smoothstep(ht * 0.80, ht, abs(d));

    // q*q rather than pow(q, 2.0): pow() is undefined for a negative base in GLSL ES.
    float qt = (d + ht) / w;
    float qb = (d - ht) / (w * 0.9);
    float top = exp(-qt * qt);
    float bot = exp(-qb * qb);

    vec3 col = (uBody * body * 0.95 + uTopEdge * top * 0.75 + uBotEdge * bot * 0.6)
             * vBand.z * uAlpha;

    gl_FragColor = vec4(col, 1.0);
}
"""
}
