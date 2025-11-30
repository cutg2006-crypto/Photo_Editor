package com.example.myapplication

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.Matrix
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class GLTextureRenderer(private val context: Context) {

    private val TAG = "GLTextureRenderer"

    // 顶点着色器代码 - 绘制纹理的正方形
    private val vertexShaderCode = """
        attribute vec4 aPosition;
        attribute vec2 aTexCoord;
        varying vec2 vTexCoord;
        uniform mat4 uMVPMatrix;
        void main() {
            gl_Position = uMVPMatrix * aPosition;
            vTexCoord = aTexCoord;
        }
    """.trimIndent()

    // 【修改】片段着色器代码 - 纹理采样、动态扫光、滤镜和强度控制
    private val fragmentShaderCode = """
        precision mediump float;
        varying vec2 vTexCoord;
        uniform sampler2D uTexture;
        uniform float uSweepPos; // 扫光中心位置 (0.0 到 1.0)
        uniform int uFilterMode; // 滤镜模式: 0=无, 1=灰度, 2=复古, 3=饱和度
        uniform float uFilterIntensity; // 滤镜强度 (0.0 到 1.0)
        
        // 饱和度调整函数
        vec3 adjustSaturation(vec3 color, float ratio) {
            float L = dot(color, vec3(0.299, 0.587, 0.114)); // 亮度
            // Saturation formula: color = L + ratio * (color - L)
            return L + ratio * (color - L);
        }
        
        void main() {
            // 1. 采样原始颜色
            vec4 color = texture2D(uTexture, vTexCoord);
            vec3 processedColor = color.rgb;
            
            // --- 2. 滤镜处理 (根据 uFilterMode) ---
            if (uFilterMode > 0 && uFilterIntensity > 0.0) {
                if (uFilterMode == 1) { // 灰度 (Grayscale)
                    float gray = dot(color.rgb, vec3(0.299, 0.587, 0.114));
                    // 强度控制：0.0为原色，1.0为完全灰度
                    processedColor = mix(color.rgb, vec3(gray), uFilterIntensity);
                    
                } else if (uFilterMode == 2) { // 复古 (Sepia)
                    // 灰度基准
                    float gray = dot(color.rgb, vec3(0.299, 0.587, 0.114));
                    // 复古色调
                    vec3 sepiaColor = vec3(gray * 1.2, gray * 1.0, gray * 0.8);
                    // 强度控制：0.0为原色，1.0为完全复古
                    processedColor = mix(color.rgb, sepiaColor, uFilterIntensity);
                    
                } else if (uFilterMode == 3) { // 饱和度 (Saturation)
                    // 强度控制：0.0到1.0。将这个范围映射到 1.0 (原色) 到 2.0 (高饱和)
                    float satFactor = 1.0 + uFilterIntensity * 1.0; 
                    processedColor = adjustSaturation(color.rgb, satFactor);
                    // 确保颜色值不超过 1.0
                    processedColor = clamp(processedColor, 0.0, 1.0);
                }
            }
            
            // --- 3. 动态扫光 (如果开启，在滤镜之后应用) ---
            if (uSweepPos > 0.0) { 
                float dist = abs(vTexCoord.x - uSweepPos);
                
                float band_width = 0.1; 
                float max_glare = 0.8;  
                
                float glare_factor = 1.0 - smoothstep(0.0, band_width, dist);
                float final_gain = 1.0 + max_glare * glare_factor;
                
                // 应用增益到颜色
                processedColor.rgb *= final_gain;
            }
            
            gl_FragColor = vec4(processedColor, color.a);
        }
    """.trimIndent()

    // 初始顶点坐标（会被 updateVertexCoords 覆盖）
    private var squareCoords = floatArrayOf(
        -1.0f,  1.0f, 0.0f,   // 左上
        -1.0f, -1.0f, 0.0f,   // 左下
        1.0f, -1.0f, 0.0f,   // 右下
        1.0f,  1.0f, 0.0f    // 右上
    )

    // 纹理坐标（通常 v 方向可能需要翻转，视你的纹理来源决定）
    private val textureCoords = floatArrayOf(
        0.0f, 0.0f,  // 左上
        0.0f, 1.0f,  // 左下
        1.0f, 1.0f,  // 右下
        1.0f, 0.0f   // 右上
    )


    private val drawOrder = shortArrayOf(0, 1, 2, 0, 2, 3)

    // GLTextureRenderer.kt (文件顶部变量定义区)

// ... (在 private var vertexBuffer: FloatBuffer 之前新增)

    // 【新增】用于 FBO 渲染的完整的 NDC 空间顶点坐标 (-1.0 到 1.0)
    private val fullQuadCoords = floatArrayOf(
        -1.0f,  1.0f, 0.0f,   // 左上
        -1.0f, -1.0f, 0.0f,   // 左下
        1.0f, -1.0f, 0.0f,   // 右下
        1.0f,  1.0f, 0.0f    // 右上
    )
    private val fullQuadBuffer: FloatBuffer

    // OpenGL变量
    private var program: Int = 0
    private var textureID: Int = 0

    // 属性位置
    private var positionHandle: Int = 0
    private var texCoordHandle: Int = 0
    private var mvpMatrixHandle: Int = 0

    // 【新增】扫光特效 uniform 位置
    private var sweepPosHandle: Int = 0

    // 【新增】滤镜模式 uniform 位置
    private var filterModeHandle: Int = 0

    // 【新增】滤镜强度 uniform 位置
    private var filterIntensityHandle: Int = 0


    // 缓冲区
    private var vertexBuffer: FloatBuffer
    private val textureBuffer: FloatBuffer
    private val indexBuffer: ByteBuffer

    // 变换矩阵
    private val mvpMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)

    // 新增：变换状态
    private val initialModelMatrix = FloatArray(16)
    private var currentScaleX = 1.0f
    private var currentScaleY = 1.0f
    private var currentTranslateX = 0.0f
    private var currentTranslateY = 0.0f
    private var minScale = 0.1f
    private var maxScale = 5.0f

    // 【新增】扫光位置状态
    private var sweepPosition: Float = 0.0f // 扫光中心位置，0.0到1.0

    // 【新增】滤镜模式状态
    private var currentFilterMode: Int = 0 // 0 = 无，1 = 灰度，2 = 复古，3 = 饱和度

    // 【新增】滤镜强度状态 (0.0 到 1.0)
    private var currentFilterIntensity: Float = 0.0f

    // =========================================================================
    // 【新增/修改】尺寸和翻转状态变量
    // =========================================================================

    // 存储图片原始尺寸
    var textureWidth: Int = 0
        private set
    var textureHeight: Int = 0
        private set

    // 存储 Viewport 尺寸
    private var viewportWidth = 1
    private var viewportHeight = 1

    // 记录翻转状态，用于 FBO 渲染时重建 Model Matrix
    private var isFlippedH: Boolean = false
    private var isFlippedV: Boolean = false

    // =========================================================================


    init {
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.setIdentityM(viewMatrix, 0)
        Matrix.setIdentityM(projectionMatrix, 0)
        Matrix.setIdentityM(mvpMatrix, 0)
        Matrix.setIdentityM(initialModelMatrix, 0)

        // 初始化顶点缓冲区（会在 setViewport/loadTexture 时更新内容）
        vertexBuffer = ByteBuffer.allocateDirect(squareCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(squareCoords)
                position(0)
            }

        textureBuffer = ByteBuffer.allocateDirect(textureCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(textureCoords)
                position(0)
            }

        val shortBuffer = ByteBuffer.allocateDirect(drawOrder.size * 2)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
            .put(drawOrder)
        shortBuffer.position(0)

        indexBuffer = ByteBuffer.allocateDirect(drawOrder.size * 2)
            .order(ByteOrder.nativeOrder())
        indexBuffer.asShortBuffer().put(shortBuffer)
        indexBuffer.position(0)
        fullQuadBuffer = ByteBuffer.allocateDirect(fullQuadCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(fullQuadCoords)
                position(0)
            }
        initOpenGL()
        Log.d(TAG, "GLTextureRenderer 初始化完成")
    }

    private fun initOpenGL() {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

        program = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)

            val linkStatus = IntArray(1)
            GLES20.glGetProgramiv(it, GLES20.GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] == 0) {
                Log.e(TAG, "着色器程序链接失败: ${GLES20.glGetProgramInfoLog(it)}")
                GLES20.glDeleteProgram(it)
            } else {
                Log.d(TAG, "着色器程序链接成功")
            }
        }

        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        texCoordHandle = GLES20.glGetAttribLocation(program, "aTexCoord")
        mvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")

        // 获取扫光位置 uniform 句柄
        sweepPosHandle = GLES20.glGetUniformLocation(program, "uSweepPos")

        // 获取滤镜模式 uniform 句柄
        filterModeHandle = GLES20.glGetUniformLocation(program, "uFilterMode")

        // 【新增】获取滤镜强度 uniform 句柄
        filterIntensityHandle = GLES20.glGetUniformLocation(program, "uFilterIntensity")


        Log.d(TAG, "属性位置获取完成: position=$positionHandle, texCoord=$texCoordHandle, matrix=$mvpMatrixHandle, sweepPos=$sweepPosHandle, filterMode=$filterModeHandle, filterIntensity=$filterIntensityHandle")
    }

    // 【修改】 loadTexture / updateTextureFromBitmap
    fun updateTextureFromBitmap(bitmap: Bitmap): Int {
        // 1. 如果存在旧纹理，先删除它
        if (textureID != 0) {
            // 注意：必须在 GL 线程执行删除
            GLES20.glDeleteTextures(1, intArrayOf(textureID), 0)
            Log.d(TAG, "已删除旧纹理 ID: $textureID")
            textureID = 0
        }

        // 2. 生成新纹理
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)

        textureID = textures[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureID)

        // 设置纹理参数
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        // 3. 上传 Bitmap 数据
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)

        // 【修改】：保存图片原始尺寸
        textureWidth = bitmap.width
        textureHeight = bitmap.height

        Log.d(TAG, "纹理加载/更新完成，ID: $textureID, 图片尺寸: ${textureWidth}x${textureHeight}")

        // 关键：更新顶点以保证比例
        if (viewportWidth > 0 && viewportHeight > 0) {
            updateVertexCoords()
        }

        return textureID
    }

    // 设置视口并根据图片比例调整投影 & 顶点
    fun setViewport(width: Int, height: Int) {
        // 【修改】：保存 viewport 尺寸
        viewportWidth = width
        viewportHeight = height
        GLES20.glViewport(0, 0, width, height)

        // 保持正交投影用于基本绘制
        Matrix.setIdentityM(projectionMatrix, 0)
        // 【修改】：为了 Aspect Fit，这里需要根据宽高比调整投影范围，而不是固定 -1 到 1
        // 注意：原代码中使用的是固定的 -1 到 1，但 updateVertexCoords 已经做了 Aspect Fit 修正。
        // 为了兼容性，这里保留原有的 Matrix.orthoM，但如果您的实际项目中有更复杂的投影逻辑，应在此处修正。
        // 这里沿用原有的 Matrix.orthoM，依赖 updateVertexCoords 进行 Aspect Fit
        Matrix.orthoM(projectionMatrix, 0, -1f, 1f, -1f, 1f, -1f, 1f)

        // 更新顶点坐标以匹配图片与视口的比例，避免拉伸
        updateVertexCoords()

        Log.d(TAG, "设置视口: ${width}x$height")
    }

    // *** 关键函数：根据图片与视口比率更新顶点坐标，保证不拉伸 ***
    private fun updateVertexCoords() {
// ... (此函数内容保持不变) ...
        if (viewportWidth <= 0 || viewportHeight <= 0) return

        val viewportRatio = viewportWidth.toFloat() / viewportHeight.toFloat()
        val imageRatio = if (textureWidth > 0 && textureHeight > 0) {
            textureWidth.toFloat() / textureHeight.toFloat()
        } else {
            1f
        }

        // 计算在 NDC（-1..1）空间中 quad 的半宽/半高
        // 如果图片比视口“更宽”，按宽度撑满（x 范围 -1..1），高度缩放
        // 否则按高度撑满，宽度缩放
        val halfWidth: Float
        val halfHeight: Float

        if (imageRatio >= viewportRatio) {
            // 图片相对更宽：宽度撑满
            halfWidth = 1.0f
            // 计算高度: (viewportRatio / imageRatio)
            halfHeight = viewportRatio / imageRatio
        } else {
            // 图片相对更高：高度撑满
            halfHeight = 1.0f
            halfWidth = imageRatio / viewportRatio
        }

        // 顶点按 (x,y) 顺序： 左上, 左下, 右下, 右上（与原 drawOrder 对应）
        squareCoords = floatArrayOf(
            -halfWidth,  halfHeight, 0.0f,   // 左上
            -halfWidth, -halfHeight, 0.0f,   // 左下
            halfWidth, -halfHeight, 0.0f,   // 右下
            halfWidth,  halfHeight, 0.0f    // 右上
        )

        // 更新 vertexBuffer 内容
        vertexBuffer.clear()
        vertexBuffer.put(squareCoords)
        vertexBuffer.position(0)

        Log.d(TAG, "更新顶点坐标: halfW=$halfWidth, halfH=$halfHeight, imageRatio=$imageRatio, viewportRatio=$viewportRatio")
    }

    // 绘制 (实时渲染)
    fun draw() {
        Log.d(TAG, "GLTextureRenderer.draw() 开始")

        try {
            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

            GLES20.glClearColor(0f, 0f, 0f, 0f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            GLES20.glUseProgram(program)

            // 传递 Uniforms
            if (sweepPosHandle >= 0) {
                GLES20.glUniform1f(sweepPosHandle, sweepPosition)
            }
            // 【新增】传递滤镜模式
            if (filterModeHandle >= 0) {
                GLES20.glUniform1i(filterModeHandle, currentFilterMode)
            }
            // 【新增】传递滤镜强度
            if (filterIntensityHandle >= 0) {
                GLES20.glUniform1f(filterIntensityHandle, currentFilterIntensity)
            }


            // 计算MVP: projection * model (实时渲染使用屏幕投影矩阵)
            Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, modelMatrix, 0)
            GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)

            // 顶点属性
            GLES20.glEnableVertexAttribArray(positionHandle)
            GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)

            GLES20.glEnableVertexAttribArray(texCoordHandle)
            GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, textureBuffer)

            // 绑定纹理并绘制
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureID)
            GLES20.glDrawElements(GLES20.GL_TRIANGLES, drawOrder.size, GLES20.GL_UNSIGNED_SHORT, indexBuffer)

            GLES20.glDisableVertexAttribArray(positionHandle)
            GLES20.glDisableVertexAttribArray(texCoordHandle)

            GLES20.glDisable(GLES20.GL_BLEND)

            val error = GLES20.glGetError()
            if (error != GLES20.GL_NO_ERROR) {
                Log.e(TAG, "OpenGL错误: $error")
            } else {
                Log.d(TAG, "绘制完成，无错误")
            }

        } catch (e: Exception) {
            Log.e(TAG, "绘制过程中发生错误: ${e.message}", e)
        }
    }

    // =========================================================================
    // 【修改】用于离屏渲染的特殊绘制方法 - 彻底修复拉伸问题
    // 逻辑：忽略用户的缩放/平移，只保留翻转，并将图像 1:1 填满 FBO
    // =========================================================================
    fun drawForOffscreen(outputWidth: Int, outputHeight: Int) {
        if (textureID == 0 || textureWidth == 0 || textureHeight == 0) {
            Log.e(TAG, "无法进行离屏渲染：纹理或尺寸无效")
            return
        }

        // 1. 声明矩阵
        val finalMVPMatrix = FloatArray(16)
        val fboProjectionMatrix = FloatArray(16)
        val viewMatrix = FloatArray(16)
        val tempVPMatrix = FloatArray(16)

        // 2. Projection Matrix (P): 使用正交投影 (-1 到 1)，对应 FBO 的整个空间
        Matrix.setIdentityM(fboProjectionMatrix, 0)
        Matrix.orthoM(fboProjectionMatrix, 0, -1f, 1f, -1f, 1f, -1f, 1f)

        // 3. View Matrix (V): 单位矩阵
        Matrix.setIdentityM(viewMatrix, 0)

        // 4. Model Matrix (M): 【关键修正】
        // 我们不使用那个包含了缩放/平移的 `modelMatrix`。
        // 我们创建一个新的临时的 Model 矩阵，只应用翻转效果。
        val fboModelMatrix = FloatArray(16)
        Matrix.setIdentityM(fboModelMatrix, 0)

        // 应用翻转状态 (只应用翻转，忽略缩放和平移)
        if (isFlippedH) {
            Matrix.scaleM(fboModelMatrix, 0, -1.0f, 1.0f, 1.0f)
        }
        if (isFlippedV) {
            Matrix.scaleM(fboModelMatrix, 0, 1.0f, -1.0f, 1.0f)
        }

        // 5. 计算最终 MVP
        Matrix.multiplyMM(tempVPMatrix, 0, fboProjectionMatrix, 0, viewMatrix, 0)
        Matrix.multiplyMM(finalMVPMatrix, 0, tempVPMatrix, 0, fboModelMatrix, 0)

        // 6. 渲染
        GLES20.glUseProgram(program)

        // 激活纹理
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureID)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uTexture"), 0)

        // 传递 MVP 矩阵
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, finalMVPMatrix, 0)

        // 【关键】：使用 fillQuadBuffer (填满 NDC 空间)，而不是为了 Aspect Fit 而缩小的 vertexBuffer
        fullQuadBuffer.position(0)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, fullQuadBuffer)

        // 纹理坐标 (保持不变)
        GLES20.glEnableVertexAttribArray(texCoordHandle)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, textureBuffer)

        // 传递滤镜和扫光参数
        GLES20.glUniform1f(sweepPosHandle, sweepPosition)
        GLES20.glUniform1i(filterModeHandle, currentFilterMode)
        GLES20.glUniform1f(filterIntensityHandle, currentFilterIntensity)

        // 绘制
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, drawOrder.size, GLES20.GL_UNSIGNED_SHORT, indexBuffer)

        // 禁用顶点数组
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
    }

    // =========================================================================
    // 图片保存/帧捕获逻辑
    // =========================================================================

    // 增加：读取当前帧缓冲区像素到 ByteBuffer
    // 注意：此函数必须在 GL 线程调用
    fun readPixelsFromBuffer(width: Int, height: Int): ByteBuffer {
        // 创建 ByteBuffer 用于存储像素数据 (RGBA, 4 bytes/pixel)
        val buffer = ByteBuffer.allocateDirect(width * height * 4)
        buffer.order(ByteOrder.nativeOrder())

        // 从 GL 帧缓冲区读取像素。结果是上下颠倒的。
        GLES20.glReadPixels(
            0, 0, width, height,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer
        )

        buffer.position(0)
        return buffer
    }

    // =========================================================================

    // 缩放与平移方法保持不变
    fun setScale(scaleFactor: Float, focusX: Float, focusY: Float) {
        val newScaleX = currentScaleX * scaleFactor
        val newScaleY = currentScaleY * scaleFactor
        val clampedScaleX = newScaleX.coerceIn(minScale, maxScale)
        val clampedScaleY = newScaleY.coerceIn(minScale, maxScale)

        if (clampedScaleX != currentScaleX || clampedScaleY != currentScaleY) {
            // 将屏幕焦点转换为 NDC（近似转换，已考虑视口宽高比）
            val ndcX = (focusX / viewportWidth) * 2f - 1f
            val ndcY = 1f - (focusY / viewportHeight) * 2f

            Matrix.translateM(modelMatrix, 0, ndcX, ndcY, 0f)
            Matrix.scaleM(modelMatrix, 0, clampedScaleX / currentScaleX, clampedScaleY / currentScaleY, 1f)
            Matrix.translateM(modelMatrix, 0, -ndcX, -ndcY, 0f)

            currentScaleX = clampedScaleX
            currentScaleY = clampedScaleY
            Log.d(TAG, "缩放: factor=$scaleFactor, 当前缩放=($currentScaleX, $currentScaleY), 焦点=($focusX, $focusY)")
        }
    }

    fun setTranslation(dx: Float, dy: Float) {
        val glDx = dx / viewportWidth * 2 * (viewportWidth.toFloat() / viewportHeight)
        val glDy = -dy / viewportHeight * 2
        Matrix.translateM(modelMatrix, 0, glDx, glDy, 0f)
        currentTranslateX += glDx
        currentTranslateY += glDy
        Log.d(TAG, "平移: dx=$dx, dy=$dy, 累计平移=($currentTranslateX, $currentTranslateY)")
    }

    // 【新增】扫光位置设置
    fun setSweepPosition(position: Float) {
        this.sweepPosition = position
    }

    // 【新增】滤镜模式设置
    fun setFilterMode(mode: Int) {
        if (mode == 0) {
            // 如果关闭滤镜，重置强度
            this.currentFilterIntensity = 0.0f
        }
        this.currentFilterMode = mode
    }

    // 【新增】滤镜强度设置
    fun setFilterIntensity(intensity: Float) {
        this.currentFilterIntensity = intensity.coerceIn(0.0f, 1.0f)
    }


    fun setScaleRange(min: Float, max: Float) {
        minScale = min
        maxScale = max
        Log.d(TAG, "设置缩放范围: min=$minScale, max=$maxScale")
    }

    fun getTransformState(): String {
        return "缩放: (${String.format("%.2f", currentScaleX)}, ${String.format("%.2f", currentScaleY)}), " +
                "平移: (${String.format("%.2f", currentTranslateX)}, ${String.format("%.2f", currentTranslateY)})"
    }

    fun getViewportWidth(): Int = viewportWidth
    fun getViewportHeight(): Int = viewportHeight

    // 【新增】获取图片尺寸
    fun getTextureDimensions(): Pair<Int, Int> = Pair(textureWidth, textureHeight)


    // =========================================================================
    // 翻转方法 (新增追踪翻转状态)
    // =========================================================================

    // 水平翻转 (沿着 Y 轴翻转，即 X 缩放 -1)
    fun flipHorizontally() {
        // 在模型矩阵上应用缩放变换 (1.0f 保持Y不变, -1.0f 翻转X)
        Matrix.scaleM(modelMatrix, 0, -1.0f, 1.0f, 1.0f)
        isFlippedH = !isFlippedH // 追踪状态
        Log.d(TAG, "执行水平翻转: ${if (isFlippedH) "已翻转" else "已恢复"}")
    }

    // 垂直翻转 (沿着 X 轴翻转，即 Y 缩放 -1)
    fun flipVertically() {
        // 在模型矩阵上应用缩放变换 (1.0f 保持X不变, -1.0f 翻转Y)
        Matrix.scaleM(modelMatrix, 0, 1.0f, -1.0f, 1.0f)
        isFlippedV = !isFlippedV // 追踪状态
        Log.d(TAG, "执行垂直翻转: ${if (isFlippedV) "已翻转" else "已恢复"}")
    }

    // =========================================================================

    // =========================================================================
    // 【新增】用于翻转确认/取消逻辑：仅重置翻转状态
    // =========================================================================
    fun resetFlipFlags() {
        isFlippedH = false
        isFlippedV = false
        Log.d(TAG, "翻转状态已重置")
    }

    fun resetTransform() {
        System.arraycopy(initialModelMatrix, 0, modelMatrix, 0, 16)
        currentScaleX = 1.0f
        currentScaleY = 1.0f
        currentTranslateX = 0.0f
        currentTranslateY = 0.0f

        // 重置翻转状态
        isFlippedH = false
        isFlippedV = false

        // 【新增】重置滤镜和扫光
        setFilterMode(0)
        setSweepPosition(0.0f)

        Log.d(TAG, "重置变换完成")
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)

            val compileStatus = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
            if (compileStatus[0] == 0) {
                Log.e(TAG, "着色器编译失败: ${GLES20.glGetProgramInfoLog(shader)}")
                GLES20.glDeleteProgram(shader)
            } else {
                Log.d(TAG, "着色器编译成功")
            }
        }
    }

    fun cleanup() {
        try {
            if (textureID != 0) {
                GLES20.glDeleteTextures(1, intArrayOf(textureID), 0)
                textureID = 0
                Log.d(TAG, "纹理已清理")
            }

            if (program != 0) {
                GLES20.glDeleteProgram(program)
                program = 0
                Log.d(TAG, "着色器程序已清理")
            }
        } catch (e: Exception) {
            Log.e(TAG, "清理资源时发生错误", e)
        }
    }
}