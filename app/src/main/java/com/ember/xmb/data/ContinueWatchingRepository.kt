package com.ember.xmb.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.database.Cursor
import android.os.Build
import android.os.Process
import android.util.Log
import androidx.tvprovider.media.tv.TvContractCompat
import com.ember.xmb.model.ItemKind
import com.ember.xmb.model.LauncherItem
import java.util.concurrent.TimeUnit

/**
 * Continue Watching.
 *
 * Measured on an AFTCU864A2 before any of this was written:
 *
 * - The Android TV **Watch Next** provider exists but is completely empty. Nothing on
 *   Fire OS writes to it, so it is tried first and almost always yields nothing.
 * - Amazon's own Continue Watching row is fed by **private, signed system providers**.
 *   A third-party launcher cannot read it, and no public provider carries playback
 *   positions or episode titles. We do not fabricate either.
 * - **UsageStatsManager** does work and the system is actively recording per-package
 *   foreground events, so it gives genuine, system-wide recency — including apps
 *   opened from Amazon's launcher rather than only from ours.
 *
 * The catch is that Fire OS resolves `android.settings.USAGE_ACCESS_SETTINGS` to a
 * `CTSDummySettingActivity` — a stub shipped to pass compliance tests. There is no UI
 * to grant the permission, so it has to come from ADB once:
 *
 *     adb shell appops set com.ember.xmb GET_USAGE_STATS allow
 *
 * Without it we fall back to the launches we tracked ourselves.
 */
class ContinueWatchingRepository(context: Context) {

    private val ctx = context.applicationContext

    /**
     * Both spellings of this check are deprecated at different API levels and there is
     * no non-deprecated replacement that works across our range, so the suppression is
     * deliberate rather than neglect.
     */
    @Suppress("DEPRECATION")
    fun hasUsageAccess(): Boolean = runCatching {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName
            )
        } else {
            ops.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName
            )
        }
        mode == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    fun load(prefs: Prefs, apps: AppRepository): List<LauncherItem> {
        // Best source: an app explicitly saying "you were part-way through this".
        runCatching { readWatchNext() }.getOrDefault(emptyList())
            .takeIf { it.isNotEmpty() }
            ?.let { return it }

        // Next best: the cards inside app channels. These carry real poster art and
        // often a playback position, and they exist whenever the user has added an
        // app's channel to the Android TV home screen.
        runCatching { readPreviewPrograms() }.getOrDefault(emptyList())
            .takeIf { it.isNotEmpty() }
            ?.let { return it }

        if (hasUsageAccess()) {
            runCatching { recentFromUsage(apps) }.getOrDefault(emptyList())
                .takeIf { it.isNotEmpty() }
                ?.let { return it }
        }

        return ownLaunchHistory(prefs, apps)
    }

    // ------------------------------------------------------------ usage stats

    private fun recentFromUsage(apps: AppRepository): List<LauncherItem> {
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyList()

        val now = System.currentTimeMillis()
        val since = now - TimeUnit.DAYS.toMillis(21)
        // queryUsageStats is the obvious call and it is close to useless here: measured
        // on the device it returned 222 buckets of which only 5 carried a usable
        // lastTimeUsed. The event stream is what actually holds the history, and it is
        // the same data `dumpsys usagestats` prints.
        val lastUsed = HashMap<String, Long>()

        val events = usm.queryEvents(since, now)
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            @Suppress("DEPRECATION")
            if (event.eventType != UsageEvents.Event.MOVE_TO_FOREGROUND) continue
            val pkg = event.packageName ?: continue
            if (event.timeStamp > (lastUsed[pkg] ?: 0L)) lastUsed[pkg] = event.timeStamp
        }

        // Fall back to the buckets if the event stream was pruned away.
        if (lastUsed.isEmpty()) {
            usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, since, now)?.forEach { entry ->
                val pkg = entry.packageName ?: return@forEach
                if (entry.lastTimeUsed > (lastUsed[pkg] ?: 0L)) {
                    lastUsed[pkg] = entry.lastTimeUsed
                }
            }
        }

        val candidates = lastUsed.entries
            .filter { it.key != ctx.packageName && it.key !in EXCLUDED }
            .sortedByDescending { it.value }

        val cards = candidates.mapNotNull { (pkg, last) -> card(pkg, apps, ago(now - last)) }
            .take(MAX_ROWS)

        // Kept: this row is the one thing that silently comes back empty, and the
        // counts say immediately whether it is the permission, the filter, or no data.
        if (cards.isEmpty()) {
            Log.i(
                TAG,
                "continue watching empty: usageAccess=${hasUsageAccess()} " +
                    "events=${lastUsed.size} candidates=${candidates.size}",
            )
        }
        return cards
    }

    private fun ownLaunchHistory(prefs: Prefs, apps: AppRepository): List<LauncherItem> =
        prefs.recentLaunches().mapNotNull { pkg -> card(pkg, apps, "Recently opened") }
            .take(MAX_ROWS)

    private fun card(pkg: String, apps: AppRepository, subtitle: String): LauncherItem? {
        if (!apps.isLaunchable(pkg)) return null
        val label = apps.label(pkg) ?: return null
        return LauncherItem(
            id = pkg,
            label = label,
            kind = ItemKind.CARD,
            packageName = pkg,
            // Banner is the proper 16:9 TV art; icon is a usable stand-in without it.
            artwork = apps.banner(pkg) ?: apps.icon(pkg),
            subtitle = subtitle,
            progress = 0f,
        )
    }

    private fun ago(elapsedMs: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsedMs)
        val hours = TimeUnit.MILLISECONDS.toHours(elapsedMs)
        val days = TimeUnit.MILLISECONDS.toDays(elapsedMs)
        return when {
            minutes < 2 -> "Just now"
            minutes < 60 -> "$minutes min ago"
            hours < 24 -> if (hours == 1L) "1 hour ago" else "$hours hours ago"
            days == 1L -> "Yesterday"
            days < 7 -> "$days days ago"
            else -> "Over a week ago"
        }
    }

    // ------------------------------------------------------------- watch next

    private fun readWatchNext(): List<LauncherItem> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return emptyList()

        data class Row(val item: LauncherItem, val engagedAt: Long)

        val rows = ArrayList<Row>()
        val cursor: Cursor = ctx.contentResolver.query(
            TvContractCompat.WatchNextPrograms.CONTENT_URI, null, null, null, null
        ) ?: return emptyList()

        cursor.use { c ->
            while (c.moveToNext()) {
                val title = c.string(TvContractCompat.WatchNextPrograms.COLUMN_TITLE) ?: continue
                val positionMs = c.long(
                    TvContractCompat.WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS
                )
                val durationMs = c.long(TvContractCompat.WatchNextPrograms.COLUMN_DURATION_MILLIS)
                val progress =
                    if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
                    else 0f

                val season = c.string(
                    TvContractCompat.WatchNextPrograms.COLUMN_SEASON_DISPLAY_NUMBER
                )
                val episode = c.string(
                    TvContractCompat.WatchNextPrograms.COLUMN_EPISODE_DISPLAY_NUMBER
                )
                val remainingMin =
                    if (durationMs > positionMs) ((durationMs - positionMs) / 60_000L).toInt()
                    else 0

                val subtitle = buildList {
                    season?.takeIf { it.isNotBlank() }?.let { add("S$it") }
                    episode?.takeIf { it.isNotBlank() }?.let { add("E$it") }
                    if (remainingMin > 0) add("$remainingMin min left")
                }.joinToString(" · ")

                val intentUri = c.string(TvContractCompat.WatchNextPrograms.COLUMN_INTENT_URI)
                val packageName = c.string(TvContractCompat.WatchNextPrograms.COLUMN_PACKAGE_NAME)

                rows += Row(
                    item = LauncherItem(
                        id = intentUri ?: "$packageName/$title",
                        label = title,
                        kind = ItemKind.CARD,
                        packageName = packageName,
                        artworkUri = c.string(
                            TvContractCompat.WatchNextPrograms.COLUMN_POSTER_ART_URI
                        ),
                        subtitle = subtitle.ifEmpty { null },
                        progress = progress,
                    ),
                    engagedAt = c.long(
                        TvContractCompat.WatchNextPrograms
                            .COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS
                    ),
                )
            }
        }

        return rows.sortedByDescending { it.engagedAt }.take(MAX_ROWS).map { it.item }
    }

    /**
     * Preview programs are the cards an app publishes into its home-screen channel.
     * They share the base program columns with Watch Next, so the same reader works;
     * what differs is that there is no engagement timestamp, so rows come back in
     * provider order with the ones carrying a playback position first — those are the
     * closest thing to "you were watching this".
     */
    private fun readPreviewPrograms(): List<LauncherItem> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return emptyList()

        val cursor = ctx.contentResolver.query(
            TvContractCompat.PreviewPrograms.CONTENT_URI, null, null, null, null
        ) ?: return emptyList()

        val items = ArrayList<Pair<LauncherItem, Float>>()
        cursor.use { c ->
            while (c.moveToNext()) {
                val title = c.string(TvContractCompat.PreviewPrograms.COLUMN_TITLE) ?: continue
                val positionMs = c.long(
                    TvContractCompat.PreviewPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS
                )
                val durationMs = c.long(TvContractCompat.PreviewPrograms.COLUMN_DURATION_MILLIS)
                val progress =
                    if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
                    else 0f

                val art = c.string(TvContractCompat.PreviewPrograms.COLUMN_POSTER_ART_URI)
                    ?: c.string(TvContractCompat.PreviewPrograms.COLUMN_THUMBNAIL_URI)
                // A card with no artwork is worse than the app banner we fall back to.
                if (art.isNullOrBlank()) continue

                val intentUri = c.string(TvContractCompat.PreviewPrograms.COLUMN_INTENT_URI)
                val packageName = c.string(TvContractCompat.PreviewPrograms.COLUMN_PACKAGE_NAME)
                val subtitle = episodeLabel(
                    c.string(TvContractCompat.PreviewPrograms.COLUMN_SEASON_DISPLAY_NUMBER),
                    c.string(TvContractCompat.PreviewPrograms.COLUMN_EPISODE_DISPLAY_NUMBER),
                    positionMs, durationMs,
                ) ?: c.string(TvContractCompat.PreviewPrograms.COLUMN_SHORT_DESCRIPTION)

                items += LauncherItem(
                    id = intentUri ?: "$packageName/$title",
                    label = title,
                    kind = ItemKind.CARD,
                    packageName = packageName,
                    artworkUri = art,
                    subtitle = subtitle,
                    progress = progress,
                ) to progress
            }
        }

        // Part-watched first, then the rest.
        return items.sortedByDescending { it.second }.map { it.first }.take(MAX_ROWS)
    }

    private fun episodeLabel(
        season: String?,
        episode: String?,
        positionMs: Long,
        durationMs: Long,
    ): String? {
        val remainingMin =
            if (durationMs > positionMs) ((durationMs - positionMs) / 60_000L).toInt() else 0
        val parts = buildList {
            season?.takeIf { it.isNotBlank() }?.let { add("S$it") }
            episode?.takeIf { it.isNotBlank() }?.let { add("E$it") }
            if (remainingMin > 0) add("$remainingMin min left")
        }
        return parts.joinToString(" · ").ifEmpty { null }
    }

    /** Column may be absent on a partial provider implementation; tolerate that. */
    private fun Cursor.string(name: String): String? {
        val index = getColumnIndex(name)
        return if (index < 0 || isNull(index)) null else getString(index)
    }

    private fun Cursor.long(name: String): Long {
        val index = getColumnIndex(name)
        return if (index < 0 || isNull(index)) 0L else getLong(index)
    }

    private companion object {
        const val TAG = "EmberXMB"
        const val MAX_ROWS = 8

        /**
         * Launchers and system surfaces are not things you "continue watching".
         * Covers Google TV and stock Android TV as well as Fire OS, so the row behaves
         * on any leanback device rather than only this one.
         */
        val EXCLUDED = setOf(
            // Fire OS
            "com.amazon.tv.launcher",
            "com.amazon.firehomestarter",
            "com.amazon.tv.settings.v2",
            "com.amazon.servicemenu",
            "com.amazon.device.sale.service",
            // Google TV / Android TV
            "com.google.android.tvlauncher",
            "com.google.android.apps.tv.launcherx",
            "com.google.android.tvrecommendations",
            "com.google.android.katniss",
            "com.android.tv.settings",
            // common to both
            "com.android.systemui",
            "com.android.settings",
        )
    }
}
