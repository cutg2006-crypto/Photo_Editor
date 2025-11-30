package com.example.myapplication

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class CropActivity : AppCompatActivity() {

    private val TAG = "CropActivity"
    private var selectedImageUri: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crop)

        Log.d(TAG, "CropActivity已创建")

        // 获取传递的图片URI
        selectedImageUri = intent.getStringExtra("SELECTED_IMAGE_URI")
        Log.d(TAG, "接收到的图片URI: $selectedImageUri")

        // 设置Toolbar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "图片裁剪"

        // 加载裁剪Fragment
        if (savedInstanceState == null) {
            val cropFragment = CropFragment().apply {
                arguments = Bundle().apply {
                    putString("IMAGE_URI", selectedImageUri)
                }
            }

            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, cropFragment)
                .commit()
        }

        // 设置按钮事件
        setupButtonListeners()
    }

    private fun setupButtonListeners() {
        // 取消按钮（左上角）
        val btnCancel = findViewById<Button>(R.id.btn_cancel)
        btnCancel.setOnClickListener {
            Log.d(TAG, "点击取消按钮")
            setResult(RESULT_CANCELED)
            finish()
        }

        // 确认按钮（右上角）
        val btnConfirm = findViewById<Button>(R.id.btn_confirm)
        btnConfirm.setOnClickListener {
            Log.d(TAG, "点击确认按钮")
            // 这里后续会实现裁剪逻辑
            val resultIntent = Intent().apply {
                putExtra("CROPPED_IMAGE_URI", selectedImageUri) // 暂时返回原图
            }
            setResult(RESULT_OK, resultIntent)
            finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        // 返回按钮（物理返回键）
        setResult(RESULT_CANCELED)
        finish()
        return true
    }
}