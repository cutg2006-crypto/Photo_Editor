package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class EditActivity : AppCompatActivity() {

    private val TAG = "EditActivity"
    // 【新增】用于覆盖旧提示的 Toast 实例
    private var currentToast: Toast? = null

    // 封装 showToast 函数，用于取消旧的 Toast
    private fun showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
        currentToast?.cancel()

        // 注意：Activity 中直接使用 this 作为 Context
        currentToast = Toast.makeText(this, message, duration)
        currentToast?.show()
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit)

        Log.d(TAG, "=== EditActivity.onCreate() ===")

        // 详细记录Intent内容
        Log.d(TAG, "Intent: $intent")
        Log.d(TAG, "Intent extras: ${intent.extras}")

        // 获取传递的图片URI和MIME类型
        val imageUri = intent.getStringExtra("SELECTED_IMAGE_URI")
        val mimeType = intent.getStringExtra("SELECTED_IMAGE_MIME_TYPE")

        Log.d(TAG, "接收到的参数:")
        Log.d(TAG, "SELECTED_IMAGE_URI: $imageUri")
        Log.d(TAG, "SELECTED_IMAGE_MIME_TYPE: $mimeType")

        // 检查参数是否为空
        if (imageUri.isNullOrEmpty()) {
            Log.e(TAG, "错误：图片URI为空！")
            showToast("错误：未接收到图片", Toast.LENGTH_LONG) // 修正：使用封装的 showToast
            finish()
            return
        }

        // 【修改点】隐藏系统导航栏（ActionBar），移除 Toolbar 设置
        supportActionBar?.hide()

        // 加载编辑Fragment
        if (savedInstanceState == null) {
            val editFragment = EditFragment().apply {
                arguments = Bundle().apply {
                    // 传递所有可能的参数名
                    putString("IMAGE_URI", imageUri)
                    putString("MIME_TYPE", mimeType)
                    putString("SELECTED_IMAGE_URI", imageUri)
                    putString("SELECTED_IMAGE_MIME_TYPE", mimeType)
                }
            }

            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, editFragment)
                .commit()

            Log.d(TAG, "EditFragment已加载，参数已传递")
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}