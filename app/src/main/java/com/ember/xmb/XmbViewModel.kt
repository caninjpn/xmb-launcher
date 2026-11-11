package com.ember.xmb

import android.app.Application
import android.content.Context
import android.os.UserManager
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ember.xmb.data.AppRepository
import com.ember.xmb.data.ArtLoader
import com.ember.xmb.data.ContinueWatchingRepository
import com.ember.xmb.data.Prefs
import com.ember.xmb.model.AppCategory
import com.ember.xmb.model.Category
import com.ember.xmb.model.CategoryIcon
import com.ember.xmb.model.ItemAction
import com.ember.xmb.model.ItemKind
import com.ember.xmb.model.LauncherItem
import com.ember.xmb.model.Mode
import com.ember.xmb.model.XmbGlyph
import com.ember.xmb.ui.XmbThemes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class MenuAction { OPEN, MOVE, CATEGORY, FAVOURITE, INFO, UNINSTALL, REARRANGE }

data class MenuRow(val action: MenuAction, val label: String, val enabled: Boolean)

data class ToastMessage(val kicker: String, val body: String)

class XmbViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = Prefs(app)
    private val appsRepo = AppRepository(app)
    private val continueRepo = ContinueWatchingRepository(app)
    private val art = ArtLoader(app)

    var categories by mutableStateOf<List<Category>>(emptyList())
        private set
    var catIndex by mutableIntStateOf(0)
        private set
    var mode by mutableStateOf(Mode.NAV)
        private set
    var menuIndex by mutableIntStateOf(0)
        private set
    var menuRows by mutableStateOf<List<MenuRow>>(emptyList())
        private set
    var themeIndex by mutableIntStateOf(prefs.themeIndex)
        private set
    var themePick by mutableIntStateOf(prefs.themeIndex)
        private set
    var catPick by mutableIntStateOf(0)
        private set
    var toast by mutableStateOf<ToastMessage?>(null)
        private set

    /** Selected item per category key, so each column remembers where you were. */
    private val selections = mutableStateMapOf<String, Int>()

    private var toastJob: Job? = null

    /** refresh() also runs on every onResume, so only the first load picks the landing category. */
    private var firstLoad = true

    val category: Category? get() = categories.getOrNull(catIndex)
    val selIndex: Int get() = selections[category?.key] ?: 0

    fun selectionOf(key: String): Int = selections[key] ?: 0
    val selectedItem: LauncherItem? get() = category?.items?.getOrNull(selIndex)

    init {
        refresh()
    }

    // ---------------------------------------------------------------- loading

    fun refresh() {
        viewModelScope.launch {
            // Remember the selected item by identity, not by index. Continue Watching
            // re-sorts by recency, so coming back from an app moves that app to the
            // front — holding the old index would silently point at a different app,
            // which reads as the cursor jumping to the top.
            val anchors = categories.associate { cat ->
                cat.key to cat.items.getOrNull(selections[cat.key] ?: 0)?.id
            }

            val loaded = withContext(Dispatchers.IO) { appsRepo.load(prefs) }
            val continueItems = withContext(Dispatchers.IO) { continueRepo.load(prefs, appsRepo) }
            categories = buildCategories(loaded, continueItems)
            restoreSelections(anchors)
            if (firstLoad) {
                firstLoad = false
                catIndex = categories.indexOfFirst { it.key == Category.KEY_CONTINUE }
                    .coerceAtLeast(0)
            } else if (catIndex !in categories.indices) {
                catIndex = 0
            }
            resolveArtwork()
        }
    }

    private fun buildCategories(
        loaded: AppRepository.Loaded,
        continueItems: List<LauncherItem>,
    ): List<Category> {
        val res = getApplication<Application>()

        fun typed(key: String, titleRes: Int, icon: CategoryIcon) = Category(
            key = key,
            title = res.getString(titleRes),
            icon = icon,
            items = loaded.byCategory[key].orEmpty(),
            contextMenu = true,
        )

        // Apps sits immediately left of Settings and lists everything; the typed
        // categories above it are assignments, not a partition.
        return applyCategoryOrder(listOf(
            usersCategory(),
            Category(
                key = Category.KEY_CONTINUE,
                title = res.getString(R.string.cat_continue),
                icon = CategoryIcon.CONTINUE,
                items = continueItems,
                cards = true,
            ),
            typed(AppCategory.GAMES, R.string.cat_games, CategoryIcon.GAMES),
            typed(AppCategory.VIDEO, R.string.cat_video, CategoryIcon.VIDEO),
            typed(AppCategory.MUSIC, R.string.cat_music, CategoryIcon.MUSIC),
            typed(AppCategory.PHOTO, R.string.cat_photo, CategoryIcon.PHOTO),
            Category(
                key = Category.KEY_APPS,
                title = res.getString(R.string.cat_apps),
                icon = CategoryIcon.APPS,
                items = loaded.all,
                contextMenu = true,
            ),
            settingsCategory(),
        ))
    }

    /**
     * Applies the user's stored arrangement of the category row.
     *
     * Keys the stored list does not mention keep their default order among themselves
     * and land at the end, so a category added in a later version shows up rather than
     * disappearing for anyone who has ever rearranged the row. Stored keys that no
     * longer exist are dropped on the way through.
     */
    private fun applyCategoryOrder(defaults: List<Category>): List<Category> {
        val stored = prefs.categoryOrder()
        if (stored.isEmpty()) return defaults
        val byKey = defaults.associateBy { it.key }
        return stored.mapNotNull(byKey::get) + defaults.filter { it.key !in stored }
    }

    /** Localised name for an [AppCategory] key, used by the picker and its toast. */
    fun categoryLabel(key: String): String {
        val res = getApplication<Application>()
        return when (key) {
            AppCategory.GAMES -> res.getString(R.string.cat_games)
            AppCategory.VIDEO -> res.getString(R.string.cat_video)
            AppCategory.MUSIC -> res.getString(R.string.cat_music)
            AppCategory.PHOTO -> res.getString(R.string.cat_photo)
            else -> res.getString(R.string.cat_apps)
        }
    }

    /**
     * Fire TV profiles are not reachable from a third-party launcher — there is no
     * public API to enumerate or switch them. We show the current profile name, which
     * UserManager will give us, and a route into system settings. Nothing invented.
     */
    private fun usersCategory(): Category {
        val app = getApplication<Application>()
        val userManager = app.getSystemService(Context.USER_SERVICE) as? UserManager
        val name = runCatching { userManager?.userName }
            .getOrNull()?.takeIf { it.isNotBlank() } ?: "Current profile"

        return Category(
            key = Category.KEY_USERS,
            title = app.getString(R.string.cat_users),
            icon = CategoryIcon.USERS,
            items = listOf(
                LauncherItem(
                    id = "user:current",
                    label = name,
                    kind = ItemKind.ACTION,
                    glyph = XmbGlyph.PROFILE,
                    accent = 0xFF3B6EF0,
                ),
                LauncherItem(
                    id = "user:settings",
                    label = app.getString(R.string.settings_device),
                    kind = ItemKind.ACTION,
                    action = ItemAction.SystemSettings(Settings.ACTION_SETTINGS),
                    glyph = XmbGlyph.DEVICE,
                    accent = 0xFF5A6373,
                ),
            ),
        )
    }

    private fun settingsCategory(): Category {
        val app = getApplication<Application>()
        fun entry(id: String, label: String, action: ItemAction, accent: Long, glyph: XmbGlyph) =
            LauncherItem(
                id = id, label = label, kind = ItemKind.ACTION,
                action = action, glyph = glyph, accent = accent,
            )

        return Category(
            key = Category.KEY_SETTINGS,
            title = app.getString(R.string.cat_settings),
            icon = CategoryIcon.SETTINGS,
            items = listOf(
                entry(
                    "set:network", app.getString(R.string.settings_network),
                    ItemAction.SystemSettings(Settings.ACTION_WIFI_SETTINGS),
                    0xFF2F80ED, XmbGlyph.WIFI,
                ),
                entry(
                    "set:theme", app.getString(R.string.settings_theme),
                    ItemAction.ThemePicker,
                    0xFF9AA7BD, XmbGlyph.THEME,
                ),
                entry(
                    "set:display", app.getString(R.string.settings_display),
                    ItemAction.SystemSettings(Settings.ACTION_DISPLAY_SETTINGS),
                    0xFF8E44AD, XmbGlyph.DISPLAY,
                ),
                entry(
                    "set:apps", app.getString(R.string.settings_apps),
                    ItemAction.SystemSettings(Settings.ACTION_APPLICATION_SETTINGS),
                    0xFF16A085, XmbGlyph.APPS,
                ),
                entry(
                    "set:device", app.getString(R.string.settings_device),
                    ItemAction.SystemSettings(Settings.ACTION_SETTINGS),
                    0xFF596273, XmbGlyph.DEVICE,
                ),
                entry(
                    "set:dev", app.getString(R.string.settings_developer),
                    ItemAction.SystemSettings(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
                    0xFFD35400, XmbGlyph.DEVELOPER,
                ),
                entry(
                    "set:about", app.getString(R.string.settings_about),
                    ItemAction.SystemSettings(Settings.ACTION_DEVICE_INFO_SETTINGS),
                    0xFF4A5260, XmbGlyph.ABOUT,
                ),
            ),
        )
    }

    private fun resolveArtwork() {
        viewModelScope.launch {
            val target = categories.firstOrNull { it.key == Category.KEY_CONTINUE } ?: return@launch
            if (target.items.none { it.artwork == null && it.artworkUri != null }) return@launch

            val resolved = target.items.map { item ->
                val uri = item.artworkUri
                if (item.artwork != null || uri == null) item
                else item.copy(artwork = withContext(Dispatchers.IO) { art.load(uri) })
            }
            updateCategory(Category.KEY_CONTINUE) { it.copy(items = resolved) }
        }
    }

    // ------------------------------------------------------------ navigation

    fun onDirection(dx: Int, dy: Int): Boolean {
        when (mode) {
            Mode.NAV -> {
                if (dx != 0) {
                    setCategory(catIndex + dx); return true
                }
                if (dy != 0) {
                    stepSelection(dy); return true
                }
            }

            Mode.MENU -> {
                if (dy != 0) {
                    stepMenu(dy); return true
                }
                if (dx < 0) {
                    mode = Mode.NAV; return true
                }
            }

            Mode.MOVE -> if (dy != 0) {
                reorder(dy); return true
            }

            Mode.REARRANGE -> if (dx != 0) {
                moveCategory(dx); return true
            }

            Mode.THEME -> {
                if (dy != 0) {
                    themePick = wrap(themePick + dy, XmbThemes.options.size); return true
                }
                if (dx < 0) {
                    mode = Mode.NAV; return true
                }
            }

            Mode.CATEGORY -> {
                if (dy != 0) {
                    catPick = wrap(catPick + dy, AppCategory.ASSIGNABLE.size); return true
                }
                if (dx < 0) {
                    mode = Mode.NAV; return true
                }
            }
        }
        return false
    }

    fun onSelect(): Boolean {
        when (mode) {
            Mode.NAV -> activate()
            Mode.MENU -> runMenu(menuIndex)
            Mode.MOVE -> {
                mode = Mode.NAV
                selectedItem?.let { showToast("Moved", it.label) }
            }
            Mode.REARRANGE -> {
                mode = Mode.NAV
                category?.let { showToast("Moved", it.title) }
            }
            Mode.THEME -> applyThemePick()
            Mode.CATEGORY -> applyCategoryPick()
        }
        return true
    }

    /**
     * Long-press Select. App columns get the full item menu; everywhere else the menu
     * still opens, holding only the actions that apply to the category itself. It used
     * to do nothing at all outside app columns, which left no way to reach Rearrange
     * from Continue Watching or Users.
     */
    fun onLongSelect(): Boolean {
        if (mode != Mode.NAV) return false
        val cat = category ?: return false
        openMenu(cat)
        return true
    }

    /** A launcher swallows Back — there is nothing behind HOME. */
    fun onBack(): Boolean {
        if (mode != Mode.NAV) mode = Mode.NAV
        return true
    }

    private fun setCategory(index: Int) {
        if (categories.isEmpty()) return
        catIndex = wrap(index, categories.size)
        mode = Mode.NAV
    }

    private fun stepSelection(delta: Int) {
        val cat = category ?: return
        if (cat.items.isEmpty()) return
        selections[cat.key] = wrap(selIndex + delta, cat.items.size)
    }

    fun selectCategory(index: Int) = setCategory(index)

    fun selectItem(index: Int) {
        val cat = category ?: return
        if (index in cat.items.indices) selections[cat.key] = index
    }

    /** Put the cursor back on the same item it was on, wherever it moved to. */
    private fun restoreSelections(anchors: Map<String, String?>) {
        categories.forEach { cat ->
            if (cat.items.isEmpty()) {
                selections[cat.key] = 0
                return@forEach
            }
            val anchorId = anchors[cat.key]
            val found = if (anchorId == null) -1 else cat.items.indexOfFirst { it.id == anchorId }
            selections[cat.key] = if (found >= 0) {
                found
            } else {
                (selections[cat.key] ?: 0).coerceIn(0, cat.items.lastIndex)
            }
        }
    }

    // ---------------------------------------------------------------- actions

    private fun activate() {
        val item = selectedItem ?: return
        when (val action = item.action) {
            is ItemAction.ThemePicker -> {
                themePick = themeIndex
                mode = Mode.THEME
            }

            is ItemAction.SystemSettings -> appsRepo.openSystemSettings(action.action)

            null -> launchItem(item)
        }
    }

    private fun launchItem(item: LauncherItem) {
        // A Watch Next row carries its own deep link; prefer it over the bare package.
        if (item.kind == ItemKind.CARD && item.id.startsWith("intent:")) {
            if (appsRepo.launchIntentUri(item.id)) {
                item.packageName?.let(prefs::noteLaunched)
                return
            }
        }
        val pkg = item.packageName
        if (pkg == null) {
            showToast("Not launchable", item.label)
            return
        }
        if (appsRepo.launch(pkg)) prefs.noteLaunched(pkg)
        else showToast("Could not open", item.label)
    }

    // ------------------------------------------------------------ context menu

    private fun openMenu(cat: Category) {
        val app = getApplication<Application>()
        // Null on a column with no item menu — Continue Watching, Users, Settings, or
        // simply an empty category. The row-level action below still applies there.
        val item = selectedItem?.takeIf { cat.contextMenu && cat.items.isNotEmpty() }

        menuRows = buildList {
            if (item != null) {
                add(MenuRow(MenuAction.OPEN, app.getString(R.string.menu_open), true))
                add(MenuRow(MenuAction.MOVE, app.getString(R.string.menu_move), true))
                add(
                    MenuRow(
                        MenuAction.CATEGORY,
                        app.getString(R.string.menu_category),
                        item.packageName != null,
                    )
                )
                add(
                    MenuRow(
                        MenuAction.FAVOURITE,
                        app.getString(
                            if (item.favourite) R.string.menu_fav_remove
                            else R.string.menu_fav_add
                        ),
                        true,
                    )
                )
                add(
                    MenuRow(
                        MenuAction.INFO,
                        app.getString(R.string.menu_info),
                        item.packageName != null,
                    )
                )
                // ACTION_DELETE is refused for system packages; do not pretend otherwise.
                add(
                    MenuRow(
                        MenuAction.UNINSTALL,
                        app.getString(R.string.menu_uninstall),
                        item.packageName != null && !item.isSystem,
                    )
                )
            }
            add(MenuRow(MenuAction.REARRANGE, app.getString(R.string.menu_rearrange), true))
        }
        menuIndex = 0
        mode = Mode.MENU
    }

    private fun stepMenu(delta: Int) {
        if (menuRows.isEmpty()) return
        var next = menuIndex
        repeat(menuRows.size) {
            next = wrap(next + delta, menuRows.size)
            if (menuRows[next].enabled) {
                menuIndex = next
                return
            }
        }
    }

    fun runMenu(index: Int) {
        val row = menuRows.getOrNull(index) ?: return
        if (!row.enabled) return
        mode = Mode.NAV

        // Acts on the row, not on an item, so it runs before the item is resolved —
        // it is the one menu action reachable from a category with no items.
        if (row.action == MenuAction.REARRANGE) {
            val app = getApplication<Application>()
            mode = Mode.REARRANGE
            showToast(
                app.getString(R.string.menu_rearrange),
                app.getString(R.string.rearrange_toast),
            )
            return
        }

        val item = selectedItem ?: return

        when (row.action) {
            MenuAction.REARRANGE -> Unit   // handled above

            MenuAction.OPEN -> launchItem(item)

            MenuAction.MOVE -> {
                mode = Mode.MOVE
                showToast(
                    getApplication<Application>().getString(R.string.menu_move),
                    getApplication<Application>().getString(R.string.move_toast),
                )
            }

            MenuAction.CATEGORY -> {
                catPick = AppCategory.ASSIGNABLE.indexOf(item.assigned).coerceAtLeast(0)
                mode = Mode.CATEGORY
            }

            MenuAction.FAVOURITE -> toggleFavourite(item)

            MenuAction.INFO -> item.packageName?.let(appsRepo::openAppInfo)

            MenuAction.UNINSTALL -> item.packageName?.let(appsRepo::requestUninstall)
        }
    }

    fun hoverMenu(index: Int) {
        if (menuRows.getOrNull(index)?.enabled == true) menuIndex = index
    }

    fun closeOverlays() {
        mode = Mode.NAV
    }

    private fun toggleFavourite(item: LauncherItem) {
        val cat = category ?: return
        val nowFavourite = prefs.toggleFavourite(item.id)
        val index = selIndex
        updateCategory(cat.key) { c ->
            c.copy(
                items = c.items.mapIndexed { i, existing ->
                    if (i == index) existing.copy(favourite = nowFavourite) else existing
                }
            )
        }
        showToast(
            if (nowFavourite) "Added to favourites" else "Removed from favourites",
            item.label,
        )
    }

    private fun reorder(delta: Int) {
        val cat = category ?: return
        val from = selIndex
        val to = from + delta
        if (to !in cat.items.indices) return

        val items = cat.items.toMutableList()
        val moved = items.removeAt(from)
        items.add(to, moved)

        updateCategory(cat.key) { it.copy(items = items) }
        selections[cat.key] = to
        prefs.setOrder(cat.key, items.map { it.id })
    }

    /**
     * Moves the whole category one slot along the row, and persists the arrangement
     * immediately so it survives being killed mid-move.
     *
     * The cursor travels with the category rather than staying at the slot, so holding
     * the direction keeps pushing the same category along. Stops at the ends instead of
     * wrapping: wrapping would fling a category the length of the row on one keypress.
     * Per-category selections are keyed by [Category.key], so nothing needs restoring.
     */
    private fun moveCategory(delta: Int) {
        val from = catIndex
        val to = from + delta
        if (to !in categories.indices) return

        val next = categories.toMutableList()
        next.add(to, next.removeAt(from))
        categories = next
        catIndex = to
        prefs.setCategoryOrder(next.map { it.key })
    }

    // ----------------------------------------------------------------- theme

    fun hoverCategory(index: Int) {
        if (index in AppCategory.ASSIGNABLE.indices) catPick = index
    }

    /**
     * Stores the override and reloads. The override always beats auto-detection, so
     * a move is permanent until the user moves it again.
     */
    fun applyCategoryPick() {
        val item = selectedItem
        val pkg = item?.packageName
        val target = AppCategory.ASSIGNABLE.getOrNull(catPick)
        mode = Mode.NAV
        if (item == null || pkg == null || target == null) return

        prefs.setCategory(pkg, target)
        showToast("Moved to ${categoryLabel(target)}", item.label)
        refresh()
    }

    fun hoverTheme(index: Int) {
        if (index in XmbThemes.options.indices) themePick = index
    }

    fun applyThemePick() {
        themeIndex = themePick
        prefs.themeIndex = themeIndex
        showToast(
            getApplication<Application>().getString(R.string.theme_title),
            XmbThemes.options[themeIndex].name,
        )
    }

    // ----------------------------------------------------------------- utils

    private fun updateCategory(key: String, transform: (Category) -> Category) {
        categories = categories.map { if (it.key == key) transform(it) else it }
    }

    private fun showToast(kicker: String, body: String) {
        toastJob?.cancel()
        toast = ToastMessage(kicker, body)
        toastJob = viewModelScope.launch {
            delay(1400)
            toast = null
        }
    }

    private fun wrap(value: Int, size: Int): Int =
        if (size <= 0) 0 else ((value % size) + size) % size
}
