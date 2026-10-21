# GPS测速仪 Android App

- 横屏 GPS 实时测速、指针式时速表、速度曲线、最高/平均速度、距离与时长
- 导出 CSV（含时间、km/h、mph、GPS 精度）和 PNG 曲线图
- Android 6.0+，GitHub Actions 云端编译
- applicationId 固定为 `com.yunwu.gpsspeedometer`。后续版本不要随意修改 applicationId 或签名密钥，否则旧版无法覆盖更新。

## GitHub Actions 编译
1. 将项目文件上传到一个新的 GitHub 仓库，确保 `.github/workflows/build-apk.yml` 也上传成功。
2. 在仓库 Settings → Secrets and variables → Actions 中添加签名 Secrets（见下文）。
3. 打开 Actions → Build GPS Speedometer APK → Run workflow。
4. 下载运行结果中的 `GPS-Speedometer-APKs` artifact。

## 签名密钥 Secrets
必须使用同一份私钥签署每个正式发行版本，**请妥善备份 release.jks，不要丢失，也不要公开上传到仓库**。

需要建立以下 4 个 Repository secrets：
- `KEYSTORE_BASE64`: 将 release.jks 转成 Base64 后的完整单行文本
- `KEYSTORE_PASSWORD`: 密钥库密码
- `KEY_ALIAS`: 密钥别名
- `KEY_PASSWORD`: 私钥密码

在电脑上生成 Base64（PowerShell）：
```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.jks")) | Set-Clipboard
```
把剪贴板内容粘贴到 `KEYSTORE_BASE64`。密码和别名填写生成密钥时设定的值。

## 更新规则
- 更新 `app/build.gradle` 的 `versionCode`（每次递增）和 `versionName`（例如 1.0 → 1.1）。
- 不要更换 `applicationId`。
- 不要重新生成或替换签名密钥；备份密钥及密码。
- 从 GitHub Actions 下载同一签名的 release APK，直接覆盖安装即可保留 App 私有数据。卸载旧版通常会清除本地数据，升级前仍建议导出记录。

## 权限和准确性
App 只请求定位权限。速度由 Android GPS 定位速度计算，室内、隧道、低卫星信号环境下会不准确。当前版本的 CSV/PNG 导出保存到手机 `Download/GPSSpeedometer`。
