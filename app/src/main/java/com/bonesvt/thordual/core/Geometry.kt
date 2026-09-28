package com.bonesvt.thordual.core

import kotlin.math.min

data class PxSize(val w: Int, val h: Int) {
    val isEmpty: Boolean get() = w <= 0 || h <= 0
}

data class PxRect(val x: Float, val y: Float, val w: Float, val h: Float) {
    val right: Float get() = x + w
    val bottom: Float get() = y + h

    /** Maps a rect normalized to this one into absolute pixels. */
    fun sub(n: NRect): PxRect = PxRect(x + n.x * w, y + n.y * h, n.w * w, n.h * h)

    /** Expresses this rect normalized to [size]. */
    fun normalizedTo(size: PxSize): NRect = NRect(x / size.w, y / size.h, w / size.w, h / size.h)

    companion object {
        fun of(size: PxSize) = PxRect(0f, 0f, size.w.toFloat(), size.h.toFloat())
    }
}

object Geometry {
    /** "16:9" -> 1.777..., "1.5" -> 1.5; null for "fill" or garbage. */
    fun parseAspect(text: String): Float? {
        val parts = text.split(':')
        val value = when (parts.size) {
            1 -> parts[0].trim().toFloatOrNull()
            2 -> {
                val a = parts[0].trim().toFloatOrNull()
                val b = parts[1].trim().toFloatOrNull()
                if (a == null || b == null || b == 0f) null else a / b
            }
            else -> null
        }
        return value?.takeIf { it > 0f && it.isFinite() }
    }

    /** Where the game image sits inside the captured screen, in capture pixels. */
    fun viewportPx(capture: PxSize, viewport: Viewport): PxRect {
        val full = PxRect.of(capture)
        viewport.rect?.let { return full.sub(it) }
        val aspect = parseAspect(viewport.aspect) ?: return full
        return fitInside(aspect, full)
    }

    /** Largest rect of [aspect] (w/h) centered inside [box]. */
    fun fitInside(aspect: Float, box: PxRect): PxRect {
        val boxAspect = box.w / box.h
        return if (boxAspect > aspect) {
            val w = box.h * aspect
            PxRect(box.x + (box.w - w) / 2f, box.y, w, box.h)
        } else {
            val h = box.w / aspect
            PxRect(box.x, box.y + (box.h - h) / 2f, box.w, h)
        }
    }

    /**
     * Resolves how [src] is drawn into [box] for the given [fit].
     * Returns the (possibly trimmed) source rect and the (possibly shrunk) destination rect.
     */
    fun fit(src: PxRect, box: PxRect, fit: Fit): Pair<PxRect, PxRect> = when (fit) {
        Fit.STRETCH -> src to box
        Fit.CONTAIN -> {
            val scale = min(box.w / src.w, box.h / src.h)
            val w = src.w * scale
            val h = src.h * scale
            src to PxRect(box.x + (box.w - w) / 2f, box.y + (box.h - h) / 2f, w, h)
        }
        Fit.COVER -> fitInside(box.w / box.h, src) to box
    }
}
