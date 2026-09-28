package com.bonesvt.thordual.core

import org.junit.Assert.assertEquals
import org.junit.Test

class RectEditTest {
    private val eps = 1e-5f

    private fun assertRect(expected: NRect, actual: NRect) {
        assertEquals("x", expected.x, actual.x, eps)
        assertEquals("y", expected.y, actual.y, eps)
        assertEquals("w", expected.w, actual.w, eps)
        assertEquals("h", expected.h, actual.h, eps)
    }

    @Test
    fun moveIsClampedInsideUnitSquare() {
        val r = NRect(0.7f, 0.1f, 0.2f, 0.2f)
        assertRect(NRect(0.8f, 0f, 0.2f, 0.2f), RectEdit.move(r, 0.5f, -0.5f))
    }

    @Test
    fun bottomRightResizeGrowsAndClamps() {
        val r = NRect(0.5f, 0.5f, 0.2f, 0.2f)
        assertRect(NRect(0.5f, 0.5f, 0.3f, 0.5f), RectEdit.resize(r, RectEdit.Handle.BOTTOM_RIGHT, 0.1f, 0.9f))
    }

    @Test
    fun topLeftResizeKeepsOppositeCornerFixed() {
        val r = NRect(0.4f, 0.4f, 0.2f, 0.2f)
        val out = RectEdit.resize(r, RectEdit.Handle.TOP_LEFT, -0.1f, -0.2f)
        assertRect(NRect(0.3f, 0.2f, 0.3f, 0.4f), out)
        assertEquals(r.right, out.right, eps)
        assertEquals(r.bottom, out.bottom, eps)
    }

    @Test
    fun resizeCannotInvertOrCollapse() {
        val r = NRect(0.4f, 0.4f, 0.2f, 0.2f)
        val out = RectEdit.resize(r, RectEdit.Handle.TOP_RIGHT, -1f, 1f)
        assertEquals(RectEdit.MIN_SIZE, out.w, eps)
        assertEquals(RectEdit.MIN_SIZE, out.h, eps)
        assertEquals(0.4f, out.x, eps)
        assertEquals(r.bottom, out.bottom, eps)
    }
}
