package com.bonesvt.thordual.core

import kotlinx.serialization.json.Json

object ProfileCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    fun decode(text: String): Profile = json.decodeFromString(Profile.serializer(), text)

    fun encode(profile: Profile): String = json.encodeToString(Profile.serializer(), profile)

    /** Returns human-readable problems; an empty list means the profile is usable. */
    fun validate(profile: Profile): List<String> {
        val problems = mutableListOf<String>()
        if (profile.id.isBlank()) problems += "Profile id is empty"
        if (profile.layouts.isEmpty()) problems += "Profile has no layouts"
        if (Geometry.parseAspect(profile.viewport.aspect) == null && profile.viewport.aspect != "fill") {
            problems += "Unknown viewport aspect '${profile.viewport.aspect}'"
        }
        if (Colors.parse(profile.background) == null) problems += "Bad background color '${profile.background}'"

        val ids = profile.regions.map { it.id }
        ids.groupBy { it }.filter { it.value.size > 1 }.keys.forEach { problems += "Duplicate region id '$it'" }
        profile.regions.forEach { r ->
            if (r.id == Profile.FULL_REGION) problems += "Region id '${r.id}' is reserved"
            if (!inUnitSquare(r.src)) problems += "Region '${r.id}' src ${r.src} is outside 0..1"
        }
        profile.viewport.rect?.let { if (!inUnitSquare(it)) problems += "Viewport rect $it is outside 0..1" }

        profile.layouts.forEach { layout ->
            if (layout.placements.isEmpty()) problems += "Layout '${layout.name}' is empty"
            layout.placements.forEach { p ->
                if (p.region != Profile.FULL_REGION && p.region !in ids) {
                    problems += "Layout '${layout.name}' references unknown region '${p.region}'"
                }
                if (!inUnitSquare(p.dst)) problems += "Layout '${layout.name}' dst ${p.dst} is outside 0..1"
                if (p.border != null && Colors.parse(p.border) == null) {
                    problems += "Layout '${layout.name}' has bad border color '${p.border}'"
                }
            }
        }
        return problems
    }

    private fun inUnitSquare(r: NRect): Boolean {
        val eps = 1e-4f
        return r.w > 0f && r.h > 0f && r.x >= -eps && r.y >= -eps && r.right <= 1f + eps && r.bottom <= 1f + eps
    }
}
