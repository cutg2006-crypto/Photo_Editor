package com.example.myapplication

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.ImageView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.size.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.Intent


class GenerallyFragment : Fragment(R.layout.fragment_generally) {

    private companion object {
        const val PERMISSION_REQUEST_CODE = 100
        const val TAG = "GenerallyFragment"
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated: Fragment视图已创建")

        // 检查权限
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
                    Toast.makeText(requireContext(), "权限获取成功，加载照片中...", Toast.LENGTH_SHORT).show()
                    loadPhotos()
                } else {
                    Log.d(TAG, "用户拒绝了权限")
                    Toast.makeText(requireContext(), "需要存储权限才能显示照片", Toast.LENGTH_LONG).show()
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
                    Toast.makeText(requireContext(), "未找到照片", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // 设置RecyclerView
                val recyclerView = requireView().findViewById<RecyclerView>(R.id.recyclerView)
                recyclerView.layoutManager = GridLayoutManager(requireContext(), 3)

                // 创建适配器并设置点击监听器 - 修改这里
                recyclerView.adapter = PhotoAdapter(photos) { photo ->
                    Log.d(TAG, "点击了照片: ${photo.name}")
                    Log.d(TAG, "照片URI: ${photo.uri}")
                    Log.d(TAG, "跳转到编辑页")

                    try {
                        // 创建跳转到EditActivity的Intent
                        val intent = Intent(requireContext(), EditActivity::class.java).apply {
                            // 传递选中的图片URI
                            putExtra("SELECTED_IMAGE_URI", photo.uri.toString())
                        }

                        // 启动EditActivity
                        startActivity(intent)
                        Log.d(TAG, "成功启动EditActivity")
                        Toast.makeText(requireContext(), "跳转到编辑页", Toast.LENGTH_SHORT).show()

                    } catch (e: Exception) {
                        Log.e(TAG, "跳转到EditActivity失败: ${e.message}", e)
                        Toast.makeText(requireContext(), "跳转失败: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }

                Toast.makeText(requireContext(), "已加载 ${photos.size} 张照片", Toast.LENGTH_SHORT).show()

            } catch (e: Exception) {
                Log.e(TAG, "加载照片失败", e)
                Toast.makeText(requireContext(), "加载照片失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
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
                    android.provider.MediaStore.Images.Media.DATE_ADDED
                )

                val sortOrder = "${android.provider.MediaStore.Images.Media.DATE_ADDED} DESC"

                val cursor = requireContext().contentResolver.query(
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    null,
                    null,
                    sortOrder
                )

                cursor?.use {
                    val idColumn = it.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media._ID)
                    val nameColumn = it.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.DISPLAY_NAME)

                    while (it.moveToNext()) {
                        val id = it.getLong(idColumn)
                        val name = it.getString(nameColumn)
                        val contentUri = android.content.ContentUris.withAppendedId(
                            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            id
                        )

                        photos.add(PhotoItem(id, contentUri, name))
                    }

                    Log.d(TAG, "成功查询到 ${photos.size} 张照片")
                } ?: run {
                    Log.e(TAG, "查询照片时cursor为null")
                }

            } catch (e: SecurityException) {
                Log.e(TAG, "权限不足，无法查询照片", e)
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "查询照片时发生错误", e)
                throw e
            }

            photos
        }
    }
}

// 照片数据类
data class PhotoItem(val id: Long, val uri: android.net.Uri, val name: String)

// 照片适配器
class PhotoAdapter(
    private val photos: List<PhotoItem>,
    private val onPhotoClickListener: (PhotoItem) -> Unit
) : RecyclerView.Adapter<PhotoAdapter.PhotoViewHolder>() {

    class PhotoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.photoImageView)
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
            size(Size(200, 200))  // 使用Size类
            crossfade(true)
            error(android.R.drawable.ic_menu_report_image)
        }

        // 设置点击事件
        holder.itemView.setOnClickListener {
            onPhotoClickListener(photo)
        }
    }

    override fun getItemCount() = photos.size
}