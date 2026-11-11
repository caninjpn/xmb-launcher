package com.ember.xmb.ui

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.ember.xmb.MenuAction
import com.ember.xmb.R
import com.ember.xmb.XmbViewModel
import com.ember.xmb.model.AppCategory
import com.ember.xmb.model.Mode
import com.ember.xmb.system.NetKind
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun XmbLabel(
    text: String,
    color: Color,
    sizeSp: Float,
    weight: FontWeight = FontWeight.Light,
    letterSpacingEm: Float = 0f,
    modifier: Modifier = Modifier,
) {
    BasicText(
        text = text,
        modifier = modifier,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(
            color = color,
            fontSize = sizeSp.sp,
            fontWeight = weight,
            letterSpacing = letterSpacingEm.em,
        ),
    )
}

@Composable
private fun rememberClock(): State<Calendar> {
    val state = remember { mutableStateOf(Calendar.getInstance()) }
    LaunchedEffect(Unit) {
        while (true) {
            state.value = Calendar.getInstance()
            delay(1_000)
        }
    }
    return state
}

/** Connection + date + clock, in the bordered box the PS3 used. */
@Composable
fun StatusCluster(palette: XmbPalette, net: NetKind, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val now by rememberClock()
    val is24h = remember(context) { DateFormat.is24HourFormat(context) }

    val timeText = remember(now.timeInMillis, is24h) {
        SimpleDateFormat(if (is24h) "HH:mm" else "h:mm a", Locale.getDefault())
            .format(now.time)
    }
    val dateText = remember(now.get(Calendar.DAY_OF_YEAR)) {
        SimpleDateFormat("EEE MMM d", Locale.getDefault()).format(now.time)
    }

    val (icon: ImageVector, label: Int) = when (net) {
        NetKind.WIFI -> XmbIcons.Wifi to R.string.net_wifi
        NetKind.ETHERNET -> XmbIcons.Ethernet to R.string.net_ethernet
        NetKind.OFFLINE -> XmbIcons.Offline to R.string.net_offline
    }
    val netColor = if (net == NetKind.OFFLINE) Color(0xFFFF8F87) else palette.dim

    Row(
        modifier
            .clip(RoundedCornerShape(3.dp))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(3.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .padding(horizontal = 11.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Image(
            painter = rememberVectorPainter(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(netColor),
            modifier = Modifier.size(13.dp),
        )
        XmbLabel(stringResource(label), netColor, 10f)
        Box(
            Modifier
                .width(1.dp)
                .height(14.dp)
                .background(Color.White.copy(alpha = 0.18f))
        )
        XmbLabel(dateText, palette.dim, 10f)
        XmbLabel(timeText, palette.fg, 17f)
    }
}

/**
 * Only drawn when a keyboard or mouse is attached — see
 * [com.ember.xmb.system.rememberHasKeyboardOrMouse]. A remote-only user never sees it.
 */
@Composable
fun Legend(palette: XmbPalette, modifier: Modifier = Modifier) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendItem("◀ ▶", stringResource(R.string.hint_category), palette)
        LegendItem("▲ ▼", stringResource(R.string.hint_item), palette)
        LegendItem("Enter", stringResource(R.string.hint_open), palette)
        LegendItem(stringResource(R.string.hint_hold_select), stringResource(R.string.hint_menu), palette)
    }
}

@Composable
private fun LegendItem(key: String, label: String, palette: XmbPalette) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        XmbLabel(key, palette.dim, 9f, FontWeight.SemiBold)
        XmbLabel(label, palette.faint, 9f)
    }
}

/** Context menu, theme picker and toast. */
@Composable
internal fun Overlays(vm: XmbViewModel, m: Metrics, palette: XmbPalette) {
    val mode = vm.mode

    if (mode == Mode.MENU || mode == Mode.THEME || mode == Mode.CATEGORY) {
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { vm.closeOverlays() } }
        )
    }

    if (mode == Mode.MENU) ContextMenu(vm, m, palette)
    if (mode == Mode.THEME) ThemePicker(vm, palette)
    if (mode == Mode.CATEGORY) CategoryPicker(vm, palette)

    vm.toast?.let { message ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(palette.panel.copy(alpha = 0.92f))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(9.dp))
                    .padding(horizontal = 22.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                XmbLabel(
                    message.kicker.uppercase(), palette.dim, 9f,
                    FontWeight.Normal, letterSpacingEm = 0.16f,
                )
                Spacer(Modifier.height(4.dp))
                XmbLabel(message.body, palette.fg, 15f)
            }
        }
    }
}

/**
 * Deliberately Android TV's own look — a light panel with blue glyphs — rather than
 * the XMB shell's. That is the convention Fire TV users already recognise for this
 * menu, and the contrast reads clearly over a dark animated background.
 */
@Composable
private fun ContextMenu(vm: XmbViewModel, m: Metrics, palette: XmbPalette) {
    val selectedIsCard = vm.category?.cards == true
    val iconWidth = if (selectedIsCard) m.cardWidth else m.itemIcon
    val gapBelow = if (selectedIsCard) m.gapBelowCards else m.gapBelowRows

    Row(
        Modifier.offset(
            x = m.anchorX + iconWidth / 2f + 14.dp,
            y = (m.axisY + gapBelow - 26.dp).coerceAtLeast(10.dp),
        ),
        verticalAlignment = Alignment.Top,
    ) {
        Canvas(Modifier.size(7.dp, 16.dp).offset(y = 12.dp)) {
            val tip = Path().apply {
                moveTo(size.width, 0f)
                lineTo(0f, size.height / 2f)
                lineTo(size.width, size.height)
                close()
            }
            drawPath(tip, Color(0xFFFBFBFB))
        }

        Column(
            Modifier
                // IntrinsicSize.Max sizes the panel to its widest row. Without a
                // bounded width here the rows' fillMaxWidth() expands to the screen.
                .width(IntrinsicSize.Max)
                .widthIn(min = 180.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFFFBFBFB))
                .padding(vertical = 4.dp)
        ) {
            vm.menuRows.forEachIndexed { index, row ->
                val selected = index == vm.menuIndex
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (selected) Color(0xFFE4E4E4) else Color.Transparent)
                        .pointerInput(index, row.enabled) {
                            if (row.enabled) detectTapGestures { vm.runMenu(index) }
                        }
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(13.dp),
                ) {
                    val alpha = if (row.enabled) 1f else 0.36f
                    Image(
                        painter = rememberVectorPainter(iconFor(row.action)),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(
                            Color(0xFF1B74E4).copy(alpha = alpha)
                        ),
                        modifier = Modifier.size(16.dp),
                    )
                    XmbLabel(
                        row.label,
                        Color(0xFF1F1F1F).copy(alpha = alpha),
                        12f,
                        FontWeight.Normal,
                    )
                }
            }
        }
    }
}

private fun iconFor(action: MenuAction): ImageVector = when (action) {
    MenuAction.OPEN -> XmbIcons.MenuOpen
    MenuAction.MOVE -> XmbIcons.MenuMove
    MenuAction.CATEGORY -> XmbIcons.MenuCategory
    MenuAction.FAVOURITE -> XmbIcons.Heart
    MenuAction.INFO -> XmbIcons.MenuInfo
    MenuAction.UNINSTALL -> XmbIcons.MenuUninstall
    MenuAction.REARRANGE -> XmbIcons.MenuRearrange
}

/** Where an app also appears, beyond the Apps list. */
@Composable
private fun CategoryPicker(vm: XmbViewModel, palette: XmbPalette) {
    val label = vm.selectedItem?.label.orEmpty()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                // Explicit width, not widthIn: the rows below use fillMaxWidth(), so an
                // unbounded column lets them expand to the whole screen.
                .width(260.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(palette.panel.copy(alpha = 0.95f))
                .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(7.dp))
                .padding(vertical = 8.dp)
        ) {
            XmbLabel(
                stringResource(R.string.category_title, label),
                palette.dim, 10f, FontWeight.Normal, 0.14f,
                Modifier.padding(start = 18.dp, end = 18.dp, bottom = 7.dp),
            )
            AppCategory.ASSIGNABLE.forEachIndexed { index, key ->
                val focused = index == vm.catPick
                val current = key == vm.selectedItem?.assigned
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            if (focused) Color.White.copy(alpha = 0.14f) else Color.Transparent
                        )
                        .pointerInput(index) {
                            detectTapGestures {
                                vm.hoverCategory(index)
                                vm.applyCategoryPick()
                            }
                        }
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Image(
                        painter = painterResource(XmbIcons.categoryRes(AppCategory.icon(key))),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(
                            if (focused) palette.fg else palette.dim
                        ),
                        modifier = Modifier.size(17.dp),
                    )
                    XmbLabel(
                        vm.categoryLabel(key),
                        if (focused) palette.fg else palette.dim,
                        12f,
                        modifier = Modifier.widthIn(min = 110.dp),
                    )
                    if (current) {
                        Image(
                            painter = rememberVectorPainter(XmbIcons.Check),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(palette.accent),
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemePicker(vm: XmbViewModel, palette: XmbPalette) {
    val listState = rememberLazyListState()
    LaunchedEffect(vm.themePick) {
        listState.animateScrollToItem(vm.themePick.coerceAtLeast(0))
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                // Same reason as the category picker, and a LazyColumn cannot be
                // measured with IntrinsicSize, so the width is stated outright.
                .width(300.dp)
                .heightIn(max = 300.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(palette.panel.copy(alpha = 0.95f))
                .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(7.dp))
                .padding(vertical = 8.dp)
        ) {
            XmbLabel(
                stringResource(R.string.theme_title).uppercase(),
                palette.dim, 9f, FontWeight.Normal, 0.2f,
                Modifier.padding(start = 18.dp, bottom = 7.dp),
            )
            LazyColumn(state = listState) {
                itemsIndexed(XmbThemes.options) { index, option ->
                    val focused = index == vm.themePick
                    val current = index == vm.themeIndex
                    val hue = XmbThemes.hueForOption(index)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(
                                if (focused) Color.White.copy(alpha = 0.14f) else Color.Transparent
                            )
                            .pointerInput(index) {
                                detectTapGestures {
                                    vm.hoverTheme(index)
                                    vm.applyThemePick()
                                }
                            }
                            .padding(horizontal = 18.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            Modifier
                                .size(13.dp)
                                .clip(RoundedCornerShape(50))
                                .background(hsl(hue, 0.85f, 0.58f))
                        )
                        XmbLabel(
                            option.name,
                            if (focused) palette.fg else palette.dim,
                            12f,
                            modifier = Modifier.widthIn(min = 120.dp),
                        )
                        option.month?.let { XmbLabel(it, palette.faint, 9f) }
                        if (current) {
                            Image(
                                painter = rememberVectorPainter(XmbIcons.Check),
                                contentDescription = null,
                                colorFilter = ColorFilter.tint(palette.accent),
                                modifier = Modifier.size(12.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Shared sizing helper for anything that needs the scale factor outside Metrics. */
internal fun Dp.scaled(k: Float): Dp = this * k
