package com.ember.xmb.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ember.xmb.R
import com.ember.xmb.XmbViewModel
import com.ember.xmb.model.Category
import com.ember.xmb.model.ItemKind
import com.ember.xmb.model.LauncherItem
import com.ember.xmb.model.Mode
import com.ember.xmb.system.NetKind

internal val XmbEase = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)

/** How far either side of the selection the column is actually composed. */
private const val WINDOW_ABOVE = 6
private const val WINDOW_BELOW = 16

/**
 * Geometry of the cross.
 *
 * The column runs THROUGH the category row: items before the selection ride above it
 * in compressed form, the selection sits in the gap below it, and the rest stride
 * down at full spacing. [gapBelowCards] is measured from the label rather than
 * guessed, because a 16:9 card is tall enough to collide with it otherwise.
 */
internal data class Metrics(
    val k: Float,
    val anchorX: Dp,
    val axisY: Dp,
    val slot: Dp,
    val catIcon: Dp,
    val itemIcon: Dp,
    val upIcon: Dp,
    val rowHeight: Dp,
    val upHeight: Dp,
    val cardHeight: Dp,
    val gapAbove: Dp,
    val gapBelowRows: Dp,
    val gapBelowCards: Dp,
    val strideUp: Dp,
    val strideRows: Dp,
    val strideCards: Dp,
) {
    val cardWidth: Dp get() = cardHeight * 16f / 9f
    val upCardWidth: Dp get() = upIcon * 16f / 9f
}

@Composable
internal fun rememberMetrics(width: Dp, height: Dp): Metrics = remember(width, height) {
    // A 1080p TV reports roughly 960 x 540 dp. Everything scales from that.
    val k = (height / 540.dp).coerceIn(0.75f, 1.45f)
    val cardH = (height * 0.19f).coerceIn(74.dp, 120.dp)
    // category icon half + gap + label line, then air
    val labelClearance = 24.dp * k + 18.dp * k
    Metrics(
        k = k,
        anchorX = (width * 0.185f).coerceAtLeast(70.dp),
        axisY = (height * 0.26f).coerceAtLeast(76.dp),
        slot = (width * 0.125f).coerceIn(68.dp, 112.dp),
        catIcon = 38.dp * k,
        itemIcon = 36.dp * k,
        upIcon = 24.dp * k,
        rowHeight = 38.dp * k,
        upHeight = 26.dp * k,
        cardHeight = cardH,
        gapAbove = 56.dp * k,
        gapBelowRows = 68.dp * k,
        gapBelowCards = labelClearance + 14.dp * k + cardH / 2f,
        strideUp = 30.dp * k,
        strideRows = 58.dp * k,
        strideCards = cardH + 15.dp * k,
    )
}

@Composable
fun XmbScreen(
    vm: XmbViewModel,
    palette: XmbPalette,
    net: NetKind,
    showLegend: Boolean,
    onAxisFraction: (Float) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val m = rememberMetrics(maxWidth, maxHeight)
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()

        // The shader centres the ribbon on the same axis the cross uses. Sent as a
        // fraction of height, because the GL surface renders smaller than the layout.
        val axisFraction = m.axisY / maxHeight
        LaunchedEffect(axisFraction) { onAxisFraction(axisFraction) }

        // Depth scrim: pushes the GL shimmer back so the cross reads as foreground.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colorStops = arrayOf(
                            0f to palette.scrim.copy(alpha = 0.62f),
                            0.56f to palette.scrim.copy(alpha = 0.26f),
                            1f to Color.Transparent,
                        ),
                        center = Offset(widthPx * 0.24f, heightPx * 0.42f),
                        radius = maxOf(widthPx, heightPx) * 0.78f,
                    )
                )
        )

        val categories = vm.categories
        if (categories.isEmpty()) return@BoxWithConstraints

        // --- item column, faded at top and bottom, cross-faded between categories ---
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.06f to Color.Black.copy(alpha = 0.35f),
                            0.14f to Color.Black,
                            0.92f to Color.Black,
                            1f to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                }
        ) {
            // Crossfade gives the category change a pure 300ms fade. Position
            // animations inside start AT their target on first composition, so
            // nothing flies in from the top.
            Crossfade(
                targetState = vm.catIndex,
                animationSpec = tween(300),
                label = "category",
            ) { index ->
                categories.getOrNull(index)?.let { cat ->
                    ItemColumn(
                        category = cat,
                        // Per-category, so the outgoing column holds still while it fades.
                        selIndex = vm.selectionOf(cat.key),
                        moving = vm.mode == Mode.MOVE && index == vm.catIndex,
                        m = m,
                        palette = palette,
                        onClick = { i ->
                            if (i == vm.selIndex) vm.onSelect() else vm.selectItem(i)
                        },
                        onLongClick = { i ->
                            vm.selectItem(i)
                            vm.onLongSelect()
                        },
                    )
                }
            }
        }

        // --- category row ---
        CategoryRow(vm = vm, m = m, palette = palette)

        // --- status cluster ---
        StatusCluster(
            palette = palette,
            net = net,
            modifier = Modifier.align(Alignment.TopEnd).padding(18.dp),
        )

        if (showLegend) {
            Legend(
                palette = palette,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
            )
        }

        Overlays(vm = vm, m = m, palette = palette)
    }
}

@Composable
private fun CategoryRow(vm: XmbViewModel, m: Metrics, palette: XmbPalette) {
    val targetX = m.anchorX - (m.slot * vm.catIndex + m.slot / 2f)
    val x by animateDpAsState(targetX, tween(420, easing = XmbEase), label = "row")

    Row(
        Modifier.offset(x = x, y = m.axisY - m.catIcon / 2f),
        verticalAlignment = Alignment.Top,
    ) {
        vm.categories.forEachIndexed { index, category ->
            val selected = index == vm.catIndex
            val scale by animateFloatAsState(
                if (selected) 1.16f else 0.80f, tween(400, easing = XmbEase), label = "scale"
            )
            val alpha by animateFloatAsState(
                if (selected) 1f else 0.40f, tween(340), label = "alpha"
            )
            Column(
                modifier = Modifier.width(m.slot),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // The scale/alpha layer sits on the Box rather than the Image so the
                // move highlight grows with the icon instead of sitting inside it.
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    },
                ) {
                    if (selected && vm.mode == Mode.REARRANGE) MovingRing(palette, m.catIcon)
                    Image(
                        painter = painterResource(XmbIcons.categoryRes(category.icon)),
                        contentDescription = category.title,
                        colorFilter = ColorFilter.tint(palette.fg),
                        modifier = Modifier.size(m.catIcon),
                    )
                }
                XmbLabel(
                    text = category.title.uppercase(),
                    color = palette.fg,
                    sizeSp = 11f * m.k,
                    weight = FontWeight.Normal,
                    letterSpacingEm = 0.15f,
                    // Unbounded: "CONTINUE WATCHING" is wider than a slot and would
                    // otherwise ellipsise to "CONTINUE WAT…".
                    modifier = Modifier
                        .wrapContentWidth(unbounded = true)
                        .padding(top = 7.dp * m.k)
                        .graphicsLayer { this.alpha = if (selected) 1f else 0f },
                )
            }
        }
    }
}

@Composable
private fun ItemColumn(
    category: Category,
    selIndex: Int,
    moving: Boolean,
    m: Metrics,
    palette: XmbPalette,
    onClick: (Int) -> Unit,
    onLongClick: (Int) -> Unit,
) {
    val cards = category.cards
    val gapBelow = if (cards) m.gapBelowCards else m.gapBelowRows
    val stride = if (cards) m.strideCards else m.strideRows

    Box(Modifier.fillMaxSize()) {
        // An empty column with nothing drawn reads as a broken screen. Say so instead.
        if (category.items.isEmpty()) {
            XmbLabel(
                text = stringResource(
                    if (cards) R.string.empty_continue else R.string.empty_category
                ),
                color = palette.faint,
                sizeSp = 13f * m.k,
                modifier = Modifier.offset(
                    x = m.anchorX - m.itemIcon / 2f,
                    y = m.axisY + gapBelow,
                ),
            )
            return@Box
        }

        // Only compose what can reach the screen. A 60-app column was composing 60
        // rows, each running three animations every frame — the main reason the
        // launcher felt sluggish on real hardware. Anything outside this window sits
        // beyond the fade mask and cannot be seen.
        val first = (selIndex - WINDOW_ABOVE).coerceAtLeast(0)
        val last = (selIndex + WINDOW_BELOW).coerceAtMost(category.items.lastIndex)
        for (index in first..last) {
            val item = category.items[index]
            val up = index < selIndex
            val selected = index == selIndex

            val slotHeight = when {
                up -> m.upHeight
                cards -> m.cardHeight
                else -> m.rowHeight
            }
            val iconWidth = when {
                up -> if (cards) m.upCardWidth else m.upIcon
                cards -> m.cardWidth
                else -> m.itemIcon
            }

            val centerY = when {
                selected -> m.axisY + gapBelow
                up -> m.axisY - m.gapAbove - m.strideUp * (selIndex - index - 1)
                else -> m.axisY + gapBelow + stride * (index - selIndex)
            }
            val y by animateDpAsState(
                centerY - slotHeight / 2f, tween(360, easing = XmbEase), label = "itemY"
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .offset(x = m.anchorX - iconWidth / 2f, y = y)
                    .height(slotHeight)
                    // pointerInput, NOT clickable: clickable makes the node focusable,
                    // and a focused Compose node swallows the D-pad before the
                    // activity ever sees it. Taps still work for mouse users.
                    .pointerInput(index) {
                        detectTapGestures(
                            onTap = { onClick(index) },
                            onLongPress = { onLongClick(index) },
                        )
                    },
            ) {
                if (cards) {
                    CardThumb(item, selected, up, moving && selected, iconWidth, slotHeight, palette)
                } else {
                    ItemGlyph(item, selected, up, moving && selected, iconWidth, palette, m)
                }

                Column(
                    Modifier.padding(start = if (cards) 14.dp * m.k else 12.dp * m.k),
                    verticalArrangement = Arrangement.Center,
                ) {
                    ItemLabel(item.label, selected, up, palette, m)
                    val subtitle = item.subtitle
                    if (cards && !up && subtitle != null) {
                        XmbLabel(
                            text = subtitle,
                            color = if (selected) palette.dim else palette.faint,
                            sizeSp = 9f * m.k,
                            letterSpacingEm = 0.05f,
                            modifier = Modifier.padding(top = 3.dp * m.k),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemLabel(
    text: String,
    selected: Boolean,
    up: Boolean,
    palette: XmbPalette,
    m: Metrics,
) {
    if (selected) {
        // XMB's highlight pill behind the selected label.
        Box(
            Modifier
                .clip(RoundedCornerShape(4.dp * m.k))
                .background(Color.White.copy(alpha = 0.17f))
                .padding(horizontal = 9.dp * m.k, vertical = 2.dp * m.k)
        ) {
            XmbLabel(text, palette.fg, 14f * m.k, FontWeight.Normal)
        }
    } else {
        XmbLabel(
            text = text,
            color = palette.dim.copy(alpha = if (up) 0.42f else 0.6f),
            sizeSp = (if (up) 10f else 12f) * m.k,
        )
    }
}

@Composable
private fun ItemGlyph(
    item: LauncherItem,
    selected: Boolean,
    up: Boolean,
    moving: Boolean,
    size: Dp,
    palette: XmbPalette,
    m: Metrics,
) {
    val alpha by animateFloatAsState(
        when {
            selected -> 1f
            up -> 0.38f
            else -> 0.5f
        },
        tween(300), label = "glyphAlpha",
    )
    val scale by animateFloatAsState(
        if (selected) 1.08f else if (up) 1f else 0.9f, tween(340, easing = XmbEase), label = "glyphScale"
    )

    Box(
        Modifier
            .size(size)
            .graphicsLayer {
                this.alpha = alpha
                scaleX = scale
                scaleY = scale
            },
        contentAlignment = Alignment.Center,
    ) {
        val icon = item.icon
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = item.label,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // No package icon (launcher actions): draw the glyph in a legible tint.
            Image(
                painter = painterResource(
                    item.glyph?.let(XmbIcons::glyphRes) ?: R.drawable.ps3_apps
                ),
                contentDescription = item.label,
                colorFilter = ColorFilter.tint(legibleOnDark(item.accent)),
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (moving) MovingRing(palette, size)

        if (item.favourite && !up) {
            FavouriteBadge(palette, m, Modifier.align(Alignment.TopStart))
        }
    }
}

@Composable
private fun FavouriteBadge(palette: XmbPalette, m: Metrics, modifier: Modifier = Modifier) {
    Box(
        modifier
            .offset(x = (-4).dp * m.k, y = (-4).dp * m.k)
            .size(13.dp * m.k)
            .clip(RoundedCornerShape(50))
            .background(palette.accent),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = rememberVectorPainter(XmbIcons.Heart),
            contentDescription = null,
            colorFilter = ColorFilter.tint(palette.panel),
            modifier = Modifier.size(8.dp * m.k),
        )
    }
}

@Composable
private fun MovingRing(palette: XmbPalette, size: Dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(6.dp))
            .background(palette.accent.copy(alpha = 0.22f))
    )
}

@Composable
private fun CardThumb(
    item: LauncherItem,
    selected: Boolean,
    up: Boolean,
    moving: Boolean,
    width: Dp,
    height: Dp,
    palette: XmbPalette,
) {
    val alpha by animateFloatAsState(
        when {
            selected -> 1f
            up -> 0.38f
            else -> 0.55f
        },
        tween(300), label = "cardAlpha",
    )
    val scale by animateFloatAsState(
        if (selected) 1.02f else 0.95f, tween(340, easing = XmbEase), label = "cardScale"
    )

    Box(
        Modifier
            .size(width, height)
            .graphicsLayer {
                this.alpha = alpha
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(6.dp))
            .background(
                // Placeholder while artwork resolves — never an empty hole.
                Brush.linearGradient(listOf(palette.bg1, palette.scrim))
            ),
    ) {
        item.artwork?.let { art ->
            Image(
                bitmap = art,
                contentDescription = item.label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (selected && !up) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(height * 0.32f)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = rememberVectorPainter(XmbIcons.Play),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(Color.White),
                    modifier = Modifier.size(height * 0.16f),
                )
            }
        }

        if (item.progress > 0f && !up) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxSize()
                    .drawWithContent {
                        val barHeight = 3.dp.toPx()
                        val top = size.height - barHeight
                        drawRect(
                            color = Color.White.copy(alpha = 0.26f),
                            topLeft = Offset(0f, top),
                            size = androidx.compose.ui.geometry.Size(size.width, barHeight),
                        )
                        drawRect(
                            color = palette.accent,
                            topLeft = Offset(0f, top),
                            size = androidx.compose.ui.geometry.Size(
                                size.width * item.progress.coerceIn(0f, 1f), barHeight
                            ),
                        )
                    }
            )
        }

        if (moving) {
            Box(Modifier.fillMaxSize().background(palette.accent.copy(alpha = 0.22f)))
        }
    }
}

/** Card items with no package icon still need something in the column. */
internal fun LauncherItem.isCard(): Boolean = kind == ItemKind.CARD
