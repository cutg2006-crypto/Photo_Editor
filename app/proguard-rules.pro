# =======================================================
# 默认配置（保留用于调试和通用Android系统）
# =======================================================

# 强烈建议保留行号信息和源文件信息，以便在崩溃报告中显示有意义的堆栈追踪。
-keepattributes SourceFile,LineNumberTable

# 隐藏原始源文件名称，在保留行号信息的同时增强混淆。
-renamesourcefileattribute SourceFile


# =======================================================
# Android 常用组件的通用保留规则
# =======================================================

# 1. 保留所有自定义 View 的构造函数
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    public void set*(...);
}

# 2. 保留 JNI Native 方法
-keepclasseswithmembers class * {
    native <methods>;
}

# 3. 保留实现了 Parcelable 接口的类
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# 4. 保留实现了 Serializable 接口的类
-keep class * implements java.io.Serializable {
    <fields>;
    <methods>;
}

# 5. 保留枚举 (Enums)
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}


# =======================================================
# 第三方库和反射数据模型 (根据您的项目定制)
# =======================================================

# --- AndroidX Navigation 导航组件 ---
# 必须保留 Navigation Argument 中的 Parcelable 类，否则会反序列化失败。
-keep class * implements androidx.navigation.NavArgs
-keep class * implements androidx.lifecycle.ViewModel


# --- Kotlin Coroutines 协程 ---
# Coroutines 的库通常自带规则，但为了安全和跨平台兼容性，保留以下。
-keepnames class kotlinx.coroutines.internal.ThreadContextKt


# --- Coil 图片加载库 (io.coil-kt) ---
# Coil 库通常会使用 consumer-proguard-rules 自动集成规则，这里提供额外的通用安全规则。
-dontwarn okio.**
-dontwarn coil.fetch.**


# --- Glide 图片加载库 (com.github.bumptech.glide) ---
# 必须保留 Glide 生成的各种 Model、Module 和 RequestManager 类。
# 警告：由于您使用了 annotationProcessor，如果构建时仍然崩溃，可能需要添加更多自定义规则。
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep public class * extends com.bumptech.glide.module.LibraryGlideModule
-keep public class * extends com.bumptech.glide.GeneratedAppGlideModule


# 保留 ModelLoaderFactory 的实现类
-keepnames class com.bumptech.glide.load.model.ModelLoaderFactory

# 保留数据模型类
-keep public class * implements com.bumptech.glide.load.model.ModelLoader { public <init>(...); }

# 忽略 Glide 内部反射可能导致的警告
-dontwarn com.bumptech.glide.manager.*
-dontwarn com.bumptech.glide.load.resource.gif.*