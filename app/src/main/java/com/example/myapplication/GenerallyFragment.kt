package com.example.myapplication

import android.Manifest
import com.bumptech.glide.Glide
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GenerallyFragment : Fragment(R.layout.fragment_generally) {
    private val TAG = "GenerallyFragment"

    private companion object {
        const val PERMISSION_REQUEST_CODE = 100
    }

    // 【新增】用于覆盖旧提示的 Toast 实例
    private var currentToast: Toast? = null

    // 封装 showToast 函数，用于取消旧的 Toast
    private fun showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
        currentToast?.cancel()

        // 注意：Fragment 中使用 requireContext()
        currentToast = Toast.makeText(requireContext(), message, duration)
        currentToast?.show()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated: Fragment视图已创建")

        // 【新增】设置刷新按钮点击事件
        view.findViewById<Button>(R.id.btn_refresh).setOnClickListener {
            Log.d(TAG, "点击刷新按钮")
            if (hasReadStoragePermission()) {
                // 有权限，直接刷新
                showToast("正在刷新相册...") // 修正：使用封装的 showToast
                loadPhotos()
            } else {
                // 无权限，请求权限
                Log.d(TAG, "刷新时无权限，请求权限")
                requestStoragePermission()
            }
        }

        // 初始检查权限
        if (hasReadStoragePermission()) {
            Log.d(TAG, "已有权限，开始加载照片")
            loadPhotos()
        } else {
            Log.d(TAG, "无权限，请求权限")
            requestStoragePermission()
        }
    }

    // 检查存储权限
    private fun hasReadStoragePermission(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    // 请求存储权限
    private fun requestStoragePermission() {
        val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        Log.d(TAG, "请求权限: $permission")
        requestPermissions(arrayOf(permission), PERMISSION_REQUEST_CODE)
    }

    // 处理权限请求结果
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "用户同意了权限")
                    showToast("权限获取成功，加载照片中...") // 修正：使用封装的 showToast
                    loadPhotos()
                } else {
                    Log.d(TAG, "用户拒绝了权限")
                    showToast("需要存储权限才能显示照片", Toast.LENGTH_LONG) // 修正：使用封装的 showToast
                }
            }
        }
    }

    // 加载照片
    private fun loadPhotos() {
        Log.d(TAG, "开始加载照片")

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
            try {
                val photos = withContext(Dispatchers.IO) {
                    queryPhotos()
                }

                Log.d(TAG, "查询到 ${photos.size} 张照片")

                if (photos.isEmpty()) {
                    showToast("未找到照片") // 修正：使用封装的 showToast
                    return@launch
                }

                // 设置RecyclerView
                val recyclerView = requireView().findViewById<RecyclerView>(R.id.recyclerView)
                recyclerView.layoutManager = GridLayoutManager(requireContext(), 3)

                // 创建适配器并设置点击监听器
                recyclerView.adapter = PhotoAdapter(photos) { photo ->
                    Log.d(TAG, "点击了照片: ${photo.name}, 类型: ${photo.mimeType}")

                    if (photo.mimeType == "image/gif") {
                        // GIF文件：直接播放，不进入编辑页
                        Log.d(TAG, "GIF文件，直接播放")
                        showGifPlayer(photo)
                    } else {
                        // 普通图片：跳转到编辑页
                        Log.d(TAG, "普通图片，跳转到编辑页")
                        goToEditPage(photo)
                    }
                }

                showToast("已加载 ${photos.size} 张照片") // 修正：使用封装的 showToast

            } catch (e: Exception) {
                Log.e(TAG, "加载照片失败", e)
                showToast("加载照片失败: ${e.message}", Toast.LENGTH_LONG) // 修正：使用封装的 showToast
            }
        }
    }

    // 跳转到编辑页
    private fun goToEditPage(photo: PhotoItem) {
        Log.d(TAG, "=== 跳转到编辑页 ===")
        Log.d(TAG, "照片URI: ${photo.uri}")
        Log.d(TAG, "照片URI字符串: ${photo.uri.toString()}")
        Log.d(TAG, "MIME类型: ${photo.mimeType}")

        try {
            val intent = Intent(requireContext(), EditActivity::class.java).apply {
                putExtra("SELECTED_IMAGE_URI", photo.uri.toString())
                putExtra("SELECTED_IMAGE_MIME_TYPE", photo.mimeType)
            }

            Log.d(TAG, "Intent创建完成")
            startActivity(intent)
            Log.d(TAG, "Activity启动完成")

        } catch (e: Exception) {
            Log.e(TAG, "跳转失败", e)
            showToast("跳转失败", Toast.LENGTH_LONG) // 修正：使用封装的 showToast
        }
    }

    // 查询手机中的照片
    private suspend fun queryPhotos(): List<PhotoItem> {
        return withContext(Dispatchers.IO) {
            val photos = mutableListOf<PhotoItem>()

            try {
                val projection = arrayOf(
                    android.provider.MediaStore.Images.Media._ID,
                    android.provider.MediaStore.Images.Media.DISPLAY_NAME,
                    android.provider.MediaStore.Images.Media.MIME_TYPE,
                    android.provider.MediaStore.Images.Media.DATE_ADDED
                )

                val selection = "${android.provider.MediaStore.Images.Media.MIME_TYPE} IN (?, ?, ?, ?)"
                val selectionArgs = arrayOf("image/jpeg", "image/png", "image/webp", "image/gif")

                val sortOrder = "${android.provider.MediaStore.Images.Media.DATE_ADDED} DESC"

                val cursor = requireContext().contentResolver.query(
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    sortOrder
                )

                cursor?.use {
                    val idColumn = it.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media._ID)
                    val nameColumn = it.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.DISPLAY_NAME)
                    val mimeTypeColumn = it.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.MIME_TYPE)

                    while (it.moveToNext()) {
                        val id = it.getLong(idColumn)
                        val name = it.getString(nameColumn)
                        val mimeType = it.getString(mimeTypeColumn)
                        val contentUri = android.content.ContentUris.withAppendedId(
                            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            id
                        )

                        photos.add(PhotoItem(id, contentUri, name, mimeType))
                    }

                    // 修复日志代码
                    val gifCount = photos.count { it.mimeType == "image/gif" }
                    Log.d(TAG, "成功查询到 ${photos.size} 个媒体文件，其中GIF文件: $gifCount")
                } ?: run {
                    Log.e(TAG, "查询照片时cursor为null")
                }

            } catch (e: Exception) {
                Log.e(TAG, "查询照片时发生错误", e)
            }

            photos
        }
    }

    // 显示GIF播放器
    private fun showGifPlayer(photo: PhotoItem) {
        Log.d(TAG, "播放GIF: ${photo.name}")

        try {
            val dialog = Dialog(requireContext()).apply {
                setContentView(R.layout.dialog_gif_player)
                window?.setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT
                )
                window?.setBackgroundDrawableResource(android.R.color.transparent)
            }

            val gifImageView = dialog.findViewById<ImageView>(R.id.gifImageView)
            val btnClose = dialog.findViewById<Button>(R.id.btnClose)

            // 使用Glide加载GIF（动图）
            Glide.with(requireContext())
                .load(photo.uri)
                .into(gifImageView)

            // 关闭按钮点击事件
            btnClose.setOnClickListener {
                dialog.dismiss()
            }

            // 点击背景关闭
            val gifContainer = dialog.findViewById<View>(R.id.gifContainer)
            gifContainer.setOnClickListener {
                dialog.dismiss()
            }

            // 显示弹窗
            dialog.show()
            showToast("GIF动图播放中") // 修正：使用封装的 showToast

        } catch (e: Exception) {
            Log.e(TAG, "播放GIF失败: ${e.message}", e)
            showToast("播放失败: ${e.message}", Toast.LENGTH_LONG) // 修正：使用封装的 showToast
        }
    }
}

// 照片数据类
data class PhotoItem(
    val id: Long,
    val uri: android.net.Uri,
    val name: String,
    val mimeType: String = "image/jpeg"
)

// 照片适配器
class PhotoAdapter(
    private val photos: List<PhotoItem>,
    private val onPhotoClickListener: (PhotoItem) -> Unit
) : RecyclerView.Adapter<PhotoAdapter.PhotoViewHolder>() {

    class PhotoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.photoImageView)
        val fileTypeBadge: TextView = view.findViewById(R.id.fileTypeBadge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_photo, parent, false)
        return PhotoViewHolder(view)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        val photo = photos[position]

        // 使用Coil加载图片
        holder.imageView.load(photo.uri) {
            size(200, 200)
            crossfade(true)
            error(android.R.drawable.ic_menu_report_image)
        }

        // 设置文件类型标识
        if (photo.mimeType == "image/gif") {
            holder.fileTypeBadge.visibility = View.VISIBLE
            holder.fileTypeBadge.text = "GIF"
            holder.fileTypeBadge.setBackgroundColor(0xCCFF5722.toInt())
        } else {
            holder.fileTypeBadge.visibility = View.GONE
        }

        // 设置点击事件
        holder.itemView.setOnClickListener {
            onPhotoClickListener(photo)
        }
    }

    override fun getItemCount() = photos.size
}