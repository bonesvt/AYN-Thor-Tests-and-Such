package com.bonesvt.thordual

import android.graphics.Point
import android.hardware.display.DisplayManager
import android.view.Display

object Displays {
    /** Name of our own capture display, so we never try to draw onto it. */
    const val CAPTURE_DISPLAY_NAME = "ThorDualCapture"

    /** Screens we could draw the second-screen view on (the Thor's bottom panel, external monitors...). */
    fun secondary(dm: DisplayManager): List<Display> = dm.displays
        .filter { it.displayId != Display.DEFAULT_DISPLAY && !it.name.startsWith(CAPTURE_DISPLAY_NAME) }
        .sortedBy { if (it.state == Display.STATE_ON) 0 else 1 }

    fun pick(dm: DisplayManager, preferredId: Int): Display? {
        val candidates = secondary(dm)
        return candidates.firstOrNull { it.displayId == preferredId } ?: candidates.firstOrNull()
    }

    @Suppress("DEPRECATION")
    fun realSize(display: Display): Point = Point().also { display.getRealSize(it) }

    fun describe(display: Display): String {
        val size = realSize(display)
        val state = when (display.state) {
            Display.STATE_ON -> "on"
            Display.STATE_OFF -> "off"
            else -> "state ${display.state}"
        }
        return "#${display.displayId} ${display.name} (${size.x}×${size.y}, $state)"
    }
}
