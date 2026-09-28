package com.bonesvt.thordual.screen

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import com.bonesvt.thordual.core.NRect
import com.bonesvt.thordual.core.PxRect
import com.bonesvt.thordual.core.RectEdit
import kotlin.math.hypot

/**
 * Transparent overlay that draws draggable boxes over the live preview. Boxes are normalized to
 * [frame] (the area of this view the preview occupies). Drag inside a box to move it, drag a
 * corner of the selected box to resize it.
 */
class RegionEditorView(context: Context) : View(context) {
    data class Item(val key: String, val label: String, val rect: NRect)

    fun interface Listener {
        fun onItemChanged(key: String, rect: NRect)
    }

    var listener: Listener? = null

    private var frame = PxRect(0f, 0f, 0f, 0f)
    private var items: List<Item> = emptyList()
    private var selectedKey: String? = null
    private var handle: RectEdit.Handle? = null
    private var lastX = 0f
    private var lastY = 0f

    private val density = resources.displayMetrics.density
    private val touchRadius = 36f * density
    private val handleRadius = 11f * density

    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        color = 0x99FFFFFF.toInt()
        pathEffect = DashPathEffect(floatArrayOf(10f * density, 6f * density), 0f)
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = 14f * density
        isFakeBoldText = true
    }
    private val scratch = RectF()

    fun setItems(frame: PxRect, items: List<Item>) {
        this.frame = frame
        this.items = items
        if (items.none { it.key == selectedKey }) selectedKey = items.firstOrNull()?.key
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(frame.x, frame.y, frame.right, frame.bottom, framePaint)
        items.forEachIndexed { i, item ->
            val color = PALETTE[i % PALETTE.size]
            val r = toPx(item.rect, scratch)
            val selected = item.key == selectedKey
            if (selected) {
                fillPaint.color = (color and 0x00FFFFFF) or 0x40000000
                canvas.drawRect(r, fillPaint)
            }
            strokePaint.color = color
            canvas.drawRect(r, strokePaint)

            val textW = labelPaint.measureText(item.label)
            val pad = 4f * density
            val labelTop = if (r.top - labelPaint.textSize - 2 * pad >= frame.y) r.top - labelPaint.textSize - 2 * pad else r.top
            fillPaint.color = (color and 0x00FFFFFF) or 0xCC000000.toInt()
            canvas.drawRect(r.left, labelTop, r.left + textW + 2 * pad, labelTop + labelPaint.textSize + 2 * pad, fillPaint)
            canvas.drawText(item.label, r.left + pad, labelTop + pad + labelPaint.textSize * 0.85f, labelPaint)

            if (selected) {
                fillPaint.color = color
                for ((cx, cy) in corners(r)) canvas.drawCircle(cx, cy, handleRadius, fillPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (frame.w <= 0f || frame.h <= 0f) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                handle = pickHandle(event.x, event.y)
                lastX = event.x
                lastY = event.y
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val h = handle ?: return true
                val key = selectedKey ?: return true
                val item = items.firstOrNull { it.key == key } ?: return true
                val updated = RectEdit.apply(item.rect, h, (event.x - lastX) / frame.w, (event.y - lastY) / frame.h)
                lastX = event.x
                lastY = event.y
                if (updated != item.rect) {
                    items = items.map { if (it.key == key) it.copy(rect = updated) else it }
                    listener?.onItemChanged(key, updated)
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                handle = null
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                handle = null
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean = super.performClick()

    private fun pickHandle(x: Float, y: Float): RectEdit.Handle? {
        // Corners of the current selection win, so small boxes stay resizable.
        items.firstOrNull { it.key == selectedKey }?.let { sel ->
            val r = toPx(sel.rect, scratch)
            val handles = listOf(
                RectEdit.Handle.TOP_LEFT, RectEdit.Handle.TOP_RIGHT,
                RectEdit.Handle.BOTTOM_LEFT, RectEdit.Handle.BOTTOM_RIGHT,
            )
            corners(r).forEachIndexed { i, (cx, cy) ->
                if (hypot(x - cx, y - cy) <= touchRadius) return handles[i]
            }
        }
        // Topmost (last drawn) box under the finger.
        val nx = (x - frame.x) / frame.w
        val ny = (y - frame.y) / frame.h
        val hit = items.lastOrNull { it.rect.contains(nx, ny) } ?: return null
        selectedKey = hit.key
        return RectEdit.Handle.MOVE
    }

    private fun toPx(n: NRect, out: RectF): RectF {
        out.set(frame.x + n.x * frame.w, frame.y + n.y * frame.h, frame.x + n.right * frame.w, frame.y + n.bottom * frame.h)
        return out
    }

    private fun corners(r: RectF) = listOf(r.left to r.top, r.right to r.top, r.left to r.bottom, r.right to r.bottom)

    private companion object {
        val PALETTE = intArrayOf(
            0xFF29D17D.toInt(), 0xFF3A7BFF.toInt(), 0xFFFF8A00.toInt(),
            0xFFE84393.toInt(), 0xFFFFD60A.toInt(), 0xFF00C2D1.toInt(),
        )
    }
}
