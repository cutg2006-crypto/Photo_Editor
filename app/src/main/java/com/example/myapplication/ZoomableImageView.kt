package com.example.myapplication

import android.content.Context
import android.graphics.Matrix
import android.graphics.PointF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView

class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    // 矩阵用于处理缩放和平移
    private val matrix = Matrix()
    private val savedMatrix = Matrix()
    private val initialMatrix = Matrix()

    // 手势检测器
    private val scaleGestureDetector: ScaleGestureDetector
    private val gestureDetector: GestureDetector

    // 触摸点状态
    private val start = PointF()
    private val mid = PointF()

    // 缩放限制
    private var minScale = 0.1f
    private var maxScale = 10.0f
    private var currentScale = 1.0f

    init {
        // 设置缩放类型为矩阵
        scaleType = ScaleType.MATRIX

        // 初始化手势检测器
        scaleGestureDetector = ScaleGestureDetector(context, ScaleListener())
        gestureDetector = GestureDetector(context, GestureListener())

        // 设置触摸监听
        setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)

            val action = event.action and MotionEvent.ACTION_MASK

            when (action) {
                MotionEvent.ACTION_DOWN -> {
                    savedMatrix.set(matrix)
                    start.set(event.x, event.y)
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    savedMatrix.set(matrix)
                    midPoint(mid, event)
                }
                MotionEvent.ACTION_MOVE -> {
                    if (event.pointerCount == 1 && !scaleGestureDetector.isInProgress) {
                        matrix.set(savedMatrix)
                        val dx = event.x - start.x
                        val dy = event.y - start.y
                        matrix.postTranslate(dx, dy)
                        setImageMatrix(matrix)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                    savedMatrix.set(matrix)
                }
            }
            true
        }
    }

    // 计算两个触摸点的中点
    private fun midPoint(point: PointF, event: MotionEvent) {
        val x = (event.getX(0) + event.getX(1)) / 2
        val y = (event.getY(0) + event.getY(1)) / 2
        point.set(x, y)
    }

    // 缩放监听器
    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        private var scaleFactor = 1.0f

        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            savedMatrix.set(matrix)
            scaleFactor = 1.0f
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleFactor *= detector.scaleFactor

            var newScale = 1.0f * scaleFactor
            newScale = newScale.coerceIn(minScale, maxScale)

            val focusX = detector.focusX
            val focusY = detector.focusY

            matrix.set(initialMatrix)
            matrix.postScale(newScale, newScale, focusX, focusY)
            setImageMatrix(matrix)

            currentScale = newScale

            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            super.onScaleEnd(detector)
            initialMatrix.set(matrix)
        }
    }

    // 手势监听器
    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            reset()
            return true
        }
    }

    // 重置图片并自适应居中显示
    fun reset() {
        matrix.reset()

        val drawable = drawable
        if (drawable != null) {
            val drawableWidth = drawable.intrinsicWidth
            val drawableHeight = drawable.intrinsicHeight
            val viewWidth = width
            val viewHeight = height

            if (drawableWidth > 0 && drawableHeight > 0 && viewWidth > 0 && viewHeight > 0) {
                // 计算适合的缩放比例（保持宽高比）
                val scaleX = viewWidth.toFloat() / drawableWidth
                val scaleY = viewHeight.toFloat() / drawableHeight
                val scale = Math.min(scaleX, scaleY).coerceAtMost(1.0f) // 不超过原始大小

                // 应用缩放
                matrix.postScale(scale, scale)
                currentScale = scale

                // 计算平移量使其居中
                val scaledWidth = drawableWidth * scale
                val scaledHeight = drawableHeight * scale
                val translateX = (viewWidth - scaledWidth) / 2
                val translateY = (viewHeight - scaledHeight) / 2

                matrix.postTranslate(translateX, translateY)
            }
        }

        setImageMatrix(matrix)
        initialMatrix.set(matrix) // 保存当前状态作为初始状态
        invalidate()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        // 延迟重置，确保尺寸正确计算
        post {
            reset()
        }
    }
}