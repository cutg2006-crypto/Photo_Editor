package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.widget.Toast

class MainActivity : AppCompatActivity() {

    // 用于在Fragment之间传递选中的图片URI
    var currentImageUri: String? = null
    var currentMimeType: String? = null  // 添加MIME类型

    // 【新增】用于覆盖旧提示的 Toast 实例
    private var currentToast: Toast? = null

    // 封装 showToast 函数，用于取消旧的 Toast
    private fun showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
        currentToast?.cancel()

        // Activity 中直接使用 this 作为 Context
        currentToast = Toast.makeText(this, message, duration)
        currentToast?.show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 【新增代码】隐藏系统导航栏（ActionBar）
        supportActionBar?.hide()

        // 找到按钮并设置点击事件
        val btnGallery = findViewById<Button>(R.id.btn_gallery)
        val btnDeveloper = findViewById<Button>(R.id.btn_developer)

        // 进入相册按钮点击事件
        btnGallery.setOnClickListener {
            // 启动相册Activity
            val intent = Intent(this, GalleryActivity::class.java)
            startActivity(intent)
        }

        // 开发者信息按钮点击事件
        btnDeveloper.setOnClickListener {
            // 启动开发者信息Activity
            val intent = Intent(this, DeveloperActivity::class.java)
            startActivity(intent)
        }
    }

    // 显示编辑页
    fun showEditFragment() {
        Log.d("MainActivity", "showEditFragment被调用")
        Log.d("MainActivity", "currentImageUri: $currentImageUri, MIME类型: $currentMimeType")

        try {
            val intent = Intent(this, EditActivity::class.java).apply {
                putExtra("SELECTED_IMAGE_URI", currentImageUri)
                putExtra("SELECTED_IMAGE_MIME_TYPE", currentMimeType)  // 传递MIME类型
            }
            startActivity(intent)
            Log.d("MainActivity", "成功启动EditActivity")
        } catch (e: Exception) {
            Log.e("MainActivity", "启动EditActivity失败", e)
            showToast("启动编辑页失败") // 修正：使用封装的 showToast
        }
    }
}