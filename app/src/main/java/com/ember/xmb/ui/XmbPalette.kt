package com.ember.xmb.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.cos

/**
 * The XMB palette.
 *
 * Two inputs drive it, exactly as the original did: the month sets the hue, and the
 * time of day sets how bright and saturated everything is. A manual theme overrides
 * only the hue; the time-of-day shading still applies.
 *
 * Everything is ANALOGOUS. Three neighbours on the wheel carry three roles —
 * shadow at hue-28, core at hue, highlight at hue+22 — and nothing is allowed outside
 * that window, so the background always reads as one family instead of a clash.
 */
@Immutable
data class XmbPalette(
    val hue: Float,
    /** 0 = deep night, 1 = midday. */
    val shade: Float,
    val bg0: Color,
    val bg1: Color,
    val tint: Color,
    val accent: Color,
    val glow: Color,
    val panel: Color,
    val scrim: Color,
    val bandBody: Color,
    val bandTop: Color,
    val bandBottom: Color,
) {
    val fg = Color(0xFFF2F6FC)
    val dim = Color(0xFFF2F6FC).copy(alpha = 0.52f)
    val faint = Color(0xFFF2F6FC).copy(alpha = 0.20f)
}

data class XmbThemeOption(val name: String, val hue: Float?, val month: String? = null)

object XmbThemes {

    private const val SPREAD_LO = 28f
    private const val SPREAD_HI = 22f

    /** One hue per month, named so they can also be picked manually. */
    private val MONTHS = listOf(
        XmbThemeOption("Aqua", 205f, "Jan"),
        XmbThemeOption("Indigo", 255f, "Feb"),
        XmbThemeOption("Orchid", 320f, "Mar"),
        XmbThemeOption("Rose", 330f, "Apr"),
        XmbThemeOption("Coral", 15f, "May"),
        XmbThemeOption("Gold", 45f, "Jun"),
        XmbThemeOption("Teal", 175f, "Jul"),
        XmbThemeOption("Jade", 140f, "Aug"),
        XmbThemeOption("Amber", 25f, "Sep"),
        XmbThemeOption("Ember", 30f, "Oct"),
        XmbThemeOption("Crimson", 350f, "Nov"),
        XmbThemeOption("Azure", 218f, "Dec"),
    )

    /** Index 0 is Auto; 1..12 are the fixed month colours. */
    val options: List<XmbThemeOption> =
        listOf(XmbThemeOption("Auto — season & time", null)) + MONTHS

    fun hueForOption(index: Int, now: Calendar = Calendar.getInstance()): Float =
        options.getOrNull(index)?.hue ?: MONTHS[now.get(Calendar.MONTH)].hue!!

    /**
     * Brightness over the day: peaks around 13:00, bottoms around 01:00.
     * A smooth cosine rather than buckets, so the screen drifts instead of stepping.
     */
    fun shadeForTime(now: Calendar): Float {
        val h = now.get(Calendar.HOUR_OF_DAY) + now.get(Calendar.MINUTE) / 60f
        return ((cos((h - 13f) / 24f * 2f * Math.PI).toFloat()) + 1f) / 2f
    }

    fun palette(themeIndex: Int, now: Calendar = Calendar.getInstance()): XmbPalette {
        val hue = hueForOption(themeIndex, now)
        val s = shadeForTime(now)
        val hDark = wrap(hue - SPREAD_LO)
        val hLite = wrap(hue + SPREAD_HI)

        return XmbPalette(
            hue = hue,
            shade = s,
            bg0 = hsl(hDark, 0.42f, 0.03f + s * 0.02f),
            bg1 = hsl(hue, 0.28f + s * 0.10f, 0.07f + s * 0.05f),
            tint = hsl(hLite, 0.50f + s * 0.22f, 0.07f + s * 0.09f),
            accent = hsl(hue, 0.86f + s * 0.06f, 0.58f + s * 0.12f),
            glow = hsl(hue, 0.86f + s * 0.06f, 0.52f + s * 0.12f, 0.55f),
            panel = hsl(hDark, 0.36f, 0.08f + s * 0.03f),
            scrim = hsl(hDark, 0.45f, 0.03f + s * 0.01f),
            bandBottom = hsl(hDark, 0.86f, 0.36f + s * 0.12f),
            bandBody = hsl(hue, 0.88f, 0.50f + s * 0.12f),
            bandTop = hsl(hLite, 0.96f, 0.74f + s * 0.12f),
        )
    }

    private fun wrap(h: Float): Float = ((h % 360f) + 360f) % 360f
}

/** HSL with s and l in 0f..1f, h in degrees. */
fun hsl(h: Float, s: Float, l: Float, alpha: Float = 1f): Color {
    val sc = s.coerceIn(0f, 1f)
    val lc = l.coerceIn(0f, 1f)
    val c = (1f - abs(2f * lc - 1f)) * sc
    val hp = (((h % 360f) + 360f) % 360f) / 60f
    val x = c * (1f - abs(hp % 2f - 1f))
    val (r, g, b) = when {
        hp < 1f -> Triple(c, x, 0f)
        hp < 2f -> Triple(x, c, 0f)
        hp < 3f -> Triple(0f, c, x)
        hp < 4f -> Triple(0f, x, c)
        hp < 5f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    val m = lc - c / 2f
    return Color(
        red = (r + m).coerceIn(0f, 1f),
        green = (g + m).coerceIn(0f, 1f),
        blue = (b + m).coerceIn(0f, 1f),
        alpha = alpha.coerceIn(0f, 1f),
    )
}

/**
 * An app's brand colour is designed as a BACKGROUND with white on top. Used directly
 * as a glyph colour on a dark screen several would vanish, so floor the lightness and
 * keep a minimum saturation. The hue — the part that carries recognition — is kept.
 */
fun legibleOnDark(argb: Long): Color {
    val r = ((argb shr 16) and 0xFF) / 255f
    val g = ((argb shr 8) and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f
    val mx = maxOf(r, g, b)
    val mn = minOf(r, g, b)
    val l = (mx + mn) / 2f
    val d = mx - mn
    var h = 0f
    var s = 0f
    if (d > 0f) {
        s = d / (1f - abs(2f * l - 1f))
        h = 60f * when (mx) {
            r -> ((g - b) / d) + if (g < b) 6f else 0f
            g -> ((b - r) / d) + 2f
            else -> ((r - g) / d) + 4f
        }
    }
    return hsl(h, s.coerceIn(0.38f, 1f), maxOf(0.66f, l))
}
