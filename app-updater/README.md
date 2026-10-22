# 应用更新模块

移植自 `D:\AndroidProject\teltphoneHelp\MyServiceCenter\app-updater`，保留 Retrofit、Kotlin Serialization、流式 APK 下载、文件大小/MD5 校验、普通/强制更新提示及安装授权流程。

当前宿主为 MyCallCompose：

- MainActivity 启动时自动检查，检查失败或没有更新时不弹提示。
- 设置页的“检查更新”显示当前版本，点击后反馈检查结果。
- 服务地址配置在 `app/build.gradle.kts` 的 `UPDATE_SERVICE_BASE_URL`，默认沿用 `http://www.lastcoffee.top:8082`。
- 请求为 `GET /api/check?packageName=com.lastcoffee.top.oppo_call_compose&versionCode=<当前安装版本号>`。
- 更新包通过独立的 `${applicationId}.appupdater.fileprovider` 交给系统安装器，不使用配置包分享的 Provider。

为兼容当前 AGP 和 Compose 宿主，模块使用内置 Kotlin、ComponentActivity 和系统 AlertDialog。安装权限需要在 Activity 进入 STARTED 前注册，因此统一在 MainActivity.onCreate 中创建 AppUpdater，设置页复用同一个实例。

服务端发布时，应使用本应用的包名、更高的 versionCode，以及与当前安装版本一致的签名。当前版本保持 1.0 / versionCode 1。

验证命令：

```powershell
.\gradlew.bat :app-updater:testDebugUnitTest :app:assembleDebug
```
