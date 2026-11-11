package com.ember.xmb.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.ui.graphics.ImageBitmap
import com.ember.xmb.model.AppCategory
import com.ember.xmb.model.Category
import com.ember.xmb.model.ItemKind
import com.ember.xmb.model.LauncherItem

/**
 * Everything that talks to PackageManager.
 *
 * Open / Info / Uninstall are all plain intents — no special permissions. Uninstall is
 * the one with a catch: ACTION_DELETE is silently refused for system packages, which on
 * Fire TV means every Amazon preinstall, so [LauncherItem.isSystem] is carried through
 * and the menu disables the row rather than firing an intent the system will ignore.
 */
class AppRepository(context: Context) {

    private val ctx = context.applicationContext
    private val pm = ctx.packageManager

    /**
     * [all] is every launchable app and backs the Apps category. [byCategory] holds the
     * typed categories; an app appears in one of them as well as in Apps.
     */
    data class Loaded(
        val all: List<LauncherItem>,
        val byCategory: Map<String, List<LauncherItem>>,
    )

    fun load(prefs: Prefs): Loaded {
        val favourites = prefs.favourites()
        val overrides = prefs.categoryOverrides()

        // Leanback entries first so a TV-native activity wins over a phone one.
        val byPackage = LinkedHashMap<String, ResolveInfo>()
        for (ri in queryLaunchable()) {
            val pkg = ri.activityInfo?.packageName ?: continue
            if (pkg == ctx.packageName) continue
            if (!byPackage.containsKey(pkg)) byPackage[pkg] = ri
        }

        val all = ArrayList<LauncherItem>(byPackage.size)
        for ((pkg, ri) in byPackage) {
            val appInfo = ri.activityInfo.applicationInfo
            all += LauncherItem(
                id = pkg,
                label = ri.loadLabel(pm).toString().ifEmpty { pkg },
                kind = ItemKind.APP,
                packageName = pkg,
                icon = runCatching { ri.loadIcon(pm).toImageBitmap(ICON_PX, ICON_PX) }.getOrNull(),
                isSystem = appInfo.isSystemPackage(),
                favourite = favourites.contains(pkg),
                assigned = overrides[pkg] ?: autoCategory(pkg, appInfo),
            )
        }

        val ordered = applyOrder(all, prefs.order(Category.KEY_APPS))
        val typed = AppCategory.ASSIGNABLE.associateWith { key ->
            applyOrder(ordered.filter { it.assigned == key }, prefs.order(key))
        }
        return Loaded(all = ordered, byCategory = typed)
    }

    /**
     * Auto-assignment, best source first.
     *
     * The system's own `ApplicationInfo.category` is authoritative when an app sets it,
     * but most of the big streaming apps on Fire TV leave it UNDEFINED, which is why
     * the package-name table exists. It is a seed for first run, not a hard rule —
     * anything the user moves by hand is stored as an override and wins.
     */
    private fun autoCategory(pkg: String, info: ApplicationInfo): String {
        if (info.isGame()) return AppCategory.GAMES

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            when (info.category) {
                ApplicationInfo.CATEGORY_VIDEO -> return AppCategory.VIDEO
                ApplicationInfo.CATEGORY_AUDIO -> return AppCategory.MUSIC
                ApplicationInfo.CATEGORY_IMAGE -> return AppCategory.PHOTO
                ApplicationInfo.CATEGORY_GAME -> return AppCategory.GAMES
                else -> Unit
            }
        }

        val lower = pkg.lowercase()
        for ((category, needles) in SEEDS) {
            if (needles.any { lower.contains(it) }) return category
        }
        return AppCategory.APPS
    }

    private fun queryLaunchable(): List<ResolveInfo> {
        val leanback = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val standard = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val out = ArrayList<ResolveInfo>()
        out += runCatching { pm.queryIntentActivities(leanback, 0) }.getOrDefault(emptyList())
        out += runCatching { pm.queryIntentActivities(standard, 0) }.getOrDefault(emptyList())
        return out
    }

    /**
     * With no saved order, favourites float to the top and the rest go alphabetical.
     * Once the user has used Move, their order is authoritative and nothing re-sorts.
     */
    private fun applyOrder(items: List<LauncherItem>, order: List<String>): List<LauncherItem> {
        if (order.isEmpty()) {
            return items.sortedWith(
                compareByDescending<LauncherItem> { it.favourite }.thenBy { it.label.lowercase() }
            )
        }
        val rank = order.withIndex().associate { (index, id) -> id to index }
        return items.sortedWith(
            compareBy({ rank[it.id] ?: Int.MAX_VALUE }, { it.label.lowercase() })
        )
    }

    fun isLaunchable(packageName: String): Boolean =
        pm.getLeanbackLaunchIntentForPackage(packageName) != null ||
            pm.getLaunchIntentForPackage(packageName) != null

    fun launch(packageName: String): Boolean {
        val intent = pm.getLeanbackLaunchIntentForPackage(packageName)
            ?: pm.getLaunchIntentForPackage(packageName)
            ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return runCatching { ctx.startActivity(intent); true }.getOrDefault(false)
    }

    fun launchIntentUri(intentUri: String): Boolean = runCatching {
        val intent = Intent.parseUri(intentUri, Intent.URI_INTENT_SCHEME)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
        true
    }.getOrDefault(false)

    fun openAppInfo(packageName: String) {
        start(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName")
            )
        )
    }

    /** The system shows its own confirmation dialog. Refused for system packages. */
    fun requestUninstall(packageName: String) {
        start(Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName")))
    }

    fun openSystemSettings(action: String) = start(Intent(action))

    /** 16:9 TV banner, used for Continue Watching cards. */
    fun banner(packageName: String): ImageBitmap? = runCatching {
        val info = pm.getApplicationInfo(packageName, 0)
        (info.loadBanner(pm) ?: info.loadLogo(pm))?.toImageBitmap(CARD_W, CARD_H)
    }.getOrNull()

    fun icon(packageName: String): ImageBitmap? = runCatching {
        pm.getApplicationIcon(packageName).toImageBitmap(ICON_PX, ICON_PX)
    }.getOrNull()

    fun label(packageName: String): String? = runCatching {
        pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString()
    }.getOrNull()

    private fun start(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(intent) }
    }

    private fun ApplicationInfo.isSystemPackage(): Boolean =
        (flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
            (flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

    private fun ApplicationInfo.isGame(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            category == ApplicationInfo.CATEGORY_GAME
        } else {
            @Suppress("DEPRECATION")
            (flags and ApplicationInfo.FLAG_IS_GAME) != 0
        }

    companion object {
        const val ICON_PX = 128
        const val CARD_W = 480
        const val CARD_H = 270

        /** Package-name fragments, checked in order. First match wins. */
        private val SEEDS: List<Pair<String, List<String>>> = listOf(
            AppCategory.VIDEO to listOf(
                "netflix", "avod", "primevideo", "disneyplus", "hbo", "wbd.stream",
                "hulu", "plexapp", "jellyfin", "twitch", "youtube", "crunchyroll",
                "peacock", "paramount", "tubi", "pluto", "starz", "discovery",
                "sling", "britbox", "mubi", "atve", "miniTV".lowercase(), "vudu",
                "curiositystream", "kodi", "stremio",
            ),
            AppCategory.MUSIC to listOf(
                "spotify", "bueller.music", "amazon.music", "pandora", "tunein",
                "iheart", "deezer", "tidal", "soundcloud", "audible", "plexamp",
            ),
            AppCategory.PHOTO to listOf(
                "photos", "gallery", "amazon.photo",
            ),
        )
    }
}
