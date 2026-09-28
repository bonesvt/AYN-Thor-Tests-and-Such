package com.bonesvt.thordual.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ProfileTest {
    private val eps = 1e-4f
    private val assetDir = File("src/main/assets/profiles")

    private fun bundled(): List<Pair<File, Profile>> =
        assetDir.listFiles { f -> f.extension == "json" }!!.map { it to ProfileCodec.decode(it.readText()) }

    @Test
    fun everyBundledProfileIsValid() {
        val profiles = bundled()
        assertTrue("no bundled profiles found in ${assetDir.absolutePath}", profiles.isNotEmpty())
        for ((file, profile) in profiles) {
            assertEquals("${file.name}: ${ProfileCodec.validate(profile)}", emptyList<String>(), ProfileCodec.validate(profile))
            assertEquals("file name should match id", "${profile.id}.json", file.name)
        }
    }

    @Test
    fun jakXProfileShipsUncalibratedWithAFullMirrorLayout() {
        val jak = bundled().map { it.second }.single { it.id == "jak-x-combat-racing" }
        assertFalse(jak.calibrated)
        assertTrue(jak.layouts.any { l -> l.placements.any { it.region == Profile.FULL_REGION } })
    }

    @Test
    fun roundTripsThroughJson() {
        for ((_, profile) in bundled()) {
            assertEquals(profile, ProfileCodec.decode(ProfileCodec.encode(profile)))
        }
    }

    @Test
    fun validationCatchesBrokenProfiles() {
        val broken = Profile(
            id = "x", name = "x", background = "nope",
            regions = listOf(Region("a", src = NRect(0.9f, 0f, 0.5f, 0.5f)), Region("a", src = NRect.FULL)),
            layouts = listOf(Layout("L", listOf(Placement("missing", NRect.FULL, border = "#12")))),
        )
        val problems = ProfileCodec.validate(broken)
        assertTrue(problems.any { "background" in it })
        assertTrue(problems.any { "Duplicate region id 'a'" in it })
        assertTrue(problems.any { "outside 0..1" in it })
        assertTrue(problems.any { "unknown region 'missing'" in it })
        assertTrue(problems.any { "border color" in it })
    }

    @Test
    fun editingHelpersOnlyTouchTheirTarget() {
        val p = Profile(
            id = "p", name = "p",
            regions = listOf(Region("a", src = NRect.FULL), Region("b", src = NRect.FULL)),
            layouts = listOf(
                Layout("one", listOf(Placement("a", NRect.FULL), Placement("b", NRect.FULL))),
                Layout("two", listOf(Placement("a", NRect.FULL))),
            ),
        )
        val r = NRect(0.1f, 0.2f, 0.3f, 0.4f)
        val edited = p.withRegionSrc("b", r).withPlacementDst(0, 1, r)
        assertEquals(NRect.FULL, edited.region("a")!!.src)
        assertEquals(r, edited.region("b")!!.src)
        assertEquals(NRect.FULL, edited.layouts[0].placements[0].dst)
        assertEquals(r, edited.layouts[0].placements[1].dst)
        assertEquals(NRect.FULL, edited.layouts[1].placements[0].dst)
    }

    @Test
    fun colorsParse() {
        assertEquals(0xFF29D17D.toInt(), Colors.parse("#29D17D"))
        assertEquals(0x8029D17D.toInt(), Colors.parse("#8029D17D"))
        assertEquals(null, Colors.parse("#GGGGGG"))
        val rgba = Colors.toRgba(0x80FF0000.toInt())
        assertEquals(1f, rgba[0], eps)
        assertEquals(0f, rgba[1], eps)
        assertEquals(128f / 255f, rgba[3], eps)
    }
}
