package com.example.gesturerecord.service

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.Log
import android.view.MotionEvent
import android.view.View

class TransparentCaptureView(context: Context) : View(context) {

    private val paint = Paint().apply {
        color = Color.RED
        strokeWidth = 10f
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val path = Path()
    
    // To store gesture raw data for AccessibilityService path building
    val recordedPoints = mutableListOf<Pair<Float, Float>>()
    var startTimeMs: Long = 0
    var durationMs: Long = 0

    var onGestureComplete: (() -> Unit)? = null

    init {
        setBackgroundColor(Color.parseColor("#44000000")) // Semi-transparent to indicate recording mode
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                path.moveTo(x, y)
                recordedPoints.clear()
                recordedPoints.add(Pair(x, y))
                startTimeMs = System.currentTimeMillis()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                path.lineTo(x, y)
                recordedPoints.add(Pair(x, y))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                path.lineTo(x, y)
                recordedPoints.add(Pair(x, y))
                durationMs = System.currentTimeMillis() - startTimeMs
                onGestureComplete?.invoke()
            }
        }
        invalidate()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawPath(path, paint)
    }
    
    fun reset() {
        path.reset()
        recordedPoints.clear()
        invalidate()
    }
}
