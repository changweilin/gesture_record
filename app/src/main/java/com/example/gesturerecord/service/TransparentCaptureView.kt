package com.example.gesturerecord.service

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
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

    // 上一次記錄的座標，用於距離篩選
    private var lastRecordedX = 0f
    private var lastRecordedY = 0f

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
                lastRecordedX = x
                lastRecordedY = y
                startTimeMs = System.currentTimeMillis()
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                // 只有移動距離 >= MIN_POINT_DISTANCE_SQ 才記錄，減少物件分配與重繪次數
                val dx = x - lastRecordedX
                val dy = y - lastRecordedY
                if (dx * dx + dy * dy >= MIN_POINT_DISTANCE_SQ) {
                    path.lineTo(x, y)
                    recordedPoints.add(Pair(x, y))
                    lastRecordedX = x
                    lastRecordedY = y
                    // 與 Choreographer Vsync 同步，避免超過螢幕刷新率的無效重繪
                    postInvalidateOnAnimation()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                path.lineTo(x, y)
                recordedPoints.add(Pair(x, y))
                durationMs = System.currentTimeMillis() - startTimeMs
                invalidate()
                onGestureComplete?.invoke()
            }
        }
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

    companion object {
        // 5px 距離閾值（平方值避免 sqrt 計算）
        private const val MIN_POINT_DISTANCE_SQ = 25f
    }
}
