# 周边生活地图 (Nearby Map)

基于高德地图 Android SDK 的本地生活服务应用，支持查看附近维修站、医院、充电站，分类筛选地图标记和列表。

## 功能

- 🗺️ **高德 3D 地图** — 实时定位 + 蓝点跟随 + 缩放控件
- 🔧🏥🔌 **分类筛选** — 全部 / 维修站 / 医院 / 充电站，点击切换联动地图标记和卡片列表
- 📍 **附近推荐** — 随机生成周边 POI 标记，颜色区分类型（橙色=维修、红色=医院、蓝色=充电）
- 🧭 **底部导航栏** — 主页 / 地图 / 问AI / 社群 / 个人，问AI 按钮凸出居中
- ⚠️ **预警横幅** — 台风/灾害实时提醒

## 技术栈

| 模块 | 技术 |
|------|------|
| 语言 | Java |
| 构建 | Gradle 8.13 (Kotlin DSL) |
| 最低 SDK | Android 7.0 (API 24) |
| 目标 SDK | Android 15 (API 36) |
| 地图 SDK | 高德 3D 地图 + 定位 + 搜索合包 |
| 架构 | FrameLayout + LinearLayout + ScrollView |

## 快速开始

### 1. 获取高德 Key

1. 打开 [高德开放平台控制台](https://console.amap.com/dev/key/app)
2. 创建应用 → 添加 Key → 选择「Android 平台 SDK」
3. 填写包名 `com.example.map` 和调试版 SHA1
4. 复制生成的 Key

### 2. 配置 Key

在 `app/src/main/AndroidManifest.xml` 中替换：

```xml
<meta-data
    android:name="com.amap.api.v2.apikey"
    android:value="你的Key" />
```

### 3. 编译运行

```bash
# 调试版 APK
./gradlew assembleDebug

# 或直接在 Android Studio 点 Run ▶
```

## 依赖

```kotlin
// 高德 3D 地图合包（含定位+搜索）
implementation("com.amap.api:3dmap-location-search:11.2.000_loc11.2.000_sea9.8.0")

// AndroidX
implementation(libs.appcompat)
implementation(libs.material)
implementation(libs.activity)
implementation(libs.constraintlayout)
```

## 项目结构

```
app/src/main/
├── java/com/example/map/
│   └── MainActivity.java        # 主页面：地图 + 分类筛选 + 导航栏
├── res/
│   ├── layout/
│   │   └── activity_main.xml    # 完整布局（地图/分类/列表/导航栏/预警）
│   └── drawable/
│       ├── card_white_round.xml  # 白色圆角卡片背景
│       ├── card_yellow.xml       # 黄色预警卡片背景
│       ├── bg_icon_orange.xml    # 橙色分类图标底
│       ├── bg_icon_red.xml       # 红色医院图标底
│       ├── bg_tag_orange.xml     # 橙色标签
│       ├── bg_tag_red.xml        # 红色标签
│       ├── bg_tag_yellow.xml     # 黄色预警标签
│       ├── bg_ai_button.xml      # 问AI 按钮背景
│       └── card_img_placeholder.xml  # 图片占位
└── AndroidManifest.xml
```

## 权限

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
```

## License

仅学习用途。
