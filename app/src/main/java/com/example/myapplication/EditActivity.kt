package com.example.myapplication

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity

class EditActivity : AppCompatActivity() {

    private val TAG = "EditActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit)  // 确保这行存在

        Log.d(TAG, "EditActivity已创建")

        // 设置Toolbar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "图片编辑"

        // 获取传递的图片URI
        val imageUri = intent.getStringExtra("SELECTED_IMAGE_URI")
        Log.d(TAG, "接收到的图片URI: $imageUri")

        // 加载编辑Fragment
        if (savedInstanceState == null) {
            val editFragment = EditFragment().apply {
                arguments = Bundle().apply {
                    putString("IMAGE_URI", imageUri)
                }
            }

            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, editFragment)
                .commit()

            Log.d(TAG, "EditFragment已加载")
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}