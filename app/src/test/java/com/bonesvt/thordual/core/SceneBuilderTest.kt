package com.bonesvt.thordual.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneBuilderTest {
    private val eps = 1e-4f
    private val capture = PxSize(1920, 1080)
    private val bottom = PxSize(1240, 1080)

    private fun assertRect(expected: NRect, actual: NRect) {
        assertEquals("x", expected.x, actual.x, eps)
        assertEquals("y", expected.y, actual.y, eps)
        assertEquals("w", expected.w, actual.w, eps)
        assertEquals("h", expected.h, actual.h, eps)
    }

    private fun profile(aspect: String, vararg placements: Placement) = Profile(
        id = "t", name = "t", viewport = Viewport(aspect),
        regions = listOf(Region("map", src = NRect(0.5f, 0.5f, 0.5f, 0.5f))),
        layouts = listOf(Layout("L", placements.toList())),
    )

    @Test
    fun regionCoordinatesAreRelativeToThePillarboxedViewport() {
        val p = profile("4:3", Placement("map", NRect.FULL, fit = Fit.STRETCH))
        val draw = SceneBuilder.layoutScene(p, 0, capture, bottom).draws.single()
        // 4:3 viewport is x=240..1680; the bottom-right quarter starts at x=960.
        assertRect(NRect(960f / 1920f, 0.5f, 720f / 1920f, 0.5f), draw.srcUv)
        assertRect(NRect.FULL, draw.dst)
    }

    @Test
    fun fullRegionMirrorsTheWholeViewport() {
        val p = profile("16:9", Placement(Profile.FULL_REGION, NRect.FULL))
        val draw = SceneBuilder.layoutScene(p, 0, capture, bottom).draws.single()
        assertRect(NRect.FULL, draw.srcUv)
        // 16:9 into 1240x1080 letterboxes to 1240x697.5, centered.
        val h = 1240f / (16f / 9f) / 1080f
        assertRect(NRect(0f, (1f - h) / 2f, 1f, h), draw.dst)
    }

    @Test
    fun unknownRegionsAreSkippedAndBadLayoutIndexFallsBack() {
        val p = profile("16:9", Placement("map", NRect.FULL), Placement("ghost", NRect.FULL))
        assertEquals(1, SceneBuilder.layoutScene(p, 7, capture, bottom).draws.size)
    }

    @Test
    fun emptySizesProduceNoDraws() {
        val p = profile("16:9", Placement("map", NRect.FULL))
        assertTrue(SceneBuilder.layoutScene(p, 0, PxSize(0, 0), bottom).draws.isEmpty())
        assertTrue(SceneBuilder.sourcePreviewScene(p, capture, PxSize(0, 0)).draws.isEmpty())
    }

    @Test
    fun previewSceneMatchesEditorFrame() {
        val p = profile("4:3", Placement("map", NRect.FULL))
        val draw = SceneBuilder.sourcePreviewScene(p, capture, bottom).draws.single()
        val frame = SceneBuilder.previewFrame(p, capture, bottom)
        assertRect(frame.normalizedTo(bottom), draw.dst)
        assertRect(NRect(240f / 1920f, 0f, 1440f / 1920f, 1f), draw.srcUv)
    }

    @Test
    fun bordersAndShapesPassThrough() {
        val p = profile("16:9", Placement("map", NRect.FULL, shape = Shape.ELLIPSE, border = "#FF0000", borderWidth = 6f, opacity = 2f))
        val draw = SceneBuilder.layoutScene(p, 0, capture, bottom).draws.single()
        assertEquals(Shape.ELLIPSE, draw.shape)
        assertEquals(0xFFFF0000.toInt(), draw.borderColor)
        assertEquals(6f, draw.borderPx, eps)
        assertEquals(1f, draw.opacity, eps)
    }
}
