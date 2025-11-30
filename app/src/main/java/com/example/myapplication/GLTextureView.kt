package com.example.myapplication

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator
import android.graphics.Matrix // 【新增】用于垂直翻转 Bitmap
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class GLTextureView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs), GLSurfaceView.Renderer {

    private val TAG = "GLTextureView"
    private lateinit var textureRenderer: GLTextureRenderer
    private var bitmap: Bitmap? = null

    // 新增：手势控制器
    private lateinit var gestureController: GestureController
    private var isGestureEnabled = true

    // 新增：变换状态
    private var currentScale = 1.0f
    private var translateX = 0.0f
    private var translateY = 0.0f
    private val minScale = 0.1f
    private val maxScale = 5.0f


    // 新增：启用/禁用手势
    fun setGestureEnabled(enabled: Boolean) {
        isGestureEnabled = enabled
        Log.d(TAG, "手势${if (enabled) "启用" else "禁用"}")
    }
    // 【新增】扫光动画控制器
    private var sweepAnimator: ValueAnimator? = null

    fun getRenderer(): GLTextureRenderer {
        // 假设 textureRenderer 已经在 onSurfaceCreated 中初始化
        return textureRenderer
    }

    // 新增：用于帧捕获的回调接口
    interface BitmapReadyCallback {
        fun onBitmapReady(bitmap: Bitmap?)
    }

    init {
        setEGLContextClientVersion(2)
        setRenderer(this)

        // 【修改】将渲染模式改为只在需要时重绘，更省电
        renderMode = RENDERMODE_WHEN_DIRTY
        Log.d(TAG, "GLTextureView初始化完成（脏区渲染模式）")

        initGestureController()
    }

    // 新增：初始化手势控制器
    private fun initGestureController() {
        gestureController = GestureController(context, object : GestureController.OnGestureListener {
            override fun onScale(scaleFactor: Float, focusX: Float, focusY: Float) {
                if (isGestureEnabled) {
                    handleScale(scaleFactor, focusX, focusY)
                }
            }

            override fun onTranslate(dx: Float, dy: Float) {
                if (isGestureEnabled) {
                    handleTranslate(dx, dy)
                }
            }

            override fun onDoubleTap(): Boolean {
                if (isGestureEnabled) {
                    handleDoubleTap()
                    return true
                }
                return false
            }
        })

        // 设置触摸监听器
        setOnTouchListener { _, event ->
            gestureController.onTouchEvent(event)
            true
        }

        Log.d(TAG, "手势控制器初始化完成")
    }

    // 新增：专用于在 GL 线程上安全地更新基础纹理的函数 (用于滤镜固化)
    fun updateTexture(bitmap: Bitmap) {
        this.bitmap = bitmap // 更新主线程的 Bitmap 引用

        // 核心：使用 queueEvent 在 GL 线程上执行纹理的删除和上传，确保同步
        queueEvent {
            if (::textureRenderer.isInitialized) {
                textureRenderer.updateTextureFromBitmap(bitmap)
                // 确保 GL 线程渲染一次，显示新的纹理
                requestRender()
                Log.d(TAG, "GL 线程安全更新纹理完成")
            } else {
                Log.e(TAG, "无法更新纹理：渲染器未初始化")
            }
        }
    }

    // 设置要显示的图片
    // 【修改】：仅在渲染器未初始化时等待 onSurfaceCreated，否则立即请求更新
    fun setImage(bitmap: Bitmap) {
        if (::textureRenderer.isInitialized) {
            // 如果渲染器已存在，直接调用 updateTexture 来安全更新纹理
            updateTexture(bitmap)
        } else {
            // 首次设置，仅更新引用，等待 onSurfaceCreated 调用 updateTextureFromBitmap
            this.bitmap = bitmap
            requestRender()
        }
        Log.d(TAG, "设置图片，尺寸: ${bitmap.width}x${bitmap.height}")
    }

    // Surface创建时调用
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        Log.d(TAG, "onSurfaceCreated: Surface创建")

        // 创建纹理渲染器
        textureRenderer = GLTextureRenderer(context)
        Log.d(TAG, "纹理渲染器创建完成")

        // 加载纹理
        bitmap?.let {
            Log.d(TAG, "开始加载纹理")
            // 【修改】：调用新的 updateTextureFromBitmap
            textureRenderer.updateTextureFromBitmap(it)
            // 设置缩放范围
            textureRenderer.setScaleRange(minScale, maxScale)
            Log.d(TAG, "纹理加载完成")
        } ?: run {
            Log.w(TAG, "bitmap为null，无法加载纹理")
        }

        // 设置透明背景
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f)  // 完全透明
        Log.d(TAG, "透明背景设置完成")
    }

    // Surface尺寸改变时调用
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        Log.d(TAG, "Surface尺寸改变: ${width}x${height}")
        textureRenderer.setViewport(width, height)
    }

    // 绘制每一帧时调用
    override fun onDrawFrame(gl: GL10?) {
        // 清除颜色缓冲区
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        // 绘制纹理
        textureRenderer.draw()
    }

    // =========================================================================
    // 【新增】动态扫光动画控制
    // =========================================================================

    fun startEffectAnimation() {
        if (!::textureRenderer.isInitialized) return

        // 如果动画已在运行，先停止
        stopEffectAnimation()

        Log.d(TAG, "启动扫光动画")

        // 动画范围：从 -0.2 (屏幕左侧外) 到 1.2 (屏幕右侧外)，确保完整扫过
        sweepAnimator = ValueAnimator.ofFloat(-0.2f, 1.2f).apply {
            duration = 1500L // 1.5秒扫完一次
            repeatMode = ValueAnimator.RESTART
            repeatCount = ValueAnimator.INFINITE // 无限重复
            interpolator = LinearInterpolator() // 线性插值，平滑移动

            addUpdateListener { animator ->
                val sweepPos = animator.animatedValue as Float

                // 1. 更新渲染器中的扫光位置 (必须在 GL 线程执行，但 ValueAnimator 在主线程)
                // 因此我们使用 queueEvent 来安全地进行 GL 状态更新和渲染请求
                queueEvent {
                    textureRenderer.setSweepPosition(sweepPos)
                    requestRender() // 请求重绘，将新位置应用到着色器
                }
            }
            start()
        }
    }

    fun stopEffectAnimation() {
        if (!::textureRenderer.isInitialized) return

        sweepAnimator?.cancel()
        sweepAnimator = null

        // 停止动画后，将扫光位置重置为 0.0f，并请求一次最终渲染
        queueEvent {
            textureRenderer.setSweepPosition(0.0f) // 重置到默认位置 (或一个不影响图像的位置)
            requestRender()
        }
        Log.d(TAG, "停止扫光动画")
    }

    // =========================================================================
    // 帧捕获实现 (新增 FBO 逻辑)
    // =========================================================================

    // 【新增】辅助函数：垂直翻转Bitmap（glReadPixels的常规操作）
    private fun flipBitmap(source: Bitmap, width: Int, height: Int): Bitmap {
        // 创建一个用于垂直翻转的矩阵
        val matrix = android.graphics.Matrix()
        matrix.preScale(1.0f, -1.0f) // 垂直翻转

        // 使用原始位图作为源，创建一个新的位图
        return Bitmap.createBitmap(source, 0, 0, width, height, matrix, true)
    }

    /**
     * 【修改】离屏渲染捕获 (用于最终保存) - 避免黑边和裁剪
     */
    fun captureFrameOffScreen(callback: BitmapReadyCallback) {
        if (!::textureRenderer.isInitialized) {
            callback.onBitmapReady(null)
            return
        }

        // 获取原始图片尺寸作为 FBO 尺寸
        val (outputWidth, outputHeight) = textureRenderer.getTextureDimensions()

        if (outputWidth <= 0 || outputHeight <= 0) {
            Log.e(TAG, "图片尺寸无效，无法进行离屏渲染: ${outputWidth}x${outputHeight}")
            callback.onBitmapReady(null)
            return
        }

        Log.d(TAG, "开始离屏渲染捕获 (FBO): ${outputWidth}x${outputHeight}")

        // 确保在 GL 线程上执行所有操作
        queueEvent(Runnable { // 【修复 1】：显式使用 Runnable
            // 存储 GL 状态，以便渲染后恢复
            val oldViewport = IntArray(4)
            GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, oldViewport, 0)
            val oldFbo = IntArray(1)
            GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, oldFbo, 0)

            val fboIds = IntArray(1)
            val textureIds = IntArray(1)
            var fboId = 0
            var textureId = 0
            var renderedBitmap: Bitmap? = null

            try {
                // 1. 创建和绑定 FBO
                GLES20.glGenFramebuffers(1, fboIds, 0)
                fboId = fboIds[0]
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboId)

                // 2. 创建 FBO 纹理附件
                GLES20.glGenTextures(1, textureIds, 0)
                textureId = textureIds[0]
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
                GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, outputWidth, outputHeight, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
                GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, textureId, 0)

                // 3. 检查 FBO 状态
                if (GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) != GLES20.GL_FRAMEBUFFER_COMPLETE) {
                    Log.e(TAG, "FBO 状态不完整!")
                    // 【修复 2】：使用 return@Runnable 跳出
                    return@Runnable
                }

                // 4. 设置视口为 FBO 尺寸 (关键)
                GLES20.glViewport(0, 0, outputWidth, outputHeight)

                // 5. 清除 FBO 缓冲区
                GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f) // 清除为黑色
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

                // 6. 渲染到 FBO (调用 Renderer 的特殊方法)
                textureRenderer.drawForOffscreen(outputWidth, outputHeight)
                GLES20.glFinish() // 等待渲染完成

                // 7. 从 FBO 读取像素
                val pixelBuffer = ByteBuffer.allocateDirect(outputWidth * outputHeight * 4).order(ByteOrder.nativeOrder())
                GLES20.glReadPixels(0, 0, outputWidth, outputHeight, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixelBuffer)

                // 8. 创建 Bitmap
                renderedBitmap = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
                pixelBuffer.rewind()
                renderedBitmap!!.copyPixelsFromBuffer(pixelBuffer)

                // 9. 垂直翻转
                val finalBitmap = flipBitmap(renderedBitmap!!, outputWidth, outputHeight)
                renderedBitmap!!.recycle()
                renderedBitmap = finalBitmap // 将最终结果赋给 renderedBitmap

            } catch (e: Exception) {
                Log.e(TAG, "离屏渲染失败: ${e.message}", e)
                renderedBitmap = null
            } finally {
                // 10. 清理和恢复状态 (重要)
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, oldFbo[0]) // 恢复旧的 FBO (默认或上一个)
                GLES20.glViewport(oldViewport[0], oldViewport[1], oldViewport[2], oldViewport[3]) // 恢复视口

                if (fboId != 0) {
                    // 【修复 3】：使用 fboIds 数组进行删除
                    GLES20.glDeleteFramebuffers(1, fboIds, 0)
                }
                if (textureId != 0) {
                    // 【修复 4】：使用 textureIds 数组进行删除
                    GLES20.glDeleteTextures(1, textureIds, 0)
                }

                // 11. 回调
                post {
                    callback.onBitmapReady(renderedBitmap)
                }
            }
        })
    }

    /**
     * 【修改】公共捕获方法 (用于保存) - 默认使用离屏渲染
     */
    fun captureFrame(callback: BitmapReadyCallback) {
        captureFrameOffScreen(callback) // **保存时默认使用 FBO 离屏渲染**
    }

    /**
     * 【新增】公共固化滤镜方法 (使用屏幕捕获)
     */
    fun captureFrameForSolidify(callback: BitmapReadyCallback) {
        // 使用屏幕捕获，因为它只需要捕获当前屏幕显示的内容，无需 FBO 的复杂性
        queueEvent(Runnable { // 【修复 5】：显式使用 Runnable
            val width = textureRenderer.getViewportWidth()
            val height = textureRenderer.getViewportHeight()

            if (width <= 0 || height <= 0) {
                Log.e(TAG, "视口尺寸无效 ($width x $height)，无法捕获帧")
                post { callback.onBitmapReady(null) }
                return@Runnable // 【修复 6】：使用 return@Runnable
            }

            try {
                // 1. 从帧缓冲区读取像素
                val buffer = textureRenderer.readPixelsFromBuffer(width, height)

                // 2. 创建 Bitmap
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

                // 3. 将 ByteBuffer 中的像素数据复制到 Bitmap
                bitmap.copyPixelsFromBuffer(buffer)

                // 4. 垂直翻转
                val flippedBitmap = flipBitmap(bitmap, width, height)
                bitmap.recycle()

                // 回调主线程
                post {
                    callback.onBitmapReady(flippedBitmap)
                }

            } catch (e: Exception) {
                Log.e(TAG, "固化帧捕获失败: ${e.message}", e)
                post {
                    callback.onBitmapReady(null)
                }
            }
        })
    }
    // =========================================================================

    // 新增：处理缩放手势
    private fun handleScale(scaleFactor: Float, focusX: Float, focusY: Float) {
        val newScale = (currentScale * scaleFactor).coerceIn(minScale, maxScale)

        if (newScale != currentScale) {
            textureRenderer.setScale(scaleFactor, focusX, focusY)
            currentScale = newScale
            requestRender()

            Log.d(TAG, "缩放处理: factor=$scaleFactor, 当前缩放=$currentScale, 焦点=($focusX, $focusY)")
        }
    }

    // 新增：处理平移手势
    private fun handleTranslate(dx: Float, dy: Float) {
        translateX += dx
        translateY += dy

        textureRenderer.setTranslation(dx, dy)
        requestRender()

        Log.d(TAG, "平移处理: dx=$dx, dy=$dy, 累计平移=($translateX, $translateY)")
    }

    // 新增：处理双击手势
    private fun handleDoubleTap() {
        resetTransform()
        Log.d(TAG, "双击重置")
    }

    // 新增：处理快速滑动手势（可选功能）
    private fun handleFling(velocityX: Float, velocityY: Float) {
        // 这里可以添加惯性滑动效果
        Log.d(TAG, "快速滑动: velocityX=$velocityX, velocityY=$velocityY")

        // 简单的惯性效果（可选）
        if (Math.abs(velocityX) > 100 || Math.abs(velocityY) > 100) {
            val inertiaDx = velocityX * 0.01f
            val inertiaDy = velocityY * 0.01f

            // 应用惯性滑动
            handleTranslate(inertiaDx, inertiaDy)
        }
    }

    // 修复：设置缩放（使用正确的参数）
    fun setScale(scaleX: Float, scaleY: Float) {
        // 使用中心点作为焦点
        val focusX = width / 2f
        val focusY = height / 2f

        // 计算缩放因子
        val scaleFactorX = scaleX / currentScale
        val scaleFactorY = scaleY / currentScale

        textureRenderer.setScale(scaleFactorX, focusX, focusY)
        currentScale = scaleX
        requestRender()
        Log.d(TAG, "设置缩放: scaleX=$scaleX, scaleY=$scaleY")
    }

    // 新增：简化版缩放设置（不使用焦点）
    fun setSimpleScale(scale: Float) {
        currentScale = scale.coerceIn(minScale, maxScale)
        // 这里需要重新实现缩放逻辑，或者使用其他方法
        requestRender()
        Log.d(TAG, "设置简化缩放: scale=$currentScale")
    }

    // 设置平移
    fun setTranslation(dx: Float, dy: Float) {
        textureRenderer.setTranslation(dx, dy)
        translateX += dx
        translateY += dy
        requestRender()
        Log.d(TAG, "设置平移: dx=$dx, dy=$dy")
    }

    // 重置变换
    fun resetTransform() {
        textureRenderer.resetTransform()
        currentScale = 1.0f
        translateX = 0.0f
        translateY = 0.0f
        requestRender()
        Log.d(TAG, "重置变换")
    }

    // 新增：获取当前变换状态
    fun getTransformState(): String {
        return if (::textureRenderer.isInitialized) {
            textureRenderer.getTransformState()
        } else {
            "变换状态: 缩放=$currentScale, 平移=($translateX, $translateY)"
        }
    }

    // 新增：设置缩放范围
    fun setScaleRange(min: Float, max: Float) {
        if (::textureRenderer.isInitialized) {
            textureRenderer.setScaleRange(min, max)
        }
        Log.d(TAG, "设置缩放范围: min=$min, max=$max")
    }

    // 新增：获取当前缩放值
    fun getCurrentScale(): Float = currentScale

    // 新增：获取当前平移值
    fun getCurrentTranslation(): Pair<Float, Float> = Pair(translateX, translateY)

    // 视图从窗口分离时调用（清理资源）
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        // 停止动画
        stopEffectAnimation()

        // 安全检查：确保textureRenderer已初始化
        if (::textureRenderer.isInitialized) {
            textureRenderer.cleanup()
            Log.d(TAG, "GLTextureView已销毁，资源已清理")
        } else {
            Log.w(TAG, "GLTextureView已销毁，但textureRenderer未初始化")
        }
    }
}