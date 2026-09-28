package com.bonesvt.thordual

import android.content.Context
import android.util.Log
import com.bonesvt.thordual.core.Profile
import com.bonesvt.thordual.core.ProfileCodec
import java.io.File

/**
 * Built-in profiles ship in assets/profiles. Calibrated copies are written to
 * Android/data/<package>/files/profiles/<id>.json, where they can be pulled with adb,
 * shared, hand-edited, or dropped in to add support for new games.
 */
class ProfileRepository(private val context: Context) {
    val userDir: File
        get() = File(context.getExternalFilesDir(null) ?: context.filesDir, "profiles").apply { mkdirs() }

    /** Problems found while loading, for display in the UI. */
    var loadErrors: List<String> = emptyList()
        private set

    fun list(): List<Profile> {
        val errors = mutableListOf<String>()
        val byId = LinkedHashMap<String, Profile>()
        context.assets.list(ASSET_DIR).orEmpty().filter { it.endsWith(".json") }.sorted().forEach { name ->
            load("assets/$name", errors) { context.assets.open("$ASSET_DIR/$name").bufferedReader().use { it.readText() } }
                ?.let { byId[it.id] = it }
        }
        userDir.listFiles { f -> f.extension == "json" }.orEmpty().sortedBy { it.name }.forEach { file ->
            load(file.name, errors) { file.readText() }?.let { byId[it.id] = it }
        }
        loadErrors = errors
        return byId.values.toList()
    }

    fun get(id: String): Profile? = list().firstOrNull { it.id == id }

    fun save(profile: Profile) {
        fileFor(profile.id).writeText(ProfileCodec.encode(profile))
    }

    fun hasUserCopy(id: String): Boolean = fileFor(id).exists()

    /** Deletes the calibrated copy so the built-in profile applies again. */
    fun reset(id: String): Boolean = fileFor(id).delete()

    private fun fileFor(id: String) = File(userDir, id.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".json")

    private inline fun load(source: String, errors: MutableList<String>, read: () -> String): Profile? = try {
        val profile = ProfileCodec.decode(read())
        val problems = ProfileCodec.validate(profile)
        if (problems.isEmpty()) profile else {
            errors += "$source: ${problems.joinToString("; ")}"
            null
        }
    } catch (e: Exception) {
        Log.w(TAG, "Failed to load profile $source", e)
        errors += "$source: ${e.message}"
        null
    }

    private companion object {
        const val TAG = "ThorDual/Profiles"
        const val ASSET_DIR = "profiles"
    }
}
