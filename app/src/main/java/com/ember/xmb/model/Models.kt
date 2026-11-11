package com.ember.xmb.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap

/** How a category renders its items. */
enum class ItemKind {
    /** Icon + label row. */
    APP,

    /** 16:9 thumbnail card with a progress bar — Continue Watching. */
    CARD,

    /** A launcher-owned action rather than an installed package. */
    ACTION,
}

enum class CategoryIcon { USERS, CONTINUE, APPS, GAMES, VIDEO, MUSIC, PHOTO, SETTINGS }

/** Glyph for items that have no package icon of their own (Settings rows, profiles). */
enum class XmbGlyph { PROFILE, DEVICE, WIFI, THEME, DISPLAY, APPS, DEVELOPER, ABOUT }

/**
 * Which typed category an app is assigned to. Apps always shows everything, so an
 * assignment decides where else the app appears rather than hiding it.
 */
object AppCategory {
    const val APPS = "apps"
    const val GAMES = "games"
    const val VIDEO = "video"
    const val MUSIC = "music"
    const val PHOTO = "photo"

    val ASSIGNABLE = listOf(APPS, GAMES, VIDEO, MUSIC, PHOTO)

    fun icon(key: String): CategoryIcon = when (key) {
        GAMES -> CategoryIcon.GAMES
        VIDEO -> CategoryIcon.VIDEO
        MUSIC -> CategoryIcon.MUSIC
        PHOTO -> CategoryIcon.PHOTO
        else -> CategoryIcon.APPS
    }
}

/** Actions that belong to the launcher itself, not to any installed app. */
sealed interface ItemAction {
    data object ThemePicker : ItemAction

    /** Fires a system Settings intent, e.g. [android.provider.Settings.ACTION_WIFI_SETTINGS]. */
    data class SystemSettings(val action: String) : ItemAction
}

@Immutable
data class LauncherItem(
    /** Stable identity: package name for apps, a fixed key for actions. */
    val id: String,
    val label: String,
    val kind: ItemKind,
    val packageName: String? = null,
    /** Square-ish icon drawn in the column. */
    val icon: ImageBitmap? = null,
    /** 16:9 artwork for [ItemKind.CARD]. */
    val artwork: ImageBitmap? = null,
    /** Remote/content artwork not yet resolved; [com.ember.xmb.data.ArtLoader] fills it in. */
    val artworkUri: String? = null,
    /** "S2 · E4 · 23 min left" */
    val subtitle: String? = null,
    /** 0f..1f, drawn as the bar across the bottom of a card. */
    val progress: Float = 0f,
    /**
     * System/preinstalled packages cannot be uninstalled by intent — on Fire TV that
     * covers every Amazon app. The menu greys the row out rather than firing an
     * intent the system will silently refuse.
     */
    val isSystem: Boolean = false,
    val favourite: Boolean = false,
    val action: ItemAction? = null,
    /** Drawn when [icon] is null. Without this every action row looks identical. */
    val glyph: XmbGlyph? = null,
    /** Current [AppCategory] assignment, so the picker can tick the right row. */
    val assigned: String = AppCategory.APPS,
    /** Fallback colour when there is no icon, already lightened for a dark ground. */
    val accent: Long = 0xFF8EA0B8,
)

@Immutable
data class Category(
    val key: String,
    val title: String,
    val icon: CategoryIcon,
    val items: List<LauncherItem>,
    /** Render items as thumbnail cards instead of icon rows. */
    val cards: Boolean = false,
    /** Long-press opens the app context menu. Only true where items are real packages. */
    val contextMenu: Boolean = false,
) {
    companion object {
        const val KEY_USERS = "users"
        const val KEY_CONTINUE = "continue"
        const val KEY_APPS = "apps"
        const val KEY_GAMES = "games"
        const val KEY_VIDEO = "video"
        const val KEY_MUSIC = "music"
        const val KEY_PHOTO = "photo"
        const val KEY_SETTINGS = "settings"
    }
}

/**
 * What the screen is currently doing. Drives key handling.
 *
 * [MOVE] and [REARRANGE] are the same gesture on the two axes of the cross: items move
 * up and down inside a category, categories move left and right along the row.
 */
enum class Mode { NAV, MENU, MOVE, REARRANGE, THEME, CATEGORY }
