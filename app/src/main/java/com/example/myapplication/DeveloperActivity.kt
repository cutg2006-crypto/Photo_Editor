package com.example.myapplication

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class DeveloperActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_developer)

        // 【新增代码】隐藏系统导航栏（ActionBar）
        supportActionBar?.hide()

        // 移除了原有的设置 Toolbar 代码，只保留隐藏
    }

    override fun onSupportNavigateUp(): Boolean {
        // 返回按钮点击事件
        finish()
        return true
    }
}