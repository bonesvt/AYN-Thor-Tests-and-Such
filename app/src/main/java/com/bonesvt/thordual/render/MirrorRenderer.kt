package com.bonesvt.thordual.render

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.Matrix
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import android.util.Log
import android.view.Surface
import com.bonesvt.thordual.core.Colors
import com.bonesvt.thordual.core.Scene
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Owns a GL thread that receives the captured top screen through a SurfaceTexture and draws the
 * current [Scene] onto the second screen's surface. The whole path stays on the GPU: no bitmaps,
 * no CPU copies, so it adds very little latency or load while a game is running.
 */
class MirrorRenderer {
    private val thread = HandlerThread("ThorDual-GL", Process.THREAD_PRIORITY_DISPLAY).apply { start() }
    private val handler = Handler(thread.looper)

    // Everything below is only touched on the GL thread.
    private var egl: EglCore? = null
    private var pbuffer: EGLSurface = EGL14.EGL_NO_SURFACE
    private var window: EGLSurface = EGL14.EGL_NO_SURFACE
    private var windowTarget: Surface? = null
    private var outW = 0
    private var outH = 0
    private var program: MirrorProgram? = null
    private var surfaceTexture: SurfaceTexture? = null
    private var inputSurface: Surface? = null
    private val texMatrix = FloatArray(16).also { Matrix.setIdentityM(it, 0) }
    private var scene = Scene.EMPTY
    private var hasFrame = false
    private var released = false

    /** Sets up GL and returns the Surface the screen capture should render into. */
    fun start(captureW: Int, captureH: Int): Surface = runSync {
        val core = EglCore()
        egl = core
        pbuffer = core.createPbufferSurface(1, 1)
        core.makeCurrent(pbuffer)
        val prog = MirrorProgram()
        program = prog
        val st = SurfaceTexture(prog.textureId)
        st.setDefaultBufferSize(captureW, captureH)
        st.setOnFrameAvailableListener({ onFrameAvailable() }, handler)
        surfaceTexture = st
        Surface(st).also { inputSurface = it }
    }

    fun setCaptureSize(w: Int, h: Int) {
        handler.post { surfaceTexture?.setDefaultBufferSize(w, h) }
    }

    /**
     * Attaches (or with null, detaches) the output surface. Blocks until done so a
     * SurfaceHolder.Callback can safely return from surfaceDestroyed afterwards.
     */
    fun setOutput(surface: Surface?, width: Int, height: Int) = runSync {
        val core = egl ?: return@runSync
        if (surface !== windowTarget && window != EGL14.EGL_NO_SURFACE) {
            core.releaseSurface(window)
            window = EGL14.EGL_NO_SURFACE
            windowTarget = null
        }
        if (surface != null && window == EGL14.EGL_NO_SURFACE) {
            window = core.createWindowSurface(surface)
            windowTarget = surface
        }
        outW = width
        outH = height
        if (window == EGL14.EGL_NO_SURFACE) core.makeCurrent(pbuffer) else draw()
    }

    fun setScene(scene: Scene) {
        handler.post {
            this.scene = scene
            draw()
        }
    }

    fun release() {
        handler.post {
            released = true
            val core = egl
            if (core != null) {
                core.makeCurrent(pbuffer)
                program?.release()
                if (window != EGL14.EGL_NO_SURFACE) core.releaseSurface(window)
                core.releaseSurface(pbuffer)
                core.release()
            }
            inputSurface?.release()
            surfaceTexture?.release()
            egl = null
        }
        thread.quitSafely()
    }

    private fun onFrameAvailable() {
        if (released) return
        val core = egl ?: return
        val st = surfaceTexture ?: return
        core.makeCurrent(if (window != EGL14.EGL_NO_SURFACE) window else pbuffer)
        // Always latch, even with no output, so the capture producer never stalls.
        st.updateTexImage()
        st.getTransformMatrix(texMatrix)
        hasFrame = true
        draw()
    }

    private fun draw() {
        val core = egl ?: return
        if (released || window == EGL14.EGL_NO_SURFACE || outW <= 0 || outH <= 0) return
        core.makeCurrent(window)
        GLES20.glViewport(0, 0, outW, outH)
        val bg = Colors.toRgba(scene.background)
        GLES20.glClearColor(bg[0], bg[1], bg[2], 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        if (hasFrame) program?.draw(scene.draws, texMatrix, outW, outH)
        if (!core.swapBuffers(window)) {
            Log.w(TAG, "eglSwapBuffers failed: 0x${Integer.toHexString(EGL14.eglGetError())}")
        }
    }

    private fun <T> runSync(block: () -> T): T {
        if (Looper.myLooper() == thread.looper) return block()
        val latch = CountDownLatch(1)
        var result: Result<T>? = null
        check(handler.post {
            result = runCatching(block)
            latch.countDown()
        }) { "Renderer thread is gone" }
        check(latch.await(5, TimeUnit.SECONDS)) { "Renderer thread timed out" }
        return result!!.getOrThrow()
    }

    private companion object {
        const val TAG = "ThorDual/Renderer"
    }
}
