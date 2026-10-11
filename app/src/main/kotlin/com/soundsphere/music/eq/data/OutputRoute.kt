/**
 * Soundsphere Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.soundsphere.music.eq.data

import android.media.AudioDeviceInfo

/**
 * Audio output route buckets for per-output EQ auto-switching.
 * Classified from [AudioDeviceInfo.type]; anything else is OTHER
 * (which never auto-switches — the global active profile stays).
 */
enum class OutputRoute {
    BLUETOOTH,
    SPEAKER,
    WIRED,
    USB,
    OTHER,
}

fun classifyOutputRoute(type: Int): OutputRoute =
    when (type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> OutputRoute.BLUETOOTH
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> OutputRoute.SPEAKER
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> OutputRoute.WIRED
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_HEADSET -> OutputRoute.USB
        else -> OutputRoute.OTHER
    }
