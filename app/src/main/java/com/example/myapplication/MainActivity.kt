package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    // 用于在Fragment之间传递选中的图片URI
    var currentImageUri: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

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

    // 添加这个方法：跳转到编辑页
    fun showEditFragment() {
        val intent = Intent(this, EditActivity::class.java).apply {
            putExtra("SELECTED_IMAGE_URI", currentImageUri)
        }
        startActivity(intent)
    }
}