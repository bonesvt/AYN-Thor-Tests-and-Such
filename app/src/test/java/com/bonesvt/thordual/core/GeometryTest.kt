package com.bonesvt.thordual.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GeometryTest {
    private val eps = 1e-3f
    private val thorTop = PxSize(1920, 1080)

    private fun assertRect(expected: PxRect, actual: PxRect) {
        assertEquals("x", expected.x, actual.x, eps)
        assertEquals("y", expected.y, actual.y, eps)
        assertEquals("w", expected.w, actual.w, eps)
        assertEquals("h", expected.h, actual.h, eps)
    }

    @Test
    fun parsesAspects() {
        assertEquals(16f / 9f, Geometry.parseAspect("16:9")!!, eps)
        assertEquals(4f / 3f, Geometry.parseAspect(" 4 : 3 ")!!, eps)
        assertEquals(1.5f, Geometry.parseAspect("1.5")!!, eps)
        assertNull(Geometry.parseAspect("fill"))
        assertNull(Geometry.parseAspect("4:0"))
        assertNull(Geometry.parseAspect("1:2:3"))
    }

    @Test
    fun widescreenGameFillsThorTopScreen() {
        assertRect(PxRect(0f, 0f, 1920f, 1080f), Geometry.viewportPx(thorTop, Viewport("16:9")))
    }

    @Test
    fun fourByThreeGameIsPillarboxed() {
        assertRect(PxRect(240f, 0f, 1440f, 1080f), Geometry.viewportPx(thorTop, Viewport("4:3")))
    }

    @Test
    fun explicitViewportRectWins() {
        val vp = Geometry.viewportPx(thorTop, Viewport("4:3", NRect(0.1f, 0f, 0.5f, 1f)))
        assertRect(PxRect(192f, 0f, 960f, 1080f), vp)
    }

    @Test
    fun fillUsesWholeCapture() {
        assertRect(PxRect(0f, 0f, 1920f, 1080f), Geometry.viewportPx(thorTop, Viewport("fill")))
    }

    @Test
    fun containLetterboxesAndCenters() {
        val (src, dst) = Geometry.fit(PxRect(0f, 0f, 400f, 200f), PxRect(0f, 0f, 400f, 400f), Fit.CONTAIN)
        assertRect(PxRect(0f, 0f, 400f, 200f), src)
        assertRect(PxRect(0f, 100f, 400f, 200f), dst)
    }

    @Test
    fun coverTrimsSourceToBoxAspect() {
        val (src, dst) = Geometry.fit(PxRect(100f, 0f, 400f, 200f), PxRect(0f, 0f, 300f, 300f), Fit.COVER)
        assertRect(PxRect(200f, 0f, 200f, 200f), src)
        assertRect(PxRect(0f, 0f, 300f, 300f), dst)
    }

    @Test
    fun stretchLeavesBothAlone() {
        val s = PxRect(1f, 2f, 3f, 4f)
        val b = PxRect(5f, 6f, 7f, 8f)
        assertEquals(s to b, Geometry.fit(s, b, Fit.STRETCH))
    }
}
