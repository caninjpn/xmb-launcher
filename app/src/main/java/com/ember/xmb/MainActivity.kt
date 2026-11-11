package com.ember.xmb

import android.os.Bundle
import android.view.KeyEvent
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import com.ember.xmb.gl.WaveRenderer
import com.ember.xmb.gl.WaveView
import com.ember.xmb.system.rememberHasKeyboardOrMouse
import com.ember.xmb.system.rememberNetworkStatus
import com.ember.xmb.ui.XmbPalette
import com.ember.xmb.ui.XmbScreen
import com.ember.xmb.ui.XmbThemes
import kotlinx.coroutines.delay

/**
 * The launcher.
 *
 * Two layers: a GLSurfaceView drawing the animated background, and a ComposeView on
 * top drawing the cross. The SurfaceView's surface is composited BELOW the window, so
 * the activity theme keeps a transparent window background — see res/values/themes.xml.
 * Compose must not paint an opaque background or the GL layer disappears.
 */
class MainActivity : ComponentActivity() {

    private val vm: XmbViewModel by viewModels()
    private lateinit var wave: WaveView

    /** Set when a long-press on Select opened the menu, so the key-up does not also fire. */
    private var longPressConsumed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        wave = WaveView(this)
        val compose = ComposeView(this).apply {
            setContent { XmbRoot(vm, wave.renderer) }
        }

        setContentView(
            FrameLayout(this).apply {
                addView(wave, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
                addView(compose, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            }
        )
    }

    override fun onResume() {
        super.onResume()
        wave.onResume()
        // Coming back from an uninstall or an app info screen: pick up the change.
        vm.refresh()
    }

    override fun onPause() {
        wave.onPause()
        super.onPause()
    }

    // ------------------------------------------------------------ remote keys

    private fun isSelect(keyCode: Int) = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER,
        KeyEvent.KEYCODE_BUTTON_A,
        -> true

        else -> false
    }

    /**
     * All remote input is handled here rather than in onKeyDown, and that is the whole
     * point.
     *
     * onKeyDown only runs if no focused View consumed the key first. Compose's
     * `clickable` modifiers make their nodes focusable, and once Compose focus lands
     * on one, Compose's focus system swallows D-pad keys for focus traversal. The
     * launcher then sits there focused and repainting while the remote does nothing,
     * until the process is restarted. Observed exactly that on the device.
     *
     * dispatchKeyEvent sees every key before the view hierarchy does, so the cross can
     * never be starved of input by whatever Compose thinks is focused.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode

        when (event.action) {
            KeyEvent.ACTION_DOWN -> when {
                code == KeyEvent.KEYCODE_DPAD_LEFT -> { vm.onDirection(-1, 0); return true }
                code == KeyEvent.KEYCODE_DPAD_RIGHT -> { vm.onDirection(1, 0); return true }
                code == KeyEvent.KEYCODE_DPAD_UP -> { vm.onDirection(0, -1); return true }
                code == KeyEvent.KEYCODE_DPAD_DOWN -> { vm.onDirection(0, 1); return true }

                code == KeyEvent.KEYCODE_MENU -> { vm.onLongSelect(); return true }

                code == KeyEvent.KEYCODE_BACK -> return true   // nothing sits behind HOME

                isSelect(code) -> {
                    // The framework starts auto-repeating once the long-press timeout
                    // elapses, so the first repeat IS the long press.
                    if (event.repeatCount == 0) longPressConsumed = false
                    else if (event.repeatCount == 1) longPressConsumed = vm.onLongSelect()
                    return true
                }
            }

            KeyEvent.ACTION_UP -> {
                if (isSelect(code)) {
                    if (!longPressConsumed) vm.onSelect()
                    longPressConsumed = false
                    return true
                }
                if (code == KeyEvent.KEYCODE_BACK) {
                    vm.onBack()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }
}

@Composable
private fun XmbRoot(vm: XmbViewModel, renderer: WaveRenderer) {
    val palette = rememberPalette(vm.themeIndex)
    val net by rememberNetworkStatus()
    val hasPeripheral by rememberHasKeyboardOrMouse()

    LaunchedEffect(palette) {
        renderer.hue = palette.hue
        renderer.shade = palette.shade
    }

    XmbScreen(
        vm = vm,
        palette = palette,
        net = net,
        showLegend = hasPeripheral,
        onAxisFraction = { renderer.axisFraction = it },
    )
}

/**
 * Rebuilt every minute so the time-of-day shading drifts rather than only changing
 * when the theme is switched.
 */
@Composable
private fun rememberPalette(themeIndex: Int): XmbPalette {
    var minuteTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            minuteTick++
        }
    }
    return remember(themeIndex, minuteTick) { XmbThemes.palette(themeIndex) }
}
