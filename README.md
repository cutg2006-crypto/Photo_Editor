# **Photo Editor \- 极简高性能图片编辑应用**

## ** 项目简介与核心功能**

本项目是一款基于 Android 平台开发的图片编辑应用。致力于提供流畅、高性能的实时编辑体验。应用采用了现代的 Kotlin 协程和 OpenGL ES 2.0 技术栈，确保了复杂图形操作的响应速度。

| 功能模块 | 描述 |
| :---- | :---- |
| **相册管理** | 支持从设备加载 JPG/PNG/GIF 等图片，提供刷新机制。 |
| **OpenGL 编辑** | 静态图片在 GLSurfaceView 中进行渲染，支持实时滤镜、翻转和手势缩放。 |
| **实时滤镜** | 提供灰度、复古、饱和度调节，并支持强度滑块实时预览。 |
| **历史记录** | 基于栈机制实现的撤销（Undo）和重做（Redo）功能。 |
| **动态特效** | 实时动态扫光效果，展示高性能渲染能力。 |
| **翻转** | 提供水平翻转和垂直翻转功能。 |

## ** 构建与运行说明**

### **1\. 技术栈**

* **平台：** Android (Kotlin)  
* **渲染核心：** OpenGL ES 2.0 (通过 GLTextureView 和 GLTextureRenderer 实现)  
* **并发：** Kotlin Coroutines  
* **图片处理：** Coil, Glide (用于 GIF 播放)  
* **UI 框架：** Android XML Layout, ConstraintLayout

### **2\. 运行环境要求**

* **最低 SDK 版本：** API 24 (或更高)  
* **构建工具：** Android Studio Arctic Fox 或更高版本。  
* **权限说明：** 基于 GenerallyFragment.kt 中的实现，应用会根据 Android 版本动态请求 **READ\_EXTERNAL\_STORAGE 或 READ\_MEDIA\_IMAGES** 权限。

## ** 基础项目报告**

### **I. 整体设计思路**

本项目的核心设计思想是**分离关注点 (Separation of Concerns)** 和**性能优先**：

1. **UI/交互层 (Activities/Fragments)**：负责管理用户输入、视图状态和数据流转（如 EditFragment.kt）。  
2. **数据/业务层 (PhotoItem, PhotoAdapter)**：负责数据加载、权限管理和文件保存。  
3. **渲染核心层 (GLTextureView/GLTextureRenderer)**：这是性能瓶颈的关键，采用低级图形 API **OpenGL ES 2.0**，将图像处理计算卸载到 GPU 上，实现了滤镜和变换的实时性。

### **II. 关键功能实现**

* **高性能编辑**：  
  * 通过自定义 GLTextureView 和 GLTextureRenderer，在 GPU 上通过着色器（Shader）实现了滤镜效果。  
  * **手势交互**：GestureController.kt 捕获手势，并通过 GLTextureView.kt 将缩放和平移操作直接映射到 OpenGL 的 Model 矩阵上，实现高性能、零卡顿的视图变换。  
* **历史记录 (Undo/Redo)**：  
  * 利用 ArrayDeque 作为栈结构，最大容量限制为 10 步 (MAX\_HISTORY\_SIZE \= 10)。  
  * 在每次破坏性操作（如固化滤镜、应用翻转）之前，对当前 Bitmap 进行深度复制并压入 **Undo 栈**，确保了状态的可靠回滚。  
* **Toast 提示优化**：  
  * 针对 Android Toast 消息排队导致的滞后问题，在 MainActivity.kt、EditActivity.kt 和 GenerallyFragment.kt 中均实现了 currentToast 实例追踪机制。  
  * 每次调用 showToast(message) 时，都会执行 currentToast?.cancel()，确保新的提示信息能立即显示，提升了用户反馈的即时性。

### **III. 开发中遇到的困难与解决思路**

#### **困难 1: 从 Coil (CPU) 渲染到 OpenGL (GPU) 的迁移**

* **问题描述：** 项目最初使用 Coil 库进行图片显示，该方法严重依赖 CPU 资源，无法满足高性能的实时滤镜需求。迁移到 OpenGL 意味着必须完全重写图片显示和交互的底层逻辑。  
* **解决思路：**  
  1. 引入 **GLTextureView 和 GLTextureRenderer**：构建 GL 上下文和渲染管道。  
  2. **纹理上传**：在 GLTextureRenderer.kt 中实现 updateTextureFromBitmap 方法，负责将 Android 的 Bitmap 数据高效地上传到 GPU 成为纹理 (GL\_TEXTURE\_2D)。  
  3. **手势映射**：结合 GestureController.kt，实现触摸事件到 GL 模型矩阵的转换逻辑，利用 GPU 进行加速平移和缩放。

#### **困难 2: 固化滤镜效果和保存的准确性 (FBO 应用)**

* **问题描述：** 用户的滤镜、翻转等操作仅是 GPU 状态（Shader Uniforms 和 Model 矩阵）的变化，保存时不能直接保存原始图片。同时，需要确保保存的图片是 1:1 像素且没有屏幕拉伸。  
* **解决思路：**  
  1. **引入 FBO (Framebuffer Object)**：在 GLTextureView.kt 中，实现了 FBO 机制，将渲染目标从屏幕切换到内存中的一个纹理。  
  2. **离屏渲染**：在 GLTextureRenderer.kt 中，实现特殊的 **drawForOffscreen** 方法：  
     * 该方法**忽略**了用户在屏幕上进行的缩放和平移操作。  
     * 它仅应用滤镜 (uFilterMode / uFilterIntensity) 和翻转状态 (isFlippedH/isFlippedV)，并将图像以 1:1 比例精确地渲染到 FBO 中。  
  3. **像素读取与翻转**：使用 glReadPixels 从 FBO 读取像素数据。为了修正 OpenGL 固有的垂直颠倒问题，读取到的像素在转换为最终 Bitmap 之前，必须经过一次 **垂直翻转矩阵** 操作。
