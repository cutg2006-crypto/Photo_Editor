package com.example.myapplication

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class GalleryActivity : AppCompatActivity() {

    // 添加这行：用于在Fragment之间传递选中的图片URI
    var currentImageUri: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery)

        // 【修改点 A】隐藏系统 ActionBar
        supportActionBar?.hide()

        // 【或移除以下代码】如果不想设置任何Toolbar
        // supportActionBar?.setDisplayHomeAsUpEnabled(true) // 移除这行
        // supportActionBar?.title = "相册" // 移除这行

        // 加载相册Fragment
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, GenerallyFragment())
                .commit()
        }
    }

    // 显示编辑页
    fun showEditFragment() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, EditFragment())
            .addToBackStack("gallery") // 添加到返回栈
            .commit()
    }

    override fun onSupportNavigateUp(): Boolean {
        // 返回按钮点击事件
        finish()
        return true
    }
}