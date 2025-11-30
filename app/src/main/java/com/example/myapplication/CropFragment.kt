package com.example.myapplication

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.Fragment
import coil.load

class CropFragment : Fragment(R.layout.fragment_crop) {

    private val TAG = "CropFragment"
    private var currentAspectRatio = AspectRatio.FREE // 默认自由比例

    // 裁剪比例枚举
    enum class AspectRatio(val ratio: Float, val displayName: String) {
        FREE(0f, "自由"),
        RATIO_1_1(1f, "1:1"),
        RATIO_4_3(4f/3f, "4:3"),
        RATIO_16_9(16f/9f, "16:9")
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Log.d(TAG, "CropFragment已创建")

        // 获取图片URI并加载
        val imageUriString = arguments?.getString("IMAGE_URI")
        Log.d(TAG, "接收到的图片URI: $imageUriString")

        val imageView = view.findViewById<ZoomableImageView>(R.id.cropImageView)

        if (!imageUriString.isNullOrEmpty()) {
            imageView.load(android.net.Uri.parse(imageUriString)) {
                crossfade(true)
            }
            Log.d(TAG, "图片加载成功")
        } else {
            imageView.setImageResource(android.R.drawable.ic_menu_gallery)
            Toast.makeText(requireContext(), "未找到图片", Toast.LENGTH_SHORT).show()
        }

        // 设置比例选择按钮
        setupRatioButtons(view)
    }

    private fun setupRatioButtons(view: View) {
        // 自由比例按钮
        view.findViewById<Button>(R.id.btn_ratio_free).setOnClickListener {
            setAspectRatio(AspectRatio.FREE, view)
        }

        // 1:1比例按钮
        view.findViewById<Button>(R.id.btn_ratio_1_1).setOnClickListener {
            setAspectRatio(AspectRatio.RATIO_1_1, view)
        }

        // 4:3比例按钮
        view.findViewById<Button>(R.id.btn_ratio_4_3).setOnClickListener {
            setAspectRatio(AspectRatio.RATIO_4_3, view)
        }

        // 16:9比例按钮
        view.findViewById<Button>(R.id.btn_ratio_16_9).setOnClickListener {
            setAspectRatio(AspectRatio.RATIO_16_9, view)
        }
    }

    private fun setAspectRatio(ratio: AspectRatio, view: View) {
        currentAspectRatio = ratio
        updateButtonStates(view)

        Toast.makeText(
            requireContext(),
            "已选择: ${ratio.displayName} 比例",
            Toast.LENGTH_SHORT
        ).show()

        Log.d(TAG, "选择裁剪比例: ${ratio.displayName}")

        // 这里后续会更新裁剪框的约束
    }

    private fun updateButtonStates(view: View) {
        // 重置所有按钮状态
        val buttons = listOf(
            view.findViewById<Button>(R.id.btn_ratio_free),
            view.findViewById<Button>(R.id.btn_ratio_1_1),
            view.findViewById<Button>(R.id.btn_ratio_4_3),
            view.findViewById<Button>(R.id.btn_ratio_16_9)
        )

        buttons.forEach { button ->
            button.isSelected = false
            button.alpha = 0.6f
        }

        // 设置当前选中按钮
        when (currentAspectRatio) {
            AspectRatio.FREE -> {
                view.findViewById<Button>(R.id.btn_ratio_free).apply {
                    isSelected = true
                    alpha = 1.0f
                }
            }
            AspectRatio.RATIO_1_1 -> {
                view.findViewById<Button>(R.id.btn_ratio_1_1).apply {
                    isSelected = true
                    alpha = 1.0f
                }
            }
            AspectRatio.RATIO_4_3 -> {
                view.findViewById<Button>(R.id.btn_ratio_4_3).apply {
                    isSelected = true
                    alpha = 1.0f
                }
            }
            AspectRatio.RATIO_16_9 -> {
                view.findViewById<Button>(R.id.btn_ratio_16_9).apply {
                    isSelected = true
                    alpha = 1.0f
                }
            }
        }
    }
}