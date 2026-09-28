package com.bonesvt.thordual.screen

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.Display
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.bonesvt.thordual.ProfileRepository
import com.bonesvt.thordual.R
import com.bonesvt.thordual.core.NRect
import com.bonesvt.thordual.core.Profile
import com.bonesvt.thordual.core.PxRect
import com.bonesvt.thordual.core.PxSize
import com.bonesvt.thordual.core.SceneBuilder
import com.bonesvt.thordual.render.MirrorRenderer

/**
 * Full-screen overlay window on the second display. It is deliberately non-focusable: touching
 * the bottom screen must never pull controller focus away from the emulator on the top screen.
 *
 * Gestures while playing: double-tap cycles layouts, long-press opens the calibration editor.
 */
class BottomScreenWindow(
    service: Context,
    display: Display,
    private val renderer: MirrorRenderer,
    private val repo: ProfileRepository,
    profile: Profile,
    layoutIndex: Int,
    private var captureSize: PxSize,
    private val callbacks: Callbacks,
) : SurfaceHolder.Callback {

    interface Callbacks {
        fun onLayoutChanged(index: Int)
        fun onProfileSaved(profile: Profile)
    }

    private enum class EditMode { SOURCE, LAYOUT }

    private val ctx: Context = ContextThemeWrapper(
        service.createDisplayContext(display).createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null),
        android.R.style.Theme_DeviceDefault,
    )
    private val wm = ctx.getSystemService(WindowManager::class.java)
    private val main = Handler(Looper.getMainLooper())

    var profile: Profile = profile
        private set
    var layoutIndex: Int = layoutIndex.coerceIn(0, profile.layouts.lastIndex)
        private set

    private var outputSize = PxSize(0, 0)
    private var editMode: EditMode? = null
    private var toolbarAtTop = false
    private var attached = false

    private val root = FrameLayout(ctx)
    private val surfaceView = SurfaceView(ctx)
    private val editor = RegionEditorView(ctx)
    private val hint = label(15f).apply { setBackgroundColor(0xB0000000.toInt()) }
    private val banner = label(20f).apply { setBackgroundColor(0xC0000000.toInt()) }
    private val modeButton = button { toggleMode() }
    private val layoutButton = button { nextLayout() }
    private val toolbar = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        setBackgroundColor(0xD0101418.toInt())
        val pad = dp(6)
        setPadding(pad, pad, pad, pad)
        addView(modeButton, weighted(1.4f))
        addView(layoutButton, weighted(1.4f))
        addView(button(R.string.edit_move_bar) { moveToolbar() }, weighted(0.5f))
        addView(button(R.string.edit_discard) { discard() }, weighted(0.8f))
        addView(button(R.string.edit_save) { save() }, weighted(0.8f))
    }
    private val hideBanner = Runnable { banner.visibility = View.GONE }

    private val gestures = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onDoubleTap(e: MotionEvent): Boolean {
            nextLayout()
            return true
        }
        override fun onLongPress(e: MotionEvent) = startEditing()
    })

    init {
        root.setBackgroundColor(Color.BLACK)
        surfaceView.holder.addCallback(this)
        root.addView(surfaceView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        root.addView(editor, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        root.addView(hint, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP))
        root.addView(toolbar, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        root.addView(banner, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        editor.listener = RegionEditorView.Listener(::onEditorChanged)
        root.setOnTouchListener { v, ev ->
            if (ev.actionMasked == MotionEvent.ACTION_UP) v.performClick()
            editMode == null && gestures.onTouchEvent(ev)
        }
        applyEditUi()
    }

    fun show() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.OPAQUE,
        ).apply {
            title = "ThorDual bottom screen"
            fitInsetsTypes = 0
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        wm.addView(root, params)
        attached = true
        if (!profile.calibrated) flash(ctx.getString(R.string.uncalibrated_banner), 4000)
    }

    fun dismiss() {
        main.removeCallbacks(hideBanner)
        if (attached) {
            attached = false
            // Removing the view destroys the surface, which detaches it from the renderer synchronously.
            wm.removeViewImmediate(root)
        }
    }

    fun setProfile(profile: Profile, layoutIndex: Int) {
        if (editMode != null) return
        this.profile = profile
        this.layoutIndex = layoutIndex.coerceIn(0, profile.layouts.lastIndex)
        refresh()
    }

    fun setCaptureSize(size: PxSize) {
        captureSize = size
        refresh()
    }

    fun nextLayout() {
        layoutIndex = (layoutIndex + 1) % profile.layouts.size
        callbacks.onLayoutChanged(layoutIndex)
        flash(profile.layouts[layoutIndex].name)
        refresh()
    }

    fun toggleEditing() = if (editMode == null) startEditing() else discard()

    private fun startEditing() {
        if (editMode != null) return
        editMode = EditMode.SOURCE
        applyEditUi()
        refresh()
    }

    private fun toggleMode() {
        editMode = if (editMode == EditMode.SOURCE) EditMode.LAYOUT else EditMode.SOURCE
        applyEditUi()
        refresh()
    }

    private fun moveToolbar() {
        toolbarAtTop = !toolbarAtTop
        (toolbar.layoutParams as FrameLayout.LayoutParams).gravity = if (toolbarAtTop) Gravity.TOP else Gravity.BOTTOM
        (hint.layoutParams as FrameLayout.LayoutParams).gravity = if (toolbarAtTop) Gravity.BOTTOM else Gravity.TOP
        toolbar.requestLayout()
        hint.requestLayout()
    }

    private fun save() {
        profile = profile.copy(calibrated = true)
        repo.save(profile)
        callbacks.onProfileSaved(profile)
        stopEditing()
        flash(ctx.getString(R.string.edit_saved, repo.userDir.absolutePath))
    }

    private fun discard() {
        profile = repo.get(profile.id) ?: profile
        layoutIndex = layoutIndex.coerceIn(0, profile.layouts.lastIndex)
        stopEditing()
    }

    private fun stopEditing() {
        editMode = null
        applyEditUi()
        refresh()
    }

    private fun applyEditUi() {
        val mode = editMode
        val editing = mode != null
        editor.visibility = if (editing) View.VISIBLE else View.GONE
        toolbar.visibility = if (editing) View.VISIBLE else View.GONE
        hint.visibility = if (editing) View.VISIBLE else View.GONE
        modeButton.setText(if (mode == EditMode.LAYOUT) R.string.edit_mode_layout else R.string.edit_mode_source)
        hint.setText(if (mode == EditMode.LAYOUT) R.string.edit_hint_layout else R.string.edit_hint_source)
        layoutButton.text = ctx.getString(R.string.edit_layout_button, profile.layouts.getOrNull(layoutIndex)?.name ?: "")
    }

    private fun onEditorChanged(key: String, rect: NRect) {
        profile = when (editMode) {
            EditMode.SOURCE -> profile.withRegionSrc(key, rect)
            EditMode.LAYOUT -> profile.withPlacementDst(layoutIndex, key.removePrefix("p").toInt(), rect)
            null -> return
        }
        pushScene()
    }

    private fun refresh() {
        applyEditUi()
        pushScene()
        if (outputSize.isEmpty) return
        when (editMode) {
            EditMode.SOURCE -> editor.setItems(
                SceneBuilder.previewFrame(profile, captureSize, outputSize),
                profile.regions.map { RegionEditorView.Item(it.id, it.label, it.src) },
            )
            EditMode.LAYOUT -> editor.setItems(
                PxRect.of(outputSize),
                profile.layouts[layoutIndex].placements.mapIndexed { i, p ->
                    RegionEditorView.Item("p$i", profile.regionLabel(p.region), p.dst)
                },
            )
            null -> Unit
        }
    }

    private fun pushScene() {
        if (outputSize.isEmpty) return
        renderer.setScene(
            if (editMode == EditMode.SOURCE) SceneBuilder.sourcePreviewScene(profile, captureSize, outputSize)
            else SceneBuilder.layoutScene(profile, layoutIndex, captureSize, outputSize),
        )
    }

    private fun flash(text: String, durationMs: Long = 1500) {
        banner.text = text
        banner.visibility = View.VISIBLE
        main.removeCallbacks(hideBanner)
        main.postDelayed(hideBanner, durationMs)
    }

    override fun surfaceCreated(holder: SurfaceHolder) = Unit

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        renderer.setOutput(holder.surface, width, height)
        outputSize = PxSize(width, height)
        refresh()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        renderer.setOutput(null, 0, 0)
        outputSize = PxSize(0, 0)
    }

    private fun label(sizeSp: Float) = TextView(ctx).apply {
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        gravity = Gravity.CENTER
        val pad = dp(10)
        setPadding(pad, pad, pad, pad)
        visibility = View.GONE
    }

    private fun button(textRes: Int = 0, onClick: () -> Unit) = Button(ctx).apply {
        if (textRes != 0) setText(textRes)
        isAllCaps = false
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setOnClickListener { onClick() }
    }

    private fun weighted(weight: Float) = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight).apply {
        marginStart = dp(3)
        marginEnd = dp(3)
    }

    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()
}
