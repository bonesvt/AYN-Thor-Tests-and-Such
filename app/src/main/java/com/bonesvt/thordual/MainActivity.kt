package com.bonesvt.thordual

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.bonesvt.thordual.core.Profile
import com.bonesvt.thordual.service.CaptureService

/** Control panel shown on the top screen. Pick a game profile, start, then launch the game. */
class MainActivity : Activity() {
    private lateinit var settings: AppSettings
    private lateinit var repo: ProfileRepository
    private var profiles: List<Profile> = emptyList()
    private var displayIds: List<Int> = emptyList()

    private lateinit var status: TextView
    private lateinit var profileSpinner: Spinner
    private lateinit var layoutSpinner: Spinner
    private lateinit var displaySpinner: Spinner
    private lateinit var qualitySpinner: Spinner
    private lateinit var profileNotes: TextView
    private lateinit var startButton: Button
    private lateinit var nextLayoutButton: Button
    private lateinit var calibrateButton: Button
    private lateinit var resetButton: Button
    private lateinit var footer: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        settings = AppSettings(this)
        repo = ProfileRepository(this)

        status = findViewById(R.id.status)
        profileSpinner = findViewById(R.id.profile)
        layoutSpinner = findViewById(R.id.layout)
        displaySpinner = findViewById(R.id.display)
        qualitySpinner = findViewById(R.id.quality)
        profileNotes = findViewById(R.id.profile_notes)
        startButton = findViewById(R.id.start)
        nextLayoutButton = findViewById(R.id.next_layout)
        calibrateButton = findViewById(R.id.calibrate)
        resetButton = findViewById(R.id.reset)
        footer = findViewById(R.id.footer)

        startButton.setOnClickListener { if (CaptureService.isRunning) stopCapture() else startCapture() }
        nextLayoutButton.setOnClickListener { CaptureService.send(this, CaptureService.ACTION_NEXT_LAYOUT) }
        calibrateButton.setOnClickListener { CaptureService.send(this, CaptureService.ACTION_TOGGLE_EDIT) }
        resetButton.setOnClickListener { resetProfile() }

        profileSpinner.onItemSelected { position ->
            val id = profiles.getOrNull(position)?.id ?: return@onItemSelected
            if (id != settings.profileId) {
                settings.profileId = id
                settings.layoutIndex = 0
                bindLayouts()
                CaptureService.send(this, CaptureService.ACTION_RELOAD)
            }
        }
        layoutSpinner.onItemSelected { position ->
            if (position != settings.layoutIndex) {
                settings.layoutIndex = position
                CaptureService.send(this, CaptureService.ACTION_RELOAD)
            }
        }
        displaySpinner.onItemSelected { position ->
            settings.displayId = displayIds.getOrElse(position) { AppSettings.AUTO_DISPLAY }
        }
        qualitySpinner.adapter = adapter(QUALITY_LABELS.map { getString(it) })
        qualitySpinner.setSelection(QUALITY_SCALES.indexOfFirst { it == settings.captureScale }.coerceAtLeast(0))
        qualitySpinner.onItemSelected { position -> settings.captureScale = QUALITY_SCALES[position] }
    }

    override fun onResume() {
        super.onResume()
        CaptureService.stateListener = { updateUi() }
        bindProfiles()
        bindDisplays()
        updateUi()
    }

    override fun onPause() {
        CaptureService.stateListener = null
        super.onPause()
    }

    @Deprecated("Activity result API needs androidx; the platform callback is fine here.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_CAPTURE) return
        if (resultCode == RESULT_OK && data != null) {
            CaptureService.start(this, resultCode, data)
            Toast.makeText(this, R.string.started_hint, Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, R.string.capture_denied, Toast.LENGTH_LONG).show()
        }
    }

    private fun startCapture() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.need_overlay, Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        if (Displays.secondary(getSystemService(DisplayManager::class.java)).isEmpty()) {
            Toast.makeText(this, R.string.no_second_screen, Toast.LENGTH_LONG).show()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            // Only for the Stop / Next layout notification buttons; capture works without it.
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }
        val mpm = getSystemService(MediaProjectionManager::class.java)
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            mpm.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
        } else {
            mpm.createScreenCaptureIntent()
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQUEST_CAPTURE)
    }

    private fun stopCapture() {
        CaptureService.send(this, CaptureService.ACTION_STOP)
    }

    private fun resetProfile() {
        val id = settings.profileId
        if (repo.reset(id)) {
            Toast.makeText(this, R.string.reset_done, Toast.LENGTH_SHORT).show()
            bindProfiles()
            CaptureService.send(this, CaptureService.ACTION_RELOAD)
        } else {
            Toast.makeText(this, R.string.reset_nothing, Toast.LENGTH_SHORT).show()
        }
    }

    private fun bindProfiles() {
        profiles = repo.list()
        profileSpinner.adapter = adapter(profiles.map { if (repo.hasUserCopy(it.id)) "${it.name} ✎" else it.name })
        val index = profiles.indexOfFirst { it.id == settings.profileId }
        if (index >= 0) profileSpinner.setSelection(index) else profiles.firstOrNull()?.let { settings.profileId = it.id }
        bindLayouts()
    }

    private fun bindLayouts() {
        val profile = profiles.firstOrNull { it.id == settings.profileId }
        layoutSpinner.adapter = adapter(profile?.layouts?.map { it.name }.orEmpty())
        if (profile != null) layoutSpinner.setSelection(settings.layoutIndex.coerceIn(0, profile.layouts.lastIndex))

        val notes = buildList {
            if (profile != null && !profile.calibrated) add(getString(R.string.uncalibrated_notice))
            if (!profile?.notes.isNullOrBlank()) add(profile!!.notes)
            repo.loadErrors.forEach { add(getString(R.string.profile_error, it)) }
        }
        profileNotes.text = notes.joinToString("\n\n")
        profileNotes.visibility = if (notes.isEmpty()) View.GONE else View.VISIBLE
        resetButton.isEnabled = profile != null && repo.hasUserCopy(profile.id)
    }

    private fun bindDisplays() {
        val displays = Displays.secondary(getSystemService(DisplayManager::class.java))
        displayIds = listOf(AppSettings.AUTO_DISPLAY) + displays.map { it.displayId }
        displaySpinner.adapter = adapter(listOf(getString(R.string.display_auto)) + displays.map { Displays.describe(it) })
        displaySpinner.setSelection(displayIds.indexOf(settings.displayId).coerceAtLeast(0))
        footer.text = getString(
            R.string.footer,
            if (displays.isEmpty()) getString(R.string.no_second_screen) else displays.joinToString("\n") { Displays.describe(it) },
            repo.userDir.absolutePath,
        )
    }

    private fun updateUi() {
        val running = CaptureService.isRunning
        status.setText(if (running) R.string.status_running else R.string.status_stopped)
        startButton.setText(if (running) R.string.stop else R.string.start)
        nextLayoutButton.isEnabled = running
        calibrateButton.isEnabled = running
        displaySpinner.isEnabled = !running
        qualitySpinner.isEnabled = !running
    }

    private fun adapter(items: List<String>) =
        ArrayAdapter(this, android.R.layout.simple_spinner_item, items).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

    private inline fun Spinner.onItemSelected(crossinline block: (Int) -> Unit) {
        onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = block(position)
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private companion object {
        const val REQUEST_CAPTURE = 1
        const val REQUEST_NOTIFICATIONS = 2
        val QUALITY_SCALES = floatArrayOf(1f, 0.75f, 0.5f)
        val QUALITY_LABELS = intArrayOf(R.string.quality_full, R.string.quality_75, R.string.quality_50)
    }
}
