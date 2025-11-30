package com.example.myapplication

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.Fragment
import coil.load

class EditFragment : Fragment(R.layout.fragment_edit) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 获取传递的图片URI
        val imageUriString = arguments?.getString("IMAGE_URI")
        Log.d("EditFragment", "接收到的图片URI: $imageUriString")

        // 找到ImageView并加载图片
        val imageView = view.findViewById<ZoomableImageView>(R.id.imageView)

        if (!imageUriString.isNullOrEmpty()) {
            imageView.load(Uri.parse(imageUriString)) {
                crossfade(true)
            }
        } else {
            imageView.setImageResource(android.R.drawable.ic_menu_gallery)
            Toast.makeText(requireContext(), "未找到图片", Toast.LENGTH_SHORT).show()
        }

        // 设置按钮点击事件
        setupButtonClickListeners(view)

    }

    private fun setupButtonClickListeners(view: View) {
        // 顶部操作按钮 - 只保留3个
        view.findViewById<Button>(R.id.btn_undo).setOnClickListener {
            Toast.makeText(requireContext(), "撤销功能开发中", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<Button>(R.id.btn_redo).setOnClickListener {
            Toast.makeText(requireContext(), "重做功能开发中", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<Button>(R.id.btn_save).setOnClickListener {
            Toast.makeText(requireContext(), "保存功能开发中", Toast.LENGTH_SHORT).show()
        }

        // 功能选项卡按钮
        // 功能选项卡按钮
        view.findViewById<Button>(R.id.btn_crop).setOnClickListener {
            Log.d("EditFragment", "点击了裁剪按钮")

            try {
                // 获取当前显示的图片URI
                val imageUri = arguments?.getString("IMAGE_URI")

                if (!imageUri.isNullOrEmpty()) {
                    Log.d("EditFragment", "传递图片URI到裁剪页: $imageUri")

                    // 创建跳转到CropActivity的Intent
                    val intent = Intent(requireContext(), CropActivity::class.java).apply {
                        putExtra("SELECTED_IMAGE_URI", imageUri)
                    }

                    // 启动裁剪Activity
                    startActivity(intent)
                    Log.d("EditFragment", "成功启动CropActivity")

                } else {
                    Log.e("EditFragment", "未找到图片URI，无法跳转到裁剪页")
                    Toast.makeText(requireContext(), "未找到图片，无法进行裁剪", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                Log.e("EditFragment", "跳转到裁剪页失败: ${e.message}", e)
                Toast.makeText(requireContext(), "跳转失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        view.findViewById<Button>(R.id.btn_filter).setOnClickListener {
            Toast.makeText(requireContext(), "滤镜功能开发中", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<Button>(R.id.btn_adjust).setOnClickListener {
            Toast.makeText(requireContext(), "调整功能开发中", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<Button>(R.id.btn_effect).setOnClickListener {
            Toast.makeText(requireContext(), "特效功能开发中", Toast.LENGTH_SHORT).show()
        }

        // 底部操作按钮
        view.findViewById<Button>(R.id.btn_back).setOnClickListener {
            // 返回相册页
            requireActivity().finish()
        }

        view.findViewById<Button>(R.id.btn_reset_image).setOnClickListener {
            // 重置图片
            val imageView = view.findViewById<ZoomableImageView>(R.id.imageView)
            imageView.reset()
            Toast.makeText(requireContext(), "图片已重置", Toast.LENGTH_SHORT).show()
        }
    }
}