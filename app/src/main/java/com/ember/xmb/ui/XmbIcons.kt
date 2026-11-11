package com.ember.xmb.ui

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import com.ember.xmb.R
import com.ember.xmb.model.CategoryIcon
import com.ember.xmb.model.XmbGlyph

/**
 * Icons are built from SVG path data at runtime rather than shipped as a pile of
 * vector XML files — one place to read them, and they tint through ColorFilter.
 */
private fun stroked(name: String, strokeWidth: Float, vararg paths: String): ImageVector {
    val builder = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
    paths.forEach { data ->
        builder.addPath(
            pathData = PathParser().parsePathString(data).toNodes(),
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = strokeWidth,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
    return builder.build()
}

private fun filled(name: String, vararg paths: String): ImageVector {
    val builder = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
    paths.forEach { data ->
        builder.addPath(
            pathData = PathParser().parsePathString(data).toNodes(),
            fill = SolidColor(Color.White),
        )
    }
    return builder.build()
}

object XmbIcons {

    val Users = stroked(
        "users", 1.4f,
        "M12 4.6a3.4 3.4 0 1 1 0 6.8 3.4 3.4 0 0 1 0-6.8z",
        "M5 20c0-3.6 3.1-6 7-6s7 2.4 7 6",
    )

    val Settings = stroked(
        "settings", 1.4f,
        "M12 8.8a3.2 3.2 0 1 1 0 6.4 3.2 3.2 0 0 1 0-6.4z",
        "M12 2.5v3M12 18.5v3M2.5 12h3M18.5 12h3M5 5l2.1 2.1M16.9 16.9 19 19M19 5l-2.1 2.1M7.1 16.9 5 19",
    )

    val Continue = stroked(
        "continue", 1.4f,
        "M12 3a9 9 0 1 1 0 18 9 9 0 0 1 0-18z",
        "M10.2 8.4v7.2l5.6-3.6z",
    )

    val Apps = stroked(
        "apps", 1.5f,
        "M5.1 3.5h3.8a1.6 1.6 0 0 1 1.6 1.6v3.8a1.6 1.6 0 0 1-1.6 1.6H5.1a1.6 1.6 0 0 1-1.6-1.6V5.1a1.6 1.6 0 0 1 1.6-1.6z",
        "M15.1 3.5h3.8a1.6 1.6 0 0 1 1.6 1.6v3.8a1.6 1.6 0 0 1-1.6 1.6h-3.8a1.6 1.6 0 0 1-1.6-1.6V5.1a1.6 1.6 0 0 1 1.6-1.6z",
        "M5.1 13.5h3.8a1.6 1.6 0 0 1 1.6 1.6v3.8a1.6 1.6 0 0 1-1.6 1.6H5.1a1.6 1.6 0 0 1-1.6-1.6v-3.8a1.6 1.6 0 0 1 1.6-1.6z",
        "M15.1 13.5h3.8a1.6 1.6 0 0 1 1.6 1.6v3.8a1.6 1.6 0 0 1-1.6 1.6h-3.8a1.6 1.6 0 0 1-1.6-1.6v-3.8a1.6 1.6 0 0 1 1.6-1.6z",
    )

    val Games = stroked(
        "games", 1.4f,
        "M7 8h10a4 4 0 0 1 4 4l-.7 4.4A2.4 2.4 0 0 1 16.1 18l-1.6-2.2h-5L7.9 18a2.4 2.4 0 0 1-4.2-1.6L3 12a4 4 0 0 1 4-4z",
        "M7.5 11.5v2M6.5 12.5h2M15.2 11.6h.01M17 13.3h.01",
    )

    val Video = stroked(
        "video", 1.4f,
        "M5 5h14a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z",
        "M10 9.2v5.6l4.6-2.8z",
    )

    val Music = stroked(
        "music", 1.4f,
        "M9 18V5l11-2v13",
        "M6 15a3 3 0 1 1 0 6 3 3 0 0 1 0-6z",
        "M17 13a3 3 0 1 1 0 6 3 3 0 0 1 0-6z",
    )

    val Photo = stroked(
        "photo", 1.4f,
        "M5 5h14a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z",
        "M8.5 8.4a1.6 1.6 0 1 1 0 3.2 1.6 1.6 0 0 1 0-3.2z",
        "m4 17 5-4.5 4 3.3 3-2.3 4 3.5",
    )

    val Wifi = stroked(
        "wifi", 1.8f,
        "M3.5 8.6a13.5 13.5 0 0 1 17 0",
        "M6.8 12.3a8.5 8.5 0 0 1 10.4 0",
        "M10 15.9a3.6 3.6 0 0 1 4 0",
        "M12 18.6a.6.6 0 1 1 0 1.2.6.6 0 0 1 0-1.2z",
    )

    val Ethernet = stroked(
        "ethernet", 1.6f,
        "M4 14h16a1 1 0 0 1 1 1v4a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1v-4a1 1 0 0 1 1-1z",
        "M7 14v-3h10v3M12 11V5",
    )

    val Offline = stroked(
        "offline", 1.8f,
        "M4 4l16 16",
        "M10 15.9a3.6 3.6 0 0 1 4 0",
        "M12 18.6a.6.6 0 1 1 0 1.2.6.6 0 0 1 0-1.2z",
    )

    val Play = filled("play", "M9 7.5v9l7.5-4.5z")

    val Heart = filled(
        "heart",
        "M12 20s-7-4.6-7-9.3A4.1 4.1 0 0 1 12 8a4.1 4.1 0 0 1 7 2.7C19 15.4 12 20 12 20z",
    )

    val MenuOpen = stroked(
        "open", 1.7f,
        "M14 4h6v6", "M20 4 11 13",
        "M19 14.5V19a1.5 1.5 0 0 1-1.5 1.5h-11A1.5 1.5 0 0 1 5 19V8a1.5 1.5 0 0 1 1.5-1.5H11",
    )

    val MenuMove = stroked(
        "move", 1.7f,
        "M12 3v18M3 12h18",
        "m9 6 3-3 3 3M9 18l3 3 3-3M6 9l-3 3 3 3M18 9l3 3-3 3",
    )

    val MenuInfo = stroked(
        "info", 1.7f,
        "M12 3a9 9 0 1 1 0 18 9 9 0 0 1 0-18z",
        "M12 11v5",
        "M12 6.9a.9.9 0 1 1 0 1.8.9.9 0 0 1 0-1.8z",
    )

    val MenuUninstall = stroked("uninstall", 1.7f, "M6 6l12 12M18 6 6 18")

    val MenuCategory = stroked(
        "category", 1.7f,
        "M4.5 6h4L11 8.5h8.5A1.5 1.5 0 0 1 21 10v8a1.5 1.5 0 0 1-1.5 1.5h-15A1.5 1.5 0 0 1 3 18V7.5A1.5 1.5 0 0 1 4.5 6z",
    )

    /**
     * Left-right, against [MenuMove]'s four-way cross. The two actions sit next to each
     * other in the menu, so they need to read apart at a glance: items move up the
     * column, categories move along the row.
     */
    val MenuRearrange = stroked(
        "rearrange", 1.7f,
        "M3 12h18",
        "m7 8-4 4 4 4M17 8l4 4-4 4",
    )

    val Check = stroked("check", 2.2f, "m5 12.5 4.5 4.5L19 7")

    val Device = stroked(
        "device", 1.4f,
        "M4 4h16a1.5 1.5 0 0 1 1.5 1.5v10A1.5 1.5 0 0 1 20 17H4a1.5 1.5 0 0 1-1.5-1.5v-10A1.5 1.5 0 0 1 4 4z",
        "M9 20h6M12 17v3",
    )

    /** Half-filled disc: the usual mark for a colour or contrast setting. */
    val Theme = ImageVector.Builder(
        name = "theme",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        addPath(
            pathData = PathParser().parsePathString("M12 3a9 9 0 1 1 0 18 9 9 0 0 1 0-18z").toNodes(),
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.4f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
        addPath(
            pathData = PathParser().parsePathString("M12 3a9 9 0 0 1 0 18z").toNodes(),
            fill = SolidColor(Color.White),
        )
    }.build()

    val Developer = stroked(
        "developer", 1.6f,
        "M9 7 4.5 12 9 17",
        "M15 7l4.5 5L15 17",
    )

    /**
     * The category row and the Settings rows use the real PS3 XMB artwork, cut from
     * the icon sheet rather than redrawn, so the shapes are pixel-for-pixel the
     * originals. They are monochrome silhouettes with the alpha taken from the
     * source's luminance, so tinting them with the theme accent works exactly as it
     * did for the vector versions.
     *
     * The menu panel below keeps its own vector icons on purpose — that panel is
     * deliberately Android TV's look, and PS3 glyphs would clash inside it.
     */
    @DrawableRes
    fun categoryRes(icon: CategoryIcon): Int = when (icon) {
        CategoryIcon.USERS -> R.drawable.ps3_users
        CategoryIcon.CONTINUE -> R.drawable.ps3_continue
        CategoryIcon.APPS -> R.drawable.ps3_apps
        CategoryIcon.GAMES -> R.drawable.ps3_games
        CategoryIcon.VIDEO -> R.drawable.ps3_video
        CategoryIcon.MUSIC -> R.drawable.ps3_music
        CategoryIcon.PHOTO -> R.drawable.ps3_photo
        CategoryIcon.SETTINGS -> R.drawable.ps3_settings
    }

    @DrawableRes
    fun glyphRes(glyph: XmbGlyph): Int = when (glyph) {
        XmbGlyph.PROFILE -> R.drawable.ps3_users
        XmbGlyph.DEVICE -> R.drawable.ps3_device
        XmbGlyph.WIFI -> R.drawable.ps3_network
        XmbGlyph.THEME -> R.drawable.ps3_theme
        XmbGlyph.DISPLAY -> R.drawable.ps3_display
        XmbGlyph.APPS -> R.drawable.ps3_apps
        XmbGlyph.DEVELOPER -> R.drawable.ps3_developer
        XmbGlyph.ABOUT -> R.drawable.ps3_about
    }

    fun glyph(glyph: XmbGlyph): ImageVector = when (glyph) {
        XmbGlyph.PROFILE -> Users
        XmbGlyph.DEVICE -> Device
        XmbGlyph.WIFI -> Wifi
        XmbGlyph.THEME -> Theme
        XmbGlyph.DISPLAY -> Video
        XmbGlyph.APPS -> Apps
        XmbGlyph.DEVELOPER -> Developer
        XmbGlyph.ABOUT -> MenuInfo
    }

    fun category(icon: CategoryIcon): ImageVector = when (icon) {
        CategoryIcon.USERS -> Users
        CategoryIcon.CONTINUE -> Continue
        CategoryIcon.APPS -> Apps
        CategoryIcon.GAMES -> Games
        CategoryIcon.VIDEO -> Video
        CategoryIcon.MUSIC -> Music
        CategoryIcon.PHOTO -> Photo
        CategoryIcon.SETTINGS -> Settings
    }
}
