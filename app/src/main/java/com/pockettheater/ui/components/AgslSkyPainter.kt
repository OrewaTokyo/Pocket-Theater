package com.pockettheater.ui.components

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RuntimeShader

/**
 * The real sky, painted by the phone's graphics chip: layered clouds that are lit from the side of the sun, haze that
 * thickens towards the horizon, stars that appear as the sky darkens, and a glow under the horizon.
 * It needs Android 13 (the only place [RuntimeShader] exists), so it is only ever created there.
 * If the shader cannot be built or drawn, the sky view falls back to [GradientSkyPainter].
 */
@SuppressLint("NewApi")
class AgslSkyPainter : SkyPainter {
    private val agsl = RuntimeShader(SOURCE)
    private val paint = Paint()

    init {
        paint.shader = agsl
    }

    override fun draw(canvas: Canvas, width: Int, height: Int, mood: Float, seconds: Float) {
        agsl.setFloatUniform("iResolution", width.toFloat(), height.toFloat())
        agsl.setFloatUniform("iTime", seconds)
        agsl.setFloatUniform("iMood", mood)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    companion object {
        // Written in AGSL, Android's shader language. Previewed and tuned off the phone before it went in here.
        private const val SOURCE = """
uniform float2 iResolution;
uniform float iTime;
uniform float iMood;

float hash12(float2 p) {
    float3 p3 = fract(float3(p.x, p.y, p.x) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float vnoise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash12(i);
    float b = hash12(i + float2(1.0, 0.0));
    float c = hash12(i + float2(0.0, 1.0));
    float d = hash12(i + float2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm(float2 x) {
    float2 p = x;
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 5; i++) {
        v += a * vnoise(p);
        p = float2(1.6 * p.x + 1.2 * p.y, -1.2 * p.x + 1.6 * p.y);
        a *= 0.5;
    }
    return v;
}

float3 pal(float3 day, float3 night) {
    return mix(day, night, iMood);
}

float3 skyGradient(float t) {
    float3 c0 = pal(float3(1.00, 0.87, 0.64), float3(0.50, 0.40, 0.68));
    float3 c1 = pal(float3(1.00, 0.66, 0.46), float3(0.27, 0.34, 0.63));
    float3 c2 = pal(float3(0.86, 0.42, 0.60), float3(0.15, 0.26, 0.52));
    float3 c3 = pal(float3(0.47, 0.30, 0.68), float3(0.08, 0.17, 0.39));
    float3 c4 = pal(float3(0.22, 0.17, 0.55), float3(0.04, 0.09, 0.27));
    float3 c5 = pal(float3(0.10, 0.10, 0.36), float3(0.02, 0.04, 0.13));
    float3 c = mix(c0, c1, smoothstep(0.0, 0.14, t));
    c = mix(c, c2, smoothstep(0.10, 0.30, t));
    c = mix(c, c3, smoothstep(0.26, 0.50, t));
    c = mix(c, c4, smoothstep(0.46, 0.72, t));
    c = mix(c, c5, smoothstep(0.68, 1.0, t));
    return c;
}

half4 main(float2 fragCoord) {
    float asp = iResolution.x / iResolution.y;
    float2 uv = fragCoord / iResolution;
    float y = 1.0 - uv.y;
    float2 p = float2((uv.x - 0.5) * asp, y);
    float H = 0.22;
    float t = clamp((y - H) / (1.0 - H), 0.0, 1.0);
    float night = iMood;

    float3 col = skyGradient(t);

    // glow of the sun just under the horizon, turning into the pink glow of the city at night
    float2 sp = float2(asp * 0.20, H - 0.03);
    float2 sd = (p - sp) * float2(0.45, 1.6);
    float glow = exp(-length(sd) * 3.2) * 0.42 + exp(-length(sd) * 10.0) * 0.30;
    float3 glowCol = pal(float3(1.0, 0.68, 0.36), float3(1.0, 0.42, 0.68));
    col += glowCol * glow * mix(1.0, 0.55, night);

    // stars, only in the upper sky and mostly at night
    float2 g = float2(p.x * 38.0, y * 38.0);
    float2 gi = floor(g);
    float2 gf = fract(g) - 0.5;
    float h = hash12(gi);
    float star = 0.0;
    if (h > 0.93) {
        float2 jit = float2(hash12(gi + 7.7), hash12(gi + 3.1)) - 0.5;
        float d = length(gf - jit * 0.6);
        float tw = 0.55 + 0.45 * sin(iTime * (1.0 + h * 5.0) + h * 80.0);
        star = (1.0 - smoothstep(0.0, 0.30, d)) * tw * (0.35 + (h - 0.93) * 9.0);
    }
    float starVis = smoothstep(0.12, 0.6, t) * (0.10 + 0.90 * night);
    col += float3(0.92, 0.95, 1.0) * star * starVis;

    // thin high clouds
    float2 hq = float2(p.x * 1.1 + iTime * 0.006, y * 7.0);
    hq.x += y * 1.4;
    float hc = fbm(float2(hq.x * 1.8, hq.y * 0.8) + 3.3);
    float hcov = smoothstep(0.52, 0.76, hc) * smoothstep(0.30, 0.62, t) * (1.0 - smoothstep(0.88, 1.0, t));
    float3 hcol = pal(float3(1.0, 0.74, 0.80), float3(0.52, 0.60, 0.90));
    col = mix(col, hcol, hcov * 0.34);

    float alt = max(y - H, 0.0);
    float pr = 1.0 / (0.10 + alt * 0.85);

    // far bank of clouds: smaller, flatter and paler, for depth
    float2 fq = float2(p.x * pr * 2.4 + iTime * 0.010, pr * 3.2 + 4.0);
    float f1 = fbm(fq);
    float f2 = fbm(fq + float2(0.18, 0.20));
    float fcover = smoothstep(0.50, 0.60, f1);
    fcover *= smoothstep(0.0, 0.04, alt) * (1.0 - smoothstep(0.22, 0.55, t));
    float flit = clamp(0.5 + (f1 - f2) * 5.0, 0.0, 1.0);
    float3 fcol = mix(pal(float3(0.66, 0.40, 0.62), float3(0.16, 0.22, 0.48)),
                      pal(float3(1.0, 0.70, 0.62), float3(0.40, 0.50, 0.80)), flit);
    col = mix(col, fcol, fcover * 0.75);

    // big near clouds, seen from below: tall banks that rise from the horizon, lit from the side of the sun
    float2 q = float2(p.x * pr * 1.25 + iTime * 0.018, pr * 1.7);
    float2 w = float2(vnoise(q * 0.7 + 5.0), vnoise(q * 0.7 + 9.0));
    q += (w - 0.5) * 0.35;
    float d1 = fbm(q);
    float d2 = fbm(q + float2(0.14, 0.17));
    float thr = mix(0.43, 0.67, smoothstep(0.0, 0.75, t));
    float edge = mix(0.11, 0.045, smoothstep(0.1, 0.7, t));
    float cover = smoothstep(thr, thr + edge, d1);
    cover *= smoothstep(0.0, 0.03, alt);
    float lit = clamp(0.5 + (d1 - d2) * 6.0, 0.0, 1.0);
    float core = smoothstep(thr + 0.08, thr + 0.34, d1);
    float3 litCol = mix(pal(float3(1.0, 0.80, 0.62), float3(0.52, 0.64, 0.92)),
                        pal(float3(1.0, 0.60, 0.72), float3(0.44, 0.50, 0.86)),
                        smoothstep(0.05, 0.55, t));
    float3 shadeCol = pal(float3(0.36, 0.26, 0.58), float3(0.07, 0.10, 0.28));
    float3 cloud = mix(shadeCol, litCol, lit * (1.0 - 0.5 * core));
    float rim = pow(clamp((d1 - d2) * 10.0, 0.0, 1.0), 2.0) * (1.0 - core);
    cloud += litCol * rim * 0.45 * (1.0 - 0.5 * night);
    cloud = mix(cloud, skyGradient(0.0), (1.0 - smoothstep(0.0, 0.14, t)) * 0.5);
    col = mix(col, cloud, cover * 0.96);

    // keep the brightest parts from burning out
    col = col / (1.0 + 0.22 * col);
    col *= 1.12;

    // below the horizon is the dark ground of the city, fading up into the haze
    float ground = 1.0 - smoothstep(H - 0.07, H + 0.015, y);
    col = mix(col, float3(0.035, 0.035, 0.10) + glowCol * 0.05, ground);

    // a little grain so the gradient never shows bands
    float grain = hash12(fragCoord + float2(iTime * 13.0, iTime * 7.0)) - 0.5;
    col += grain * 0.03;
    return half4(half3(col), 1.0);
}
"""
    }
}
