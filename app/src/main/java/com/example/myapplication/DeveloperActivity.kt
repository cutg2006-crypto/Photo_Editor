package com.example.myapplication

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class DeveloperActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_developer)

        // 设置Toolbar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "开发者信息"
    }

    override fun onSupportNavigateUp(): Boolean {
        // 返回按钮点击事件
        finish()
        return true
    }
}