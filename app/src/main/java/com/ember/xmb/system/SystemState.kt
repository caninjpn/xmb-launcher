package com.ember.xmb.system

import android.content.Context
import android.hardware.input.InputManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

enum class NetKind { WIFI, ETHERNET, OFFLINE }

/**
 * Connection indicator.
 *
 * Deliberately reports the transport only. SSID and signal strength both require the
 * location permission on modern Android, which is a lot to ask of a launcher for one
 * line of status text.
 */
@Composable
fun rememberNetworkStatus(): State<NetKind> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(NetKind.OFFLINE) }

    DisposableEffect(Unit) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (cm == null) {
            onDispose { }
        } else {
            fun classify(caps: NetworkCapabilities?): NetKind = when {
                caps == null -> NetKind.OFFLINE
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetKind.ETHERNET
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetKind.WIFI
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> NetKind.WIFI
                else -> NetKind.OFFLINE
            }

            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities,
                ) {
                    state.value = classify(networkCapabilities)
                }

                override fun onLost(network: Network) {
                    state.value = NetKind.OFFLINE
                }
            }

            // Seed immediately so the first frame is not wrongly "Offline".
            state.value = runCatching {
                classify(cm.getNetworkCapabilities(cm.activeNetwork))
            }.getOrDefault(NetKind.OFFLINE)

            runCatching { cm.registerDefaultNetworkCallback(callback) }
            onDispose { runCatching { cm.unregisterNetworkCallback(callback) } }
        }
    }
    return state
}

/**
 * True when a real keyboard or a mouse/touchpad is attached.
 *
 * Two tests, and the second one is the important one.
 *
 * The Fire TV remote reports KEYBOARD_TYPE_NON_ALPHABETIC, so keyboard type alone
 * looks like it separates a remote from a keyboard. It does not. Fire OS ships two
 * synthetic input devices that are ALWAYS present — `amzkeyboard`, which reports
 * KEYBOARD_TYPE_ALPHABETIC, and `kcmouse`, which reports SOURCE_MOUSE. Checked on a
 * real device (AFTCU864A2), both are permanently connected, so a naive check shows
 * the legend to everyone.
 *
 * What separates them is the HID descriptor: a genuine USB or Bluetooth peripheral
 * carries a non-zero vendor id, while Amazon's synthetic devices report 0x0000/0x0000.
 * That is a property of the hardware rather than a name match, so it does not break
 * when Amazon renames something.
 */
@Composable
fun rememberHasKeyboardOrMouse(): State<Boolean> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val im = context.getSystemService(Context.INPUT_SERVICE) as? InputManager
        if (im == null) {
            onDispose { }
        } else {
            fun scan(): Boolean {
                for (id in InputDevice.getDeviceIds()) {
                    val device = runCatching { im.getInputDevice(id) }.getOrNull() ?: continue
                    if (device.isVirtual) continue

                    // Synthetic Fire OS devices: no real HID behind them.
                    if (device.vendorId == 0 || device.productId == 0) continue

                    val sources = device.sources
                    val pointer =
                        (sources and InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE ||
                            (sources and InputDevice.SOURCE_TOUCHPAD) == InputDevice.SOURCE_TOUCHPAD
                    val alphabetic = device.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC
                    if (pointer || alphabetic) return true
                }
                return false
            }

            state.value = runCatching { scan() }.getOrDefault(false)

            val listener = object : InputManager.InputDeviceListener {
                override fun onInputDeviceAdded(deviceId: Int) {
                    state.value = scan()
                }

                override fun onInputDeviceRemoved(deviceId: Int) {
                    state.value = scan()
                }

                override fun onInputDeviceChanged(deviceId: Int) {
                    state.value = scan()
                }
            }
            im.registerInputDeviceListener(listener, Handler(Looper.getMainLooper()))
            onDispose { runCatching { im.unregisterInputDeviceListener(listener) } }
        }
    }
    return state
}
