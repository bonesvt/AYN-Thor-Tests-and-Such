package com.bonesvt.thordual.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Display
import android.widget.Toast
import com.bonesvt.thordual.AppSettings
import com.bonesvt.thordual.Displays
import com.bonesvt.thordual.MainActivity
import com.bonesvt.thordual.ProfileRepository
import com.bonesvt.thordual.R
import com.bonesvt.thordual.core.Profile
import com.bonesvt.thordual.core.PxSize
import com.bonesvt.thordual.render.MirrorRenderer
import com.bonesvt.thordual.screen.BottomScreenWindow
import kotlin.math.roundToInt

/**
 * Foreground service that holds the screen-capture session. It mirrors the top screen into a
 * GPU texture and hands it to [BottomScreenWindow] on the second display.
 */
class CaptureService : Service() {

    companion object {
        private const val TAG = "ThorDual/Capture"
        private const val CHANNEL_ID = "capture"
        private const val NOTIFICATION_ID = 1

        const val ACTION_START = "com.bonesvt.thordual.START"
        const val ACTION_STOP = "com.bonesvt.thordual.STOP"
        const val ACTION_NEXT_LAYOUT = "com.bonesvt.thordual.NEXT_LAYOUT"
        const val ACTION_TOGGLE_EDIT = "com.bonesvt.thordual.TOGGLE_EDIT"
        const val ACTION_RELOAD = "com.bonesvt.thordual.RELOAD"
        private const val EXTRA_RESULT_CODE = "resultCode"
        private const val EXTRA_RESULT_DATA = "resultData"

        @Volatile
        var isRunning = false
            private set

        /** Called on the main thread whenever [isRunning] changes. */
        var stateListener: (() -> Unit)? = null

        fun start(context: Context, resultCode: Int, data: Intent) {
            context.startForegroundService(
                Intent(context, CaptureService::class.java)
                    .setAction(ACTION_START)
                    .putExtra(EXTRA_RESULT_CODE, resultCode)
                    .putExtra(EXTRA_RESULT_DATA, data),
            )
        }

        fun send(context: Context, action: String) {
            if (isRunning) context.startService(Intent(context, CaptureService::class.java).setAction(action))
        }
    }

    private val main = Handler(Looper.getMainLooper())
    private lateinit var settings: AppSettings
    private lateinit var repo: ProfileRepository
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var renderer: MirrorRenderer? = null
    private var window: BottomScreenWindow? = null
    private var targetDisplayId = Display.INVALID_DISPLAY
    private var captureSize = PxSize(0, 0)

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit

        override fun onDisplayRemoved(displayId: Int) {
            if (displayId == targetDisplayId) {
                Log.i(TAG, "Second screen went away, stopping")
                stopSelf()
            }
        }

        override fun onDisplayChanged(displayId: Int) {
            if (displayId == Display.DEFAULT_DISPLAY) onSourceDisplayChanged()
        }
    }

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            Log.i(TAG, "Screen capture stopped by the system")
            main.post { stopSelf() }
        }
    }

    override fun onCreate() {
        super.onCreate()
        settings = AppSettings(this)
        repo = ProfileRepository(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
                teardown()
                try {
                    begin(
                        intent.getIntExtra(EXTRA_RESULT_CODE, 0),
                        requireNotNull(resultData(intent)) { "Missing screen capture token" },
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start dual screen", e)
                    Toast.makeText(this, getString(R.string.start_failed, e.message), Toast.LENGTH_LONG).show()
                    stopSelf()
                }
            }
            ACTION_STOP -> stopSelf()
            ACTION_NEXT_LAYOUT -> window?.nextLayout()
            ACTION_TOGGLE_EDIT -> window?.toggleEditing()
            ACTION_RELOAD -> reloadProfile()
            null -> if (!isRunning) stopSelf() // Restarted without a capture token: nothing to do.
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    private fun begin(resultCode: Int, data: Intent) {
        val dm = getSystemService(DisplayManager::class.java)
        val target = Displays.pick(dm, settings.displayId) ?: error(getString(R.string.no_second_screen))
        val source = dm.getDisplay(Display.DEFAULT_DISPLAY)

        val mpm = getSystemService(MediaProjectionManager::class.java)
        val proj = mpm.getMediaProjection(resultCode, data) ?: error("Screen capture permission was not granted")
        projection = proj
        proj.registerCallback(projectionCallback, main)

        captureSize = captureSizeFor(source)
        val r = MirrorRenderer()
        renderer = r
        val input = r.start(captureSize.w, captureSize.h)
        virtualDisplay = proj.createVirtualDisplay(
            Displays.CAPTURE_DISPLAY_NAME,
            captureSize.w, captureSize.h, resources.displayMetrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            input, null, main,
        )

        val profile = currentProfile()
        targetDisplayId = target.displayId
        window = BottomScreenWindow(
            this, target, r, repo, profile, settings.layoutIndex, captureSize,
            object : BottomScreenWindow.Callbacks {
                override fun onLayoutChanged(index: Int) {
                    settings.layoutIndex = index
                }

                override fun onProfileSaved(profile: Profile) = Unit
            },
        ).also { it.show() }

        dm.registerDisplayListener(displayListener, main)
        Log.i(TAG, "Mirroring ${captureSize.w}x${captureSize.h} onto ${Displays.describe(target)} with '${profile.id}'")
        setRunning(true)
    }

    private fun teardown() {
        getSystemService(DisplayManager::class.java).unregisterDisplayListener(displayListener)
        window?.dismiss()
        window = null
        virtualDisplay?.release()
        virtualDisplay = null
        renderer?.release()
        renderer = null
        projection?.let {
            it.unregisterCallback(projectionCallback)
            it.stop()
        }
        projection = null
        targetDisplayId = Display.INVALID_DISPLAY
        setRunning(false)
    }

    private fun resultData(intent: Intent): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_RESULT_DATA)
        }

    private fun currentProfile(): Profile {
        val profiles = repo.list()
        return profiles.firstOrNull { it.id == settings.profileId }
            ?: profiles.firstOrNull()
            ?: error("No valid profiles found")
    }

    private fun reloadProfile() {
        val w = window ?: return
        w.setProfile(currentProfile(), settings.layoutIndex)
    }

    /** The top screen can rotate or change resolution; keep the capture matched to it. */
    private fun onSourceDisplayChanged() {
        val vd = virtualDisplay ?: return
        val source = getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY) ?: return
        val size = captureSizeFor(source)
        if (size == captureSize) return
        captureSize = size
        vd.resize(size.w, size.h, resources.displayMetrics.densityDpi)
        renderer?.setCaptureSize(size.w, size.h)
        window?.setCaptureSize(size)
    }

    private fun captureSizeFor(display: Display): PxSize {
        val real = Displays.realSize(display)
        val scale = settings.captureScale.coerceIn(0.25f, 1f)
        // Even dimensions keep some GPU buffer allocators happy.
        fun even(v: Int) = ((v * scale).roundToInt() / 2 * 2).coerceAtLeast(2)
        return PxSize(even(real.x), even(real.y))
    }

    private fun setRunning(running: Boolean) {
        if (isRunning == running) return
        isRunning = running
        main.post { stateListener?.invoke() }
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW),
        )
        fun action(action: String, label: Int, requestCode: Int) = Notification.Action.Builder(
            null, getString(label),
            PendingIntent.getService(
                this, requestCode, Intent(this, CaptureService::class.java).setAction(action),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        ).build()

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setOngoing(true)
            .setContentIntent(
                PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE),
            )
            .addAction(action(ACTION_NEXT_LAYOUT, R.string.action_next_layout, 1))
            .addAction(action(ACTION_TOGGLE_EDIT, R.string.action_calibrate, 2))
            .addAction(action(ACTION_STOP, R.string.action_stop, 3))
            .build()
    }
}
