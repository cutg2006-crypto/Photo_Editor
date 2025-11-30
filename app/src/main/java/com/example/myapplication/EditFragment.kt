package com.example.myapplication

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import coil.load
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

class EditFragment : Fragment(R.layout.fragment_edit) {
    private val TAG = "EditFragment"

    // 【新增】撤销/重做历史栈
    // 使用 ArrayDeque 作为栈结构
    private val undoStack = java.util.ArrayDeque<Bitmap>()
    private val redoStack = java.util.ArrayDeque<Bitmap>()
    // 最大历史步数，防止 OOM
    private val MAX_HISTORY_SIZE = 10

    private lateinit var glTextureView: GLTextureView
    private lateinit var imageView: ZoomableImageView
    private lateinit var tvLoading: TextView
    private var currentBitmap: Bitmap? = null
    private var currentMimeType: String = "image/jpeg"
    private var currentImageUri: String? = null

    // 新增：手势状态
    private var isGestureEnabled = true
    private var currentScale = 1.0f
    private var isOpenGLMode = false

    // 新增：翻转菜单视图
    private lateinit var flipMenuContainer: View
    private lateinit var btnFlipHorizontal: Button
    private lateinit var btnFlipVertical: Button
    // 【新增】翻转确认/取消按钮
    private lateinit var btnFlipConfirm: Button
    private lateinit var btnFlipCancel: Button
    // 【新增】滤镜菜单视图
    private lateinit var filterMenuContainer: View
    private lateinit var btnFilterGrayscale: Button
    private lateinit var btnFilterSepia: Button
    private lateinit var btnFilterSaturation: Button
    private lateinit var sbFilterIntensity: SeekBar
    private lateinit var tvFilterIntensity: TextView

    // 【新增】滤镜确认/取消按钮
    private lateinit var btnFilterConfirm: Button
    private lateinit var btnFilterCancel: Button

    // ... 成员变量区域 ...
    // 【新增】处理裁剪页面返回的结果


    // 【新增】滤镜和特效状态
    private var isEffectActive: Boolean = false
    private var currentFilterMode: Int = 0 // 0=无, 1=灰度, 2=复古, 3=饱和度
    private var currentFilterIntensity: Float = 0.0f // 0.0到1.0

    // 新增：记录当前激活的功能菜单（用于管理子菜单的显示/隐藏）
    private var activeMenu: String? = null // 可以是 "flip", "filter" 等

    // 【新增】用于覆盖旧提示的 Toast 实例
    private var currentToast: Toast? = null

    /**
     * 【新增】保存当前状态到 Undo 栈
     * 在每次破坏性修改（如固化滤镜、翻转）之前调用
     */
    private fun saveCurrentStateToHistory() {
        val bitmapToSave = currentBitmap ?: return

        // 1. 如果 Undo 栈已满，移除最旧的（栈底）并回收
        if (undoStack.size >= MAX_HISTORY_SIZE) {
            val oldBitmap = undoStack.removeLast() // removeLast 对应栈底
            oldBitmap.recycle()
        }

        // 2. 复制当前 Bitmap 并压入 Undo 栈
        // 注意：必须复制，因为 currentBitmap 随后会被 recycle 或指向新对象
        val copyBitmap = bitmapToSave.copy(bitmapToSave.config, true)
        undoStack.push(copyBitmap)

        // 3. 清空 Redo 栈（新的分支开始了）
        while (!redoStack.isEmpty()) {
            redoStack.pop().recycle()
        }

        updateUndoRedoButtons()
        Log.d(TAG, "已保存历史记录，Undo栈大小: ${undoStack.size}")
    }


    /**
     * 【新增】处理裁剪完成后的结果
     */


    /**
     * 【新增】执行撤销操作
     */
    private fun performUndo() {
        if (undoStack.isEmpty()) {
            showToast("没有可撤销的操作")
            return
        }

        // 1. 保存当前状态到 Redo 栈
        currentBitmap?.let {
            redoStack.push(it) // 直接压入，不需要 copy，因为它是被换下来的
        }

        // 2. 从 Undo 栈恢复上一步
        val prevBitmap = undoStack.pop()
        currentBitmap = prevBitmap

        // 3. 更新显示
        if (isOpenGLMode) {
            glTextureView.updateTexture(prevBitmap)
            glTextureView.resetTransform() // 撤销后通常重置视图
        } else {
            imageView.setImageBitmap(prevBitmap)
        }

        updateUndoRedoButtons()
        showToast("已撤销")
    }

    /**
     * 【新增】执行重做操作
     */
    private fun performRedo() {
        if (redoStack.isEmpty()) {
            showToast("没有可重做的操作")
            return
        }

        // 1. 保存当前状态到 Undo 栈
        currentBitmap?.let {
            undoStack.push(it)
        }

        // 2. 从 Redo 栈恢复下一步
        val nextBitmap = redoStack.pop()
        currentBitmap = nextBitmap

        // 3. 更新显示
        if (isOpenGLMode) {
            glTextureView.updateTexture(nextBitmap)
            glTextureView.resetTransform()
        } else {
            imageView.setImageBitmap(nextBitmap)
        }

        updateUndoRedoButtons()
        showToast("已重做")
    }

    // 【新增】更新撤销/重做按钮状态
    private fun updateUndoRedoButtons() {
        val btnUndo = view?.findViewById<Button>(R.id.btn_undo)
        val btnRedo = view?.findViewById<Button>(R.id.btn_redo)

        btnUndo?.isEnabled = !undoStack.isEmpty()
        btnUndo?.alpha = if (undoStack.isEmpty()) 0.5f else 1.0f

        btnRedo?.isEnabled = !redoStack.isEmpty()
        btnRedo?.alpha = if (redoStack.isEmpty()) 0.5f else 1.0f
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "=== EditFragment.onViewCreated() ===")

        // 初始化视图
        initViews(view)

        // 获取传递的参数
        currentImageUri = getImageUriFromArguments()
        currentMimeType = getMimeTypeFromArguments()

        Log.d(TAG, "参数解析结果:")
        Log.d(TAG, "IMAGE_URI: $currentImageUri")
        Log.d(TAG, "MIME_TYPE: $currentMimeType")

        // 安全检查：GIF不应该进入这里
        if (currentMimeType == "image/gif") {
            Log.e(TAG, "错误：GIF文件进入了编辑页！")
            showToast("GIF文件不支持编辑，将返回")
            requireActivity().finish()
            return
        }

        // 加载图片
        if (!currentImageUri.isNullOrEmpty()) {
            loadImage(currentImageUri!!)
        } else {
            showError("未找到图片URI")
        }

        setupButtonClickListeners(view)
    }

    private fun getImageUriFromArguments(): String? {
        // 尝试多种方式获取图片URI
        var imageUri = arguments?.getString("IMAGE_URI")
        if (imageUri.isNullOrEmpty()) {
            imageUri = arguments?.getString("SELECTED_IMAGE_URI")
        }
        return imageUri
    }

    private fun getMimeTypeFromArguments(): String {
        // 尝试多种方式获取MIME类型
        var mimeType = arguments?.getString("MIME_TYPE")
        if (mimeType.isNullOrEmpty()) {
            mimeType = arguments?.getString("SELECTED_IMAGE_MIME_TYPE")
        }
        return mimeType ?: "image/jpeg"
    }

    private fun initViews(view: View) {
        imageView = view.findViewById(R.id.imageView)
        glTextureView = view.findViewById(R.id.glTextureView)
        tvLoading = view.findViewById(R.id.tvLoading)

        // 翻转菜单组件初始化
        flipMenuContainer = view.findViewById(R.id.flip_menu_container)
        btnFlipHorizontal = view.findViewById(R.id.btn_flip_horizontal)
        btnFlipVertical = view.findViewById(R.id.btn_flip_vertical)
        // 【新增】初始化翻转确认/取消按钮
        btnFlipConfirm = view.findViewById(R.id.btn_flip_confirm)
        btnFlipCancel = view.findViewById(R.id.btn_flip_cancel)
        // 【新增】滤镜菜单组件初始化
        filterMenuContainer = view.findViewById(R.id.filter_menu_container)
        btnFilterGrayscale = view.findViewById(R.id.btn_filter_grayscale)
        btnFilterSepia = view.findViewById(R.id.btn_filter_sepia)
        btnFilterSaturation = view.findViewById(R.id.btn_filter_saturation)
        sbFilterIntensity = view.findViewById(R.id.sb_filter_intensity)
        tvFilterIntensity = view.findViewById(R.id.tv_filter_intensity)

        // 【新增】滤镜确认/取消按钮
        btnFilterConfirm = view.findViewById(R.id.btn_filter_confirm)
        btnFilterCancel = view.findViewById(R.id.btn_filter_cancel)


        // 默认隐藏OpenGL视图和加载提示以及子菜单
        imageView.visibility = View.VISIBLE
        glTextureView.visibility = View.GONE
        tvLoading.visibility = View.GONE
        flipMenuContainer.visibility = View.GONE
        filterMenuContainer.visibility = View.GONE
        updateUndoRedoButtons()
        Log.d(TAG, "视图初始化完成")
    }

    private fun loadImage(imageUriString: String) {
        Log.d(TAG, "开始加载图片: $imageUriString (使用协程)")

        // 显示加载提示
        tvLoading.visibility = View.VISIBLE
        tvLoading.text = "加载中..."

        // 使用 lifecycleScope 在 IO 协程中执行文件读取和 Bitmap 解码
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
            val bitmap = withContext(Dispatchers.IO) {
                var inputStream: InputStream? = null
                try {
                    Log.d(TAG, "在 IO 协程中加载图片")

                    val uri = Uri.parse(imageUriString)
                    inputStream = requireContext().contentResolver.openInputStream(uri)

                    if (inputStream == null) {
                        throw Exception("Unable to open image stream, URI: $imageUriString")
                    }

                    // Decode Bitmap
                    val decodedBitmap = BitmapFactory.decodeStream(inputStream)

                    if (decodedBitmap == null) {
                        throw Exception("Bitmap decoding failed")
                    }

                    Log.d(TAG, "Bitmap decoded successfully, size: ${decodedBitmap.width}x${decodedBitmap.height}")
                    decodedBitmap

                } catch (e: Exception) {
                    Log.e(TAG, "Image loading failed: ${e.message}", e)
                    null
                } finally {
                    inputStream?.close()
                }
            }

            // Return to main thread (Dispatchers.Main) to handle the result
            tvLoading.visibility = View.GONE

            if (bitmap != null) {
                // Use OpenGL ES to display the image
                displayImageWithOpenGL(bitmap, imageUriString)
            } else {
                showError("Image loading failed, attempting fallback to Coil")
                fallbackToCoil(imageUriString)
            }
        }
    }

    // 修改：使用OpenGL ES显示图片
    private fun displayImageWithOpenGL(bitmap: Bitmap, imageUriString: String) {
        try {
            Log.d(TAG, "开始使用OpenGL ES显示图片")

            // 隐藏加载提示
            tvLoading.visibility = View.GONE

            // 显示OpenGL视图，隐藏Coil视图
            imageView.visibility = View.GONE
            glTextureView.visibility = View.VISIBLE
            isOpenGLMode = true

            // 强制设置背景色为透明
            glTextureView.setBackgroundColor(0x00000000)
            glTextureView.setImage(bitmap)
            currentBitmap = bitmap

            // 强制重绘
            glTextureView.post {
                glTextureView.requestRender()
            }

            showToast("OpenGL图片加载成功")
        } catch (e: Exception) {
            Log.e(TAG, "OpenGL ES显示图片失败: ${e.message}", e)
            fallbackToCoil(imageUriString)
        }
    }

    // 修改：回退到Coil加载
    private fun fallbackToCoil(imageUriString: String) {
        // ... (保持不变)
        Log.w(TAG, "回退到Coil加载")

        try {
            tvLoading.visibility = View.GONE
            // 显示Coil视图，隐藏OpenGL视图
            imageView.visibility = View.VISIBLE
            glTextureView.visibility = View.GONE
            isOpenGLMode = false

            imageView.load(Uri.parse(imageUriString)) {
                crossfade(true)
                listener(
                    onSuccess = { _, _ ->
                        Log.d(TAG, "Coil图片加载成功")
                        showToast("使用Coil加载成功")
                    },
                    onError = { _, result ->
                        Log.e(TAG, "Coil加载失败: ${result.throwable.message}")
                        showError("所有加载方式都失败")
                    }
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Coil回退失败: ${e.message}", e)
            showError("加载完全失败")
        }
    }

    // 封装 showToast 函数，用于取消旧的 Toast
    private fun showToast(message: String) {
        // 1. 取消正在显示的旧 Toast
        currentToast?.cancel()

        // 2. 创建并显示新的 Toast
        currentToast = Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT)
        currentToast?.show()
    }

    // 新增：显示变换状态
    private fun showTransformStatus() {
        if (isOpenGLMode && ::glTextureView.isInitialized) {
            val state = glTextureView.getTransformState()
            Log.d(TAG, "当前变换状态: $state")
        }
    }

    private fun showError(message: String) {
        tvLoading.visibility = View.GONE
        showToast(message)
        Log.e(TAG, "错误: $message")
    }

    // =========================================================================
    // 步骤 1：保存图片逻辑实现
    // =========================================================================
    // 【修改】 saveImage 移除固化逻辑，因为它会由 Confirm 按钮处理
    private fun saveImage() {
        if (isOpenGLMode && ::glTextureView.isInitialized) {
            tvLoading.visibility = View.VISIBLE
            tvLoading.text = "正在合成和保存..."

            // 停止特效
            if (isEffectActive) {
                glTextureView.stopEffectAnimation()
                isEffectActive = false
            }

            // 【关键修改点】：如果滤镜菜单打开，视为取消，并退出
            if (activeMenu == "filter") {
                filterMenuContainer.visibility = View.GONE
                activeMenu = null
                setCurrentFilter(0) // 撤销效果
                showToast("请先确认或取消滤镜调整")
                return
            }

            // 直接执行保存，因为滤镜/翻转状态始终在 GL Renderer 中保持
            executeSave(null)

        } else {
            showToast("Coil 模式不支持编辑，请使用 OpenGL 模式")
            Log.w(TAG, "尝试在 Coil 模式下保存")
        }
    }

    // 辅助函数：实际执行保存操作，避免重复代码
    // 辅助函数：实际执行保存操作，避免重复代码
    private fun executeSave(solidifiedBitmap: Bitmap?) {
        val bitmapToSave = solidifiedBitmap ?: currentBitmap

        if (bitmapToSave == null) {
            showToast("图片资源无效，无法保存")
            tvLoading.visibility = View.GONE
            return
        }

        // 【修改点】：确保调用的是 captureFrame (它现在在 GLTextureView 中指向 FBO 离屏渲染)
        // 之前我们在 GLTextureRenderer 中修复了 drawForOffscreen 的矩阵，现在可以放心使用它来保存无拉伸原图。
        glTextureView.captureFrame(object : GLTextureView.BitmapReadyCallback {
            override fun onBitmapReady(renderedBitmap: Bitmap?) {
                tvLoading.visibility = View.GONE

                if (renderedBitmap != null) {
                    // 在 IO 协程中执行保存操作
                    viewLifecycleOwner.lifecycleScope.launch {
                        val savedUri = withContext(Dispatchers.IO) {
                            saveBitmapToMediaStore(renderedBitmap)
                        }

                        // 释放 Bitmap 资源
                        renderedBitmap.recycle()
                        solidifiedBitmap?.recycle() // 释放固化时创建的临时 Bitmap

                        // 返回主线程更新 UI 和导航
                        if (savedUri != null) {
                            showToast("图片保存成功!")
                            Log.d(TAG, "图片保存成功: $savedUri")

                            // 退回到相册页面
                            requireActivity().finish()

                        } else {
                            showToast("图片保存失败")
                            Log.e(TAG, "图片保存失败，saveBitmapToMediaStore 返回 null")
                        }
                    }
                } else {
                    showToast("渲染捕获失败，无法保存")
                    Log.e(TAG, "renderedBitmap 为 null")
                }
            }
        })
    }

    /**
     * 【新增】确认翻转：捕获当前翻转后的图像为新纹理，并重置渲染器的翻转标记。
     */
    private fun commitFlipEffect() {
        if (!isOpenGLMode) return

        tvLoading.visibility = View.VISIBLE
        tvLoading.text = "正在应用翻转..."

        // 使用 FBO 捕获当前状态 (已翻转的图像)
        glTextureView.captureFrame(object : GLTextureView.BitmapReadyCallback {
            override fun onBitmapReady(renderedBitmap: Bitmap?) {
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                    if (renderedBitmap != null) {
                        // 【新增】在更新 currentBitmap 之前保存历史状态
                        saveCurrentStateToHistory()

                        currentBitmap?.recycle()
                        currentBitmap = renderedBitmap

                        // 1. 更新纹理 (传入的是已经翻转过的图片)
                        glTextureView.updateTexture(renderedBitmap)

                        // 2. 【关键修正】重置整个变换状态 (包括矩阵和Flag)
                        glTextureView.resetTransform()

                        showToast("翻转已应用")
                    } else {
                        showToast("翻转应用失败")
                        // 失败回滚：重置变换
                        glTextureView.resetTransform()
                    }

                    // 隐藏菜单
                    tvLoading.visibility = View.GONE
                    flipMenuContainer.visibility = View.GONE
                    activeMenu = null
                }
            }
        })
    }



    /**
     * 【新增】取消翻转：直接重置渲染器的翻转标记，画面瞬间复原。
     */
    private fun cancelFlipEffect() {
        if (!isOpenGLMode) return

        // 直接重置变换 (矩阵 + Flag)，不需要重新加载图片
        // 这会清除当前的翻转效果，让画面回到未翻转的状态
        glTextureView.resetTransform()

        flipMenuContainer.visibility = View.GONE
        activeMenu = null
        showToast("翻转已取消")
    }




    /**
     * 【重点修改】固化滤镜效果：将当前GL渲染结果固化为新的基础纹理，并重置滤镜状态。
     */
    private fun commitFilterEffect() {
        if (!isOpenGLMode || currentFilterMode == 0) {
            return
        }

        // 1. 禁用主控制，避免用户在异步过程中操作
        // setMainControlsEnabled(false)
        tvLoading.visibility = View.VISIBLE
        tvLoading.text = "固化滤镜效果中..."

        // 2. 捕获应用了滤镜的帧
        // 【核心修改】：改回使用 captureFrame (FBO 离屏渲染)
        glTextureView.captureFrame(object : GLTextureView.BitmapReadyCallback {
            override fun onBitmapReady(renderedBitmap: Bitmap?) {
                // UI操作和数据更新必须在主线程
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                    if (renderedBitmap != null) {
                        // 【新增】在更新 currentBitmap 之前保存历史状态
                        saveCurrentStateToHistory()

                        // 3. 更新 Fragment 的数据源 (当前位图)
                        currentBitmap?.recycle() // 释放旧的 Bitmap 内存
                        currentBitmap = renderedBitmap

                        // 4. 【核心步骤 A】安全更新 GL 纹理
                        glTextureView.updateTexture(renderedBitmap)

                        // 5. 【核心步骤 B】在 GL 线程上重置滤镜状态
                        glTextureView.queueEvent {
                            glTextureView.getRenderer().setFilterMode(0)
                            glTextureView.getRenderer().setFilterIntensity(0.0f)
                            glTextureView.getRenderer().resetFlipFlags()

                            glTextureView.requestRender()
                            Log.d(TAG, "GL 线程固化完成：滤镜模式、强度及翻转状态已重置")
                        }

                        // 6. 清除 UI 状态
                        currentFilterMode = 0
                        currentFilterIntensity = 0.0f
                        sbFilterIntensity.progress = 0
                        tvFilterIntensity.text = "强度: 0%"

                        showToast("滤镜效果已确认并固化")
                    } else {
                        showToast("固化失败：无法捕获当前画面")
                        Log.e(TAG, "固化失败：捕获帧返回 null")
                        // 失败则回退到取消状态
                        cancelFilterEffect()
                    }

                    // 7. 隐藏加载提示并恢复控制
                    tvLoading.visibility = View.GONE
                    filterMenuContainer.visibility = View.GONE
                    activeMenu = null
                    // setMainControlsEnabled(true)
                }
            }
        })
    }


    // 辅助函数：将 Bitmap 保存到 MediaStore
    private suspend fun saveBitmapToMediaStore(bitmap: Bitmap): Uri? = withContext(Dispatchers.IO) {
        val displayName = "PhotoEditor_${System.currentTimeMillis()}.jpg"
        val mimeType = "image/jpeg"

        // 兼容 Android Q (API 29) 以上和以下版本
        val imageCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            put(MediaStore.Images.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000)
            // 在 Android Q 以上，设置 IS_PENDING 标志位
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        var outputStream: OutputStream? = null
        var imageUri: Uri? = null

        try {
            // 插入新的图片文件到 MediaStore
            imageUri = requireContext().contentResolver.insert(imageCollection, contentValues)

            if (imageUri == null) {
                Log.e(TAG, "MediaStore insert 失败，返回 null URI")
                return@withContext null
            }

            // 打开输出流并写入 Bitmap
            outputStream = requireContext().contentResolver.openOutputStream(imageUri)
            if (outputStream == null) {
                Log.e(TAG, "无法打开输出流: $imageUri")
                return@withContext null
            }

            // 压缩 Bitmap 并写入流中 (质量 90)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)

            Log.d(TAG, "图片成功写入流: $imageUri")

            // Android Q (API 29) 以上清除 IS_PENDING 标志位，使文件可见
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                requireContext().contentResolver.update(imageUri, contentValues, null, null)
            }

            return@withContext imageUri

        } catch (e: Exception) {
            // 如果写入失败，尝试删除 MediaStore 条目
            imageUri?.let { uri ->
                requireContext().contentResolver.delete(uri, null, null)
            }
            Log.e(TAG, "保存图片到相册失败: ${e.message}", e)
            return@withContext null
        } finally {
            outputStream?.close()
        }
    }
    // =========================================================================

    // 【新增】仅更新 UI 状态的辅助函数 (用于固化回调)
    private fun setCurrentFilterUIOnly(mode: Int, intensity: Float = 0.0f) {
        currentFilterMode = mode
        currentFilterIntensity = if (mode == 0) 0.0f else intensity

        if (::sbFilterIntensity.isInitialized) {
            sbFilterIntensity.progress = (currentFilterIntensity * 100).toInt()
            tvFilterIntensity.text = "强度: ${(currentFilterIntensity * 100).toInt()}%"
        }
        Log.d(TAG, "设置滤镜UI ONLY: Mode=$currentFilterMode, Intensity=$currentFilterIntensity")
    }

    // 【新增】滤镜控制辅助函数
    private fun setCurrentFilter(mode: Int, intensity: Float = 0.0f) {
        // 如果 mode 为 0，则重置强度，否则使用传入的强度
        currentFilterMode = mode
        currentFilterIntensity = if (mode == 0) 0.0f else intensity

        // 确保强度滑块和文本显示正确
        if (::sbFilterIntensity.isInitialized) {
            sbFilterIntensity.progress = (currentFilterIntensity * 100).toInt()
            tvFilterIntensity.text = "强度: ${(currentFilterIntensity * 100).toInt()}%"
        }

        // 将状态推送到 GL 线程
        glTextureView.queueEvent {
            glTextureView.getRenderer().setFilterMode(currentFilterMode)
            glTextureView.getRenderer().setFilterIntensity(currentFilterIntensity)
            glTextureView.requestRender()
        }

        Log.d(TAG, "设置滤镜: Mode=$currentFilterMode, Intensity=$currentFilterIntensity")
    }

    // 修改：调整功能（现在是翻转）
    private fun adjustImage() {
        if (isOpenGLMode && ::glTextureView.isInitialized) {

            // 【新增】如果滤镜菜单开启，则固化滤镜并关闭菜单 (保持不变)
            if (activeMenu == "filter") {
                filterMenuContainer.visibility = View.GONE
                activeMenu = null
                setCurrentFilter(0) // 撤销效果
                showToast("滤镜菜单已关闭 (效果已撤销)")
                return
            }

            // 如果特效开启，则关闭它 (保持不变)
            if (isEffectActive) {
                glTextureView.stopEffectAnimation()
                isEffectActive = false
                showToast("特效已关闭")
            }

            // 切换翻转菜单的显示状态
            if (activeMenu == "flip") {
                // 【修改点】：如果已激活，则视为放弃操作
                // 调用 cancelFlipEffect() 来撤销未确认的翻转，隐藏菜单，并重置 activeMenu
                cancelFlipEffect()
                // cancelFlipEffect() 内部已经包含了 showToast("翻转已取消")，
                // 如果您想显示特定的提示，可以在这里再 Toast 一次，或者修改 cancelFlipEffect 的提示
                showToast("翻转菜单已关闭 (效果已撤销)")
            } else {
                // 如果未激活，则显示，并隐藏其他菜单
                activeMenu = "flip"
                flipMenuContainer.visibility = View.VISIBLE
                showToast("翻转菜单已打开")
            }

        } else {
            showToast("请在OpenGL模式下使用翻转功能")
        }
    }

    // 【修改】滤镜功能
    private fun applyFilter() {
        if (isOpenGLMode && ::glTextureView.isInitialized) {

            // 1. 如果翻转菜单开启，则关闭它
            if (activeMenu == "flip") {
                flipMenuContainer.visibility = View.GONE
                cancelFlipEffect()
            }

            // 2. 如果特效开启，则关闭它
            if (isEffectActive) {
                glTextureView.stopEffectAnimation()
                isEffectActive = false
                showToast("特效已关闭")
            }

            // 切换滤镜菜单的显示状态
            if (activeMenu == "filter") {
                // 如果已激活，则执行撤销并隐藏 (滤镜按钮现在是取消功能)
                activeMenu = null
                filterMenuContainer.visibility = View.GONE

                // 撤销滤镜效果
                setCurrentFilter(0)
                showToast("滤镜菜单已关闭 (效果已撤销)")

            } else {
                // 如果未激活，则显示
                activeMenu = "filter"
                filterMenuContainer.visibility = View.VISIBLE

                // 确保滤镜被关闭 (模式0)
                setCurrentFilter(0)
                showToast("滤镜菜单已打开，请选择滤镜")
            }

        } else {
            showToast("请在OpenGL模式下使用滤镜功能")
        }
    }


    private fun setupButtonClickListeners(view: View) {
        // 顶部操作按钮
        view.findViewById<Button>(R.id.btn_undo).setOnClickListener {
            if (isOpenGLMode) {
                performUndo() // 【修改】调用 performUndo
            } else {
                showToast("Coil模式不支持撤销")
            }
        }

        view.findViewById<Button>(R.id.btn_redo).setOnClickListener {
            if (isOpenGLMode) {
                performRedo() // 【修改】调用 performRedo
            } else {
                showToast("Coil模式不支持重做")
            }
        }

        view.findViewById<Button>(R.id.btn_save).setOnClickListener {
            saveImage()
        }

        // 功能选项卡按钮


        // 【修改】滤镜按钮
        view.findViewById<Button>(R.id.btn_filter).setOnClickListener {
            applyFilter()
        }

        // “翻转”按钮 (原调整按钮)
        view.findViewById<Button>(R.id.btn_adjust).setOnClickListener {
            adjustImage() // 调用切换子菜单的函数
        }

        // “特效”按钮
        view.findViewById<Button>(R.id.btn_effect).setOnClickListener {
            applyEffect()
        }

        // ===========================================
        // 翻转子菜单按钮点击事件
        // ===========================================

        btnFlipHorizontal.setOnClickListener {
            if (isOpenGLMode && ::glTextureView.isInitialized) {
                glTextureView.queueEvent {
                    glTextureView.getRenderer().flipHorizontally()
                    glTextureView.requestRender()
                }
                showToast("已应用水平翻转")
                // 翻转后保持菜单可见，方便用户继续操作
            }
        }

        btnFlipVertical.setOnClickListener {
            if (isOpenGLMode && ::glTextureView.isInitialized) {
                glTextureView.queueEvent {
                    glTextureView.getRenderer().flipVertically()
                    glTextureView.requestRender()
                }
                showToast("已应用垂直翻转")
            }
        }

        // 【新增】翻转确认按钮
        btnFlipConfirm.setOnClickListener {
            // 只有当翻转菜单打开时才处理
            if (activeMenu == "flip") {
                commitFlipEffect()
            }
        }

        // 【新增】翻转取消按钮
        btnFlipCancel.setOnClickListener {
            // 只有当翻转菜单打开时才处理
            if (activeMenu == "flip") {
                cancelFlipEffect()
            }
        }

        // ===========================================
        // 【新增】滤镜子菜单按钮点击事件
        // ===========================================

        // 滤镜按钮点击统一处理：设置模式，强度默认100% (1.0f)
        val filterClickListener = View.OnClickListener { v ->
            if (isOpenGLMode) {
                val mode = v.tag.toString().toInt()
                // 饱和度默认50%强度 (0.5f)，其他默认100% (1.0f)
                val defaultIntensity = if (mode == 3) 0.5f else 1.0f
                setCurrentFilter(mode, defaultIntensity)
                showToast("已选择 ${when(mode) { 1 -> "灰度"; 2 -> "复古"; 3 -> "饱和度"; else -> "滤镜" }}")
            }
        }

        btnFilterGrayscale.setOnClickListener(filterClickListener)
        btnFilterSepia.setOnClickListener(filterClickListener)
        btnFilterSaturation.setOnClickListener(filterClickListener)

        // 滤镜强度进度条监听
        sbFilterIntensity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (currentFilterMode == 0) return // 只有在有滤镜选中时才更新

                val intensity = progress / 100.0f
                tvFilterIntensity.text = "强度: ${progress}%"

                // 实时将强度推送到 GL 线程
                glTextureView.queueEvent {
                    glTextureView.getRenderer().setFilterIntensity(intensity)
                    glTextureView.requestRender()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // 停止拖动时更新状态值
                currentFilterIntensity = seekBar?.progress?.div(100.0f) ?: 0.0f
            }
        })

        // 【新增】确认按钮点击事件
        btnFilterConfirm.setOnClickListener {
            if (currentFilterMode != 0) {
                // 确认：固化效果并关闭菜单
                commitFilterEffect()
                filterMenuContainer.visibility = View.GONE
                activeMenu = null
                showToast("滤镜调整已确认") // 固化函数内部会再发一次Toast，这里保持简单
            } else {
                showToast("请先选择一个滤镜")
            }
        }

        // 【新增】取消按钮点击事件
        btnFilterCancel.setOnClickListener {
            // 取消：撤销效果并关闭菜单
            // 采用新的 cancelFilterEffect 逻辑
            cancelFilterEffect()
        }


        // 底部操作按钮
        view.findViewById<Button>(R.id.btn_back).setOnClickListener {
            requireActivity().finish()
        }

        view.findViewById<Button>(R.id.btn_reset_image).setOnClickListener {
            resetImage()
        }

        // 新增：手势控制按钮
        view.findViewById<Button>(R.id.btn_gesture_toggle).setOnClickListener {
            toggleGestureControl()
        }

        // 新增：状态显示按钮
        view.findViewById<Button>(R.id.btn_show_status).setOnClickListener {
            showCurrentStatus()
        }
    }

    // 新增：切换手势控制
    private fun toggleGestureControl() {
        if (isOpenGLMode && ::glTextureView.isInitialized) {
            isGestureEnabled = !isGestureEnabled
            glTextureView.setGestureEnabled(isGestureEnabled)

            val status = if (isGestureEnabled) "启用" else "禁用"
            showToast("手势控制已$status")
            Log.d(TAG, "手势控制$status")
        } else {
            showToast("Coil模式不支持手势控制")
        }
    }

    // 新增：显示当前状态
    private fun showCurrentStatus() {
        if (isOpenGLMode && ::glTextureView.isInitialized) {
            val state = glTextureView.getTransformState()
            val gestureStatus = if (isGestureEnabled) "启用" else "禁用"

            val message = "当前状态:\n" +
                    "模式: OpenGL ES\n" +
                    "手势: $gestureStatus\n" +
                    state

            showToast(message)
            Log.d(TAG, "状态显示: $message")
        } else {
            showToast("当前模式: Coil加载")
        }
    }

    // 【新增】特效功能 (实现动态扫光切换)
    private fun applyEffect() {
        if (isOpenGLMode && ::glTextureView.isInitialized) {

            // 1. 如果翻转菜单开启，则关闭它
            if (activeMenu == "flip") {
                cancelFlipEffect()
                flipMenuContainer.visibility = View.GONE
            }

            // 2. 如果滤镜菜单开启，则关闭它 (切换特效时是取消滤镜效果，而不是固化)
            if (activeMenu == "filter") {
                setCurrentFilter(0) // 撤销效果
                filterMenuContainer.visibility = View.GONE
                activeMenu = null
                showToast("滤镜菜单已关闭")
            }


            if (isEffectActive) {
                // 停止特效
                glTextureView.stopEffectAnimation()
                isEffectActive = false
                showToast("动态扫光特效已关闭")
            } else {
                // 启动特效
                glTextureView.startEffectAnimation()
                isEffectActive = true
                showToast("动态扫光特效已启动！")
            }
        } else {
            showToast("请在OpenGL模式下使用特效功能")
        }
    }



    // 修改：重置图片逻辑
    private fun resetImage() {
        val imageUriString = getImageUriFromArguments()
        if (!imageUriString.isNullOrEmpty()) {
            if (isOpenGLMode && ::glTextureView.isInitialized) {

                // 停止所有特效动画和菜单
                if (isEffectActive) {
                    glTextureView.stopEffectAnimation()
                    isEffectActive = false
                }

                // 重置滤镜和菜单
                if (activeMenu != null) {
                    if (activeMenu == "filter") {
                        setCurrentFilter(0) // 确保滤镜重置
                        filterMenuContainer.visibility = View.GONE
                    }
                    flipMenuContainer.visibility = View.GONE
                    activeMenu = null
                }

                // 当前使用OpenGL ES，重置变换
                glTextureView.resetTransform()
                // 【新增】确保翻转状态也被重置 (调用 Renderer 中新加的方法)
                glTextureView.queueEvent {
                    glTextureView.getRenderer().resetFlipFlags()
                    glTextureView.requestRender()
                }
                currentScale = 1.0f
                showTransformStatus()
                showToast("OpenGL图片已重置")
                Log.d(TAG, "OpenGL图片重置完成")
            } else {
                // 当前使用Coil，重新加载
                loadImage(imageUriString)
                showToast("图片已重置")
            }
        } else {
            showToast("无法重置图片")
        }
    }

    // 新增：处理返回键
    fun onBackPressed(): Boolean {
        // 1. 如果滤镜子菜单打开，先关闭滤镜菜单并重置滤镜 (撤销效果)
        if (activeMenu == "filter") {
            setCurrentFilter(0)
            filterMenuContainer.visibility = View.GONE
            activeMenu = null
            showToast("已关闭滤镜菜单 (效果已撤销)")
            return true
        }

        // 2. 如果翻转子菜单打开，先关闭翻转子菜单
        if (activeMenu == "flip") {
            flipMenuContainer.visibility = View.GONE
            activeMenu = null
            showToast("已关闭翻转菜单")
            return true
        }

        // 3. 如果特效开启，先关闭特效
        if (isEffectActive) {
            glTextureView.stopEffectAnimation()
            isEffectActive = false
            showToast("已关闭动态特效")
            return true
        }

        if (isOpenGLMode && currentScale != 1.0f) {
            // 如果当前有缩放，先重置
            resetImage()
            return true  // 消费返回键
        }
        return false  // 不消费返回键
    }

    private fun testWithBuiltInImage() {
        Log.d(TAG, "使用内置图片测试")

        try {
            // 创建一个简单的测试图片
            val testBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
            // 填充红色
            testBitmap.eraseColor(0xFFFF0000.toInt())

            // 使用OpenGL ES显示测试图片
            displayImageWithOpenGL(testBitmap, "test")
            Log.d(TAG, "测试图片创建成功")

        } catch (e: Exception) {
            Log.e(TAG, "测试图片创建失败", e)
        }
    }

    override fun onDestroyView() {
        // 确保在视图销毁时停止动画
        if (::glTextureView.isInitialized) {
            glTextureView.stopEffectAnimation()
        }

        super.onDestroyView()
        // 清理资源
        while (!undoStack.isEmpty()) undoStack.pop().recycle()
        while (!redoStack.isEmpty()) redoStack.pop().recycle()
        currentBitmap?.recycle()
        Log.d(TAG, "EditFragment已销毁")
    }

    /**
     * 【重点修改】固化滤镜效果：将当前GL渲染结果固化为新的基础纹理，并重置滤镜状态。
     */


    /**
     * 取消滤镜效果：将滤镜模式重置为 0，强度重置为 0.0f，显示原图。
     */
    private fun cancelFilterEffect() {
        if (!isOpenGLMode) return

        // 1. GL 线程操作：重置滤镜参数
        glTextureView.queueEvent {
            glTextureView.getRenderer().setFilterMode(0)
            glTextureView.getRenderer().setFilterIntensity(0.0f)
            glTextureView.requestRender()
            Log.d(TAG, "GL 线程取消：滤镜模式和强度已重置")
        }

        // 2. Fragment 状态重置 (UI操作在主线程)
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
            currentFilterMode = 0
            currentFilterIntensity = 0.0f
            sbFilterIntensity.progress = 0
            tvFilterIntensity.text = "强度: 0%"

            // 3. 隐藏菜单并恢复控制
            filterMenuContainer.visibility = View.GONE
            activeMenu = null
            showToast("滤镜调整已取消，效果已撤销")
        }
    }
}