package com.ember.xmb.data

import android.content.Context

/**
 * Launcher-owned state.
 *
 * Favourites and item order are NOT system concepts — Android provides no API for
 * either. Every TV launcher keeps its own, and so do we.
 */
class Prefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("ember_xmb", Context.MODE_PRIVATE)

    var themeIndex: Int
        get() = sp.getInt(KEY_THEME, 0)
        set(value) = sp.edit().putInt(KEY_THEME, value).apply()

    /** Always returns a copy: the set handed back by SharedPreferences must not be mutated. */
    fun favourites(): Set<String> =
        sp.getStringSet(KEY_FAVS, emptySet())?.toSet() ?: emptySet()

    /** @return the new favourite state. */
    fun toggleFavourite(id: String): Boolean {
        val set = favourites().toMutableSet()
        val nowFavourite = if (set.contains(id)) {
            set.remove(id); false
        } else {
            set.add(id); true
        }
        sp.edit().putStringSet(KEY_FAVS, set).apply()
        return nowFavourite
    }

    fun order(categoryKey: String): List<String> = sp.getString(orderKey(categoryKey), null)
        ?.split('\n')?.filter { it.isNotEmpty() } ?: emptyList()

    fun setOrder(categoryKey: String, ids: List<String>) =
        sp.edit().putString(orderKey(categoryKey), ids.joinToString("\n")).apply()

    /**
     * The order of the category row itself, as keys. Empty until the user rearranges it,
     * which is what lets the default arrangement change in a later version for anyone
     * who has never touched it.
     */
    fun categoryOrder(): List<String> = sp.getString(KEY_CAT_ORDER, null)
        ?.split('\n')?.filter { it.isNotEmpty() } ?: emptyList()

    fun setCategoryOrder(keys: List<String>) =
        sp.edit().putString(KEY_CAT_ORDER, keys.joinToString("\n")).apply()

    /** Feeds the Continue Watching fallback when no Watch Next provider is available. */
    fun noteLaunched(id: String) {
        val recents = recentLaunches().toMutableList()
        recents.remove(id)
        recents.add(0, id)
        while (recents.size > MAX_RECENTS) recents.removeAt(recents.lastIndex)
        sp.edit().putString(KEY_RECENT, recents.joinToString("\n")).apply()
    }

    fun recentLaunches(): List<String> = sp.getString(KEY_RECENT, null)
        ?.split('\n')?.filter { it.isNotEmpty() } ?: emptyList()

    /**
     * Manual category assignments, which always beat the auto-detected one.
     * Stored as `package=category` lines — small enough that a map in one string
     * beats a database here.
     */
    fun categoryOverrides(): Map<String, String> =
        sp.getString(KEY_CATEGORIES, null)
            ?.split('\n')
            ?.mapNotNull { line ->
                val at = line.indexOf('=')
                if (at <= 0) null else line.substring(0, at) to line.substring(at + 1)
            }?.toMap() ?: emptyMap()

    fun setCategory(packageName: String, category: String) {
        val merged = categoryOverrides().toMutableMap()
        merged[packageName] = category
        sp.edit()
            .putString(KEY_CATEGORIES, merged.entries.joinToString("\n") { "${it.key}=${it.value}" })
            .apply()
    }

    private fun orderKey(key: String) = "order_$key"

    private companion object {
        const val KEY_THEME = "theme_index"
        const val KEY_FAVS = "favourites"
        const val KEY_RECENT = "recent_launches"
        const val KEY_CATEGORIES = "app_categories"
        const val KEY_CAT_ORDER = "category_order"
        const val MAX_RECENTS = 12
    }
}
