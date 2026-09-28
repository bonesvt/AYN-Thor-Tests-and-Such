package com.bonesvt.thordual.core

/** One textured quad: a crop of the captured screen drawn somewhere on the second screen. */
data class DrawCommand(
    /** Crop of the capture texture, normalized, top-left origin. */
    val srcUv: NRect,
    /** Destination on the second screen, normalized, top-left origin. */
    val dst: NRect,
    val shape: Shape = Shape.RECT,
    /** ARGB, or null for no border. */
    val borderColor: Int? = null,
    val borderPx: Float = 0f,
    val opacity: Float = 1f,
)

data class Scene(val background: Int, val draws: List<DrawCommand>) {
    companion object {
        val EMPTY = Scene(0xFF000000.toInt(), emptyList())
    }
}

/** Turns a profile + current screen sizes into GPU draw commands. */
object SceneBuilder {
    fun layoutScene(profile: Profile, layoutIndex: Int, capture: PxSize, output: PxSize): Scene {
        val background = Colors.parse(profile.background) ?: Scene.EMPTY.background
        val layout = profile.layouts.getOrNull(layoutIndex) ?: profile.layouts.firstOrNull()
        if (layout == null || capture.isEmpty || output.isEmpty) return Scene(background, emptyList())

        val viewport = Geometry.viewportPx(capture, profile.viewport)
        val screen = PxRect.of(output)
        val draws = layout.placements.mapNotNull { p ->
            val srcNorm = if (p.region == Profile.FULL_REGION) NRect.FULL
            else profile.region(p.region)?.src ?: return@mapNotNull null
            val (src, dst) = Geometry.fit(viewport.sub(srcNorm), screen.sub(p.dst), p.fit)
            DrawCommand(
                srcUv = src.normalizedTo(capture),
                dst = dst.normalizedTo(output),
                shape = p.shape,
                borderColor = Colors.parse(p.border),
                borderPx = p.borderWidth,
                opacity = p.opacity.coerceIn(0f, 1f),
            )
        }
        return Scene(background, draws)
    }

    /** Where the whole game viewport lands on the second screen in calibration mode. */
    fun previewFrame(profile: Profile, capture: PxSize, output: PxSize): PxRect {
        val viewport = Geometry.viewportPx(capture, profile.viewport)
        return Geometry.fitInside(viewport.w / viewport.h, PxRect.of(output))
    }

    /** Calibration view: the full game image, so region boxes can be dragged over the HUD. */
    fun sourcePreviewScene(profile: Profile, capture: PxSize, output: PxSize): Scene {
        val background = Colors.parse(profile.background) ?: Scene.EMPTY.background
        if (capture.isEmpty || output.isEmpty) return Scene(background, emptyList())
        val viewport = Geometry.viewportPx(capture, profile.viewport)
        val frame = previewFrame(profile, capture, output)
        return Scene(background, listOf(DrawCommand(viewport.normalizedTo(capture), frame.normalizedTo(output))))
    }
}
