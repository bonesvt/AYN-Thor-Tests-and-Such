package com.bonesvt.thordual.core

/** Drag math for the on-device region editor, kept pure so it can be unit-tested. */
object RectEdit {
    enum class Handle { MOVE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

    const val MIN_SIZE = 0.02f

    fun apply(r: NRect, handle: Handle, dx: Float, dy: Float, minSize: Float = MIN_SIZE): NRect = when (handle) {
        Handle.MOVE -> move(r, dx, dy)
        else -> resize(r, handle, dx, dy, minSize)
    }

    fun move(r: NRect, dx: Float, dy: Float): NRect = r.copy(
        x = (r.x + dx).coerceIn(0f, (1f - r.w).coerceAtLeast(0f)),
        y = (r.y + dy).coerceIn(0f, (1f - r.h).coerceAtLeast(0f)),
    )

    fun resize(r: NRect, handle: Handle, dx: Float, dy: Float, minSize: Float = MIN_SIZE): NRect {
        var left = r.x
        var top = r.y
        var right = r.right
        var bottom = r.bottom
        when (handle) {
            Handle.TOP_LEFT -> { left += dx; top += dy }
            Handle.TOP_RIGHT -> { right += dx; top += dy }
            Handle.BOTTOM_LEFT -> { left += dx; bottom += dy }
            Handle.BOTTOM_RIGHT -> { right += dx; bottom += dy }
            Handle.MOVE -> return move(r, dx, dy)
        }
        // Clamp the edge being dragged, never the opposite one.
        if (handle == Handle.TOP_LEFT || handle == Handle.BOTTOM_LEFT) left = left.coerceIn(0f, right - minSize)
        else right = right.coerceIn(left + minSize, 1f)
        if (handle == Handle.TOP_LEFT || handle == Handle.TOP_RIGHT) top = top.coerceIn(0f, bottom - minSize)
        else bottom = bottom.coerceIn(top + minSize, 1f)
        return NRect(left, top, right - left, bottom - top)
    }
}
