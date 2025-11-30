package com.example.myapplication

import android.content.Context
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector

class GestureController(
    context: Context,
    private val onGestureListener: OnGestureListener
) {
    private val TAG = "GestureController"

    interface OnGestureListener {
        fun onScale(scaleFactor: Float, focusX: Float, focusY: Float)
        fun onTranslate(dx: Float, dy: Float)
        fun onDoubleTap(): Boolean
    }

    // 当前缩放状态
    var currentScale = 1f

    // 用 GestureDetector 只做双击
    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            Log.d(TAG, "doubleTap")
            return onGestureListener.onDoubleTap()
        }
    })

    // 缩放检测器
    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scaleFactor = detector.scaleFactor
            val focusX = detector.focusX
            val focusY = detector.focusY

            // 更新当前缩放
            currentScale *= scaleFactor

            Log.d(TAG, "scale: factor=$scaleFactor, focus=($focusX,$focusY), currentScale=$currentScale")
            onGestureListener.onScale(scaleFactor, focusX, focusY)
            return true
        }
    })

    // 单指拖动
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isDragging = false

    fun onTouchEvent(event: MotionEvent): Boolean {
        // 先把事件交给 detectors
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                isDragging = true
                Log.d(TAG, "ACTION_DOWN: ($lastTouchX, $lastTouchY)")
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                isDragging = false
            }

            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress && isDragging && event.pointerCount == 1) {
                    val x = event.x
                    val y = event.y
                    // 根据缩放调整拖动距离
                    val dx = (x - lastTouchX) / currentScale
                    val dy = (y - lastTouchY) / currentScale

                    lastTouchX = x
                    lastTouchY = y

                    if (dx != 0f || dy != 0f) {
                        Log.d(TAG, "ACTION_MOVE: dx=$dx, dy=$dy")
                        onGestureListener.onTranslate(dx, dy)
                    }
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                isDragging = true
                lastTouchX = event.x
                lastTouchY = event.y
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                Log.d(TAG, "ACTION_UP/CANCEL")
            }
        }

        return true
    }
}
