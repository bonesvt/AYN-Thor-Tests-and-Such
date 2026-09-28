package com.bonesvt.thordual.core

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * A rectangle in normalized coordinates (0..1), origin at the top-left.
 * Serialized as a compact `[x, y, w, h]` array so profiles stay easy to hand-edit.
 */
@Serializable(with = NRectSerializer::class)
data class NRect(val x: Float, val y: Float, val w: Float, val h: Float) {
    val right: Float get() = x + w
    val bottom: Float get() = y + h

    fun contains(px: Float, py: Float): Boolean = px >= x && px <= right && py >= y && py <= bottom

    companion object {
        val FULL = NRect(0f, 0f, 1f, 1f)
    }
}

object NRectSerializer : KSerializer<NRect> {
    private val delegate = ListSerializer(Float.serializer())

    @OptIn(ExperimentalSerializationApi::class)
    override val descriptor: SerialDescriptor = SerialDescriptor("NRect", delegate.descriptor)

    override fun serialize(encoder: Encoder, value: NRect) =
        encoder.encodeSerializableValue(delegate, listOf(value.x, value.y, value.w, value.h))

    override fun deserialize(decoder: Decoder): NRect {
        val v = decoder.decodeSerializableValue(delegate)
        if (v.size != 4) throw SerializationException("Rect must be [x, y, w, h], got $v")
        return NRect(v[0], v[1], v[2], v[3])
    }
}

@Serializable
enum class Shape {
    @SerialName("rect") RECT,
    @SerialName("ellipse") ELLIPSE,
}

/** How a cropped region is scaled into its destination box on the second screen. */
@Serializable
enum class Fit {
    /** Keep aspect ratio, letterbox inside the box. */
    @SerialName("contain") CONTAIN,

    /** Keep aspect ratio, trim the source so it fills the box. */
    @SerialName("cover") COVER,

    /** Ignore aspect ratio. */
    @SerialName("stretch") STRETCH,
}

/**
 * Where the game image sits inside the captured top screen. Emulators usually letterbox the
 * game to its aspect ratio, so region coordinates are relative to this viewport rather than
 * to the raw screen. Set [rect] to override the automatic centered fit.
 */
@Serializable
data class Viewport(
    /** "16:9", "4:3", ... or "fill" when the emulator stretches the game to the whole screen. */
    val aspect: String = "16:9",
    val rect: NRect? = null,
)

/** A piece of the game image to lift onto the second screen, in game-viewport coordinates. */
@Serializable
data class Region(
    val id: String,
    val label: String = id,
    val src: NRect,
)

/** Where a region is drawn on the second screen, in second-screen coordinates. */
@Serializable
data class Placement(
    /** A [Region.id], or [Profile.FULL_REGION] for the whole game viewport. */
    val region: String,
    val dst: NRect,
    val fit: Fit = Fit.CONTAIN,
    val shape: Shape = Shape.RECT,
    /** "#RRGGBB" / "#AARRGGBB", or null for no border. */
    val border: String? = null,
    /** Border thickness in second-screen pixels. */
    val borderWidth: Float = 4f,
    val opacity: Float = 1f,
)

@Serializable
data class Layout(
    val name: String,
    val placements: List<Placement>,
)

@Serializable
data class Profile(
    val id: String,
    val name: String,
    val platform: String = "",
    val notes: String = "",
    /** False until someone has checked the crops against the real game and saved them. */
    val calibrated: Boolean = false,
    val viewport: Viewport = Viewport(),
    val background: String = "#000000",
    val regions: List<Region> = emptyList(),
    val layouts: List<Layout>,
) {
    fun region(id: String): Region? = regions.firstOrNull { it.id == id }

    fun regionLabel(id: String): String =
        if (id == FULL_REGION) "Full game" else region(id)?.label ?: id

    fun withRegionSrc(id: String, src: NRect): Profile =
        copy(regions = regions.map { if (it.id == id) it.copy(src = src) else it })

    fun withPlacementDst(layoutIndex: Int, placementIndex: Int, dst: NRect): Profile =
        copy(layouts = layouts.mapIndexed { li, layout ->
            if (li != layoutIndex) layout
            else layout.copy(placements = layout.placements.mapIndexed { pi, p ->
                if (pi == placementIndex) p.copy(dst = dst) else p
            })
        })

    companion object {
        const val FULL_REGION = "@full"
    }
}
