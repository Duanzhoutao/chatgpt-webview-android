# GPT Chat Android WebView 工具

这是一个面向中文用户、自用优先的 Android 壳应用项目。

它的作用很简单：
- 使用原生 Android + Kotlin + `WebView`
- 直接打开 ChatGPT 官方网页
- 尽量去掉浏览器感，让它更像一个独立 App

这个项目适合以下场景：
- 手机上暂时不方便下载或安装官方 ChatGPT Android App
- 想自己编译一个轻量壳应用使用
- 希望保留比较干净的全屏网页体验

## 开盒即用

仓库根目录已直接提供可安装 APK：

- `GPT-Chat-debug.apk`

下载项目后，把这个 APK 发到手机上即可安装使用。

如果你对安装包安全性不放心，完全可以先阅读源码，确认没有问题后，再用 Android Studio 自己重新打包 APK 使用。

## 项目特点

- 单 Activity
- 单 WebView
- Kotlin 原生 Android 项目
- 支持基础登录态持久化
- 支持基础文件上传
- 支持网页返回栈
- 支持简单错误页与重试

## 重要说明

- 本项目不是 OpenAI 官方客户端。
- 本项目不调用 OpenAI API，只是封装官方网页。
- 这是一个自用性质的 Android WebView 壳应用。
- 由于 Android WebView 与 Google OAuth 的限制，当前版本不支持 Google 账号登录。
- 邮箱、手机号、Apple 等非 Google 登录方式可按网页实际情况自行测试。

## 适用人群

这个仓库主要是给中文用户参考和自用的。

如果你所在地区可以方便地安装官方 ChatGPT Android App，优先建议使用官方版本。这个项目更适合“暂时没有方便方式使用官方手机 App”的场景。

## 开发环境

- Android Studio
- JDK 17
- Android SDK Platform 35
- Android 9+ 设备或模拟器

## 如何运行

1. 用 Android Studio 打开当前项目目录。
2. 等待 Gradle 同步完成。
3. 安装缺失的 SDK 组件。
4. 连接 Android 9+ 真机或启动模拟器。
5. 运行 `app` 配置。

## 目录说明

- `app/src/main/java/com/example/gptweb/MainActivity.kt`
  - 主入口、WebView 容器、返回键、文件选择、异常恢复
- `app/src/main/java/com/example/gptweb/web/WebAppConfig.kt`
  - 首页 URL、域名配置、基础开关
- `app/src/main/java/com/example/gptweb/web/AppWebViewClient.kt`
  - 网页跳转、错误处理、外链策略
- `app/src/main/java/com/example/gptweb/web/AppWebChromeClient.kt`
  - 进度条、文件上传、弹窗窗口

## 当前限制

- Google 账号登录目前不支持
- 个别网页能力仍可能受 Android WebView 本身限制
- 不同品牌手机对键盘、系统 WebView、后台保活的行为会略有差异

## APK 校验

当前仓库内 APK 文件：

- 文件名：`GPT-Chat-debug.apk`
- SHA-256：`4178B4E889231864A3A25773644131A4A027F1785599B32A52D560F1844A11D4`

## 免责声明

请仅将此项目用于个人学习与自用测试。

仓库内不包含任何 OpenAI 服务端能力，也不会替代官方客户端。若官方 App 可正常获取和使用，仍建议优先使用官方渠道。
