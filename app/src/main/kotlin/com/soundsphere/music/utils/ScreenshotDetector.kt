/**
 * Soundsphere Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.soundsphere.music.utils

import android.os.Environment
import android.os.FileObserver
import java.io.File

/**
 * Watches the system screenshot directories and reports when a new screenshot
 * is taken. Used to nudge users towards the beautiful share card instead of
 * a plain screenshot.
 */
class ScreenshotDetector(
    private val onScreenshot: () -> Unit,
) : AutoCloseable {
    @Volatile
    private var active = false

    private val observers = mutableListOf<FileObserver>()

    private val screenshotDirectories =
        listOf("Screenshots", "Screenshot")
            .map { name ->
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                    .resolve(name)
            }

    /**
     * Starts watching for new screenshots. Repeated calls are ignored until
     * [stopWatching] is invoked.
     */
    fun startWatching() {
        if (active) return
        active = true

        screenshotDirectories.filter(File::exists).forEach { dir ->
            val observer =
                object : FileObserver(dir.absolutePath, FileObserver.CREATE or FileObserver.CLOSE_WRITE) {
                    override fun onEvent(event: Int, path: String?) {
                        if (!active || path == null) return
                        if (path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".jpeg")) {
                            onScreenshot()
                        }
                    }
                }
            observer.startWatching()
            observers.add(observer)
        }
    }

    /**
     * Stops watching for new screenshots.
     */
    fun stopWatching() {
        if (!active) return
        active = false
        observers.forEach { observer ->
            runCatching { observer.stopWatching() }
        }
        observers.clear()
    }

    override fun close() = stopWatching()
}