package com.bonesvt.thordual.render

import android.opengl.GLES11Ext
import android.opengl.GLES20
import com.bonesvt.thordual.core.Colors
import com.bonesvt.thordual.core.DrawCommand
import com.bonesvt.thordual.core.Shape
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Draws crops of the external (screen-capture) texture as quads. Everything is expressed in
 * normalized top-left-origin coordinates; the shader flips into GL space and applies the
 * SurfaceTexture transform so crops line up regardless of how the producer lays out buffers.
 */
class MirrorProgram {
    val textureId: Int
    private val program: Int
    private val aPos: Int
    private val uDst: Int
    private val uSrc: Int
    private val uTexMatrix: Int
    private val uTex: Int
    private val uShape: Int
    private val uBorder: Int
    private val uBorderColor: Int
    private val uOpacity: Int

    private val quad: FloatBuffer = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f))
        position(0)
    }

    init {
        program = link(compile(GLES20.GL_VERTEX_SHADER, VERTEX), compile(GLES20.GL_FRAGMENT_SHADER, FRAGMENT))
        aPos = GLES20.glGetAttribLocation(program, "aPos")
        uDst = GLES20.glGetUniformLocation(program, "uDst")
        uSrc = GLES20.glGetUniformLocation(program, "uSrc")
        uTexMatrix = GLES20.glGetUniformLocation(program, "uTexMatrix")
        uTex = GLES20.glGetUniformLocation(program, "uTex")
        uShape = GLES20.glGetUniformLocation(program, "uShape")
        uBorder = GLES20.glGetUniformLocation(program, "uBorder")
        uBorderColor = GLES20.glGetUniformLocation(program, "uBorderColor")
        uOpacity = GLES20.glGetUniformLocation(program, "uOpacity")

        val tex = IntArray(1)
        GLES20.glGenTextures(1, tex, 0)
        textureId = tex[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    }

    fun draw(draws: List<DrawCommand>, texMatrix: FloatArray, outW: Int, outH: Int) {
        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glUniform1i(uTex, 0)
        GLES20.glUniformMatrix4fv(uTexMatrix, 1, false, texMatrix, 0)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, quad)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        for (d in draws) {
            GLES20.glUniform4f(uDst, d.dst.x, d.dst.y, d.dst.w, d.dst.h)
            GLES20.glUniform4f(uSrc, d.srcUv.x, d.srcUv.y, d.srcUv.w, d.srcUv.h)
            GLES20.glUniform1f(uShape, if (d.shape == Shape.ELLIPSE) 1f else 0f)
            GLES20.glUniform1f(uOpacity, d.opacity)
            val color = d.borderColor
            if (color != null && d.borderPx > 0f) {
                val c = Colors.toRgba(color)
                GLES20.glUniform4f(uBorderColor, c[0], c[1], c[2], c[3])
                GLES20.glUniform2f(uBorder, d.borderPx / (d.dst.w * outW), d.borderPx / (d.dst.h * outH))
            } else {
                GLES20.glUniform4f(uBorderColor, 0f, 0f, 0f, 0f)
                GLES20.glUniform2f(uBorder, 0f, 0f)
            }
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        }

        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    fun release() {
        GLES20.glDeleteProgram(program)
        GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
    }

    private fun compile(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val ok = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            error("Shader compile failed: $log")
        }
        return shader
    }

    private fun link(vs: Int, fs: Int): Int {
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, vs)
        GLES20.glAttachShader(p, fs)
        GLES20.glLinkProgram(p)
        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)
        val ok = IntArray(1)
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0)
        if (ok[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(p)
            GLES20.glDeleteProgram(p)
            error("Program link failed: $log")
        }
        return p
    }

    private companion object {
        const val VERTEX = """
            attribute vec2 aPos;
            uniform vec4 uDst;
            uniform vec4 uSrc;
            uniform mat4 uTexMatrix;
            varying vec2 vTex;
            varying vec2 vLocal;
            void main() {
                vLocal = aPos;
                vec2 p = uDst.xy + aPos * uDst.zw;
                gl_Position = vec4(p.x * 2.0 - 1.0, 1.0 - p.y * 2.0, 0.0, 1.0);
                vec2 img = uSrc.xy + aPos * uSrc.zw;
                vTex = (uTexMatrix * vec4(img.x, 1.0 - img.y, 0.0, 1.0)).xy;
            }
        """

        const val FRAGMENT = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            uniform samplerExternalOES uTex;
            uniform float uShape;
            uniform vec2 uBorder;
            uniform vec4 uBorderColor;
            uniform float uOpacity;
            varying vec2 vTex;
            varying vec2 vLocal;
            void main() {
                vec4 c = texture2D(uTex, vTex);
                bool border = uBorderColor.a > 0.0;
                if (uShape > 0.5) {
                    vec2 d = (vLocal - 0.5) * 2.0;
                    if (length(d) > 1.0) discard;
                    vec2 inner = d / max(vec2(1.0) - uBorder * 2.0, vec2(0.001));
                    if (border && length(inner) > 1.0) c = uBorderColor;
                } else if (border) {
                    if (vLocal.x < uBorder.x || vLocal.x > 1.0 - uBorder.x ||
                        vLocal.y < uBorder.y || vLocal.y > 1.0 - uBorder.y) c = uBorderColor;
                }
                gl_FragColor = vec4(c.rgb, c.a * uOpacity);
            }
        """
    }
}
