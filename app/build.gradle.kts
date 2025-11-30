plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("kotlin-kapt") // 使用 kapt 插件
}

android {
    namespace = "com.example.myapplication"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.myapplication"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // ⚠ 签名配置必须在构建类型引用之前定义
    signingConfigs {
        create("release") {
            storeFile = file("D:/Program/Android/hzrKey.jks")
            storePassword = "abc12345"
            keyAlias = "key0"
            keyPassword = "abc12345"
        }
    }

    buildTypes {
        release {
            //  关键：启用 R8/混淆
            isMinifyEnabled = true

            //  关键：引用前面定义的签名配置
            signingConfig = signingConfigs.getByName("release")

            // 关键：指定 R8/Proguard 规则文件
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        debug {
            isMinifyEnabled = false
        }
    }

    // 编译选项
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    // AndroidX & Google Material 基础库
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation("com.google.android.material:material:1.10.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Navigation 导航组件
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.5")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.5")

    // Kotlin Coroutines 协程
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Coil 图片加载库 (已合并所有 Coil 依赖)
    implementation("io.coil-kt:coil:2.5.0")
    implementation("io.coil-kt:coil-gif:2.5.0")  // GIF 支持
    implementation("io.coil-kt:coil-video:2.5.0") // 视频支持

    // Glide 图片加载库
    implementation("com.github.bumptech.glide:glide:4.15.1")
    // 使用 kapt 替换 annotationProcessor (如果项目使用 Kotlin 推荐)
    kapt("com.github.bumptech.glide:compiler:4.15.1")

    // 测试依赖
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}