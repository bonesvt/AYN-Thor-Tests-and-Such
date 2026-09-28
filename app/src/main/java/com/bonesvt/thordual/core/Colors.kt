package com.bonesvt.thordual.core

/** Color helpers that don't depend on android.graphics, so they run in plain JVM tests. */
object Colors {
    /** Parses "#RRGGBB" or "#AARRGGBB" into an ARGB int, or null if malformed. */
    fun parse(text: String?): Int? {
        if (text == null) return null
        val hex = text.trim().removePrefix("#")
        val value = hex.toLongOrNull(16) ?: return null
        return when (hex.length) {
            6 -> (0xFF000000 or value).toInt()
            8 -> value.toInt()
            else -> null
        }
    }

    /** ARGB int to straight RGBA floats for GL uniforms. */
    fun toRgba(argb: Int): FloatArray = floatArrayOf(
        ((argb shr 16) and 0xFF) / 255f,
        ((argb shr 8) and 0xFF) / 255f,
        (argb and 0xFF) / 255f,
        ((argb ushr 24) and 0xFF) / 255f,
    )
}
