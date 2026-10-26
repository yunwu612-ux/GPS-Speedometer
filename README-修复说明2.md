# Fixed2 修复说明

本版本修正了 GitHub Actions 读取签名密钥时对 Base64 换行的处理，并在解码后检查密钥是否有效。

如果仍然提示 `KEYSTORE_BASE64 was decoded, but the keystore is invalid`：
1. 从原来的签名密钥 ZIP 中解压出 `GPS-Speedometer-release.jks`。
2. 在 Windows PowerShell 中进入该文件所在目录，运行：
   `[Convert]::ToBase64String([IO.File]::ReadAllBytes(".\GPS-Speedometer-release.jks")) | Set-Clipboard`
3. 将剪贴板完整内容粘贴到仓库 Settings → Secrets and variables → Actions → `KEYSTORE_BASE64`。
4. 保留原有 `KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`，不要重新生成密钥。

请将本文件夹内的全部内容上传到仓库根目录，确保覆盖 `.github/workflows/build-apk.yml`。
