# GitHub Actions SDK 初始化修复说明

此次修复针对 GitHub Actions 日志中的：
- `Warning: Failed to find package 'tools'`
- `sdkmanager failed with exit code 1`

原因是工作流没有显式指定 Android SDK 包，setup-android 默认尝试安装已废弃的 `tools` 包。现在明确安装 `platform-tools`、`platforms;android-35` 和 `build-tools;35.0.0`，并移除了多余的许可证安装命令。

## 使用方式
1. 解压本 ZIP。
2. 将 `GPSSpeedometer` 文件夹内所有文件上传到仓库根目录，覆盖旧文件。
3. 确认 `.github/workflows/build-apk.yml` 已覆盖旧工作流。
4. 在 GitHub Actions 重新运行 `Build GPS Speedometer APK`。

此修复不会更改 `applicationId`、`versionCode` 或签名密钥配置。请继续使用原先保存的同一份签名密钥与 Secrets。
