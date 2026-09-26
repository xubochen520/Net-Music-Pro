# Net Music Pro 模组工程

这是 [Net Music Pro](../README.md) 的 NeoForge 工程。目标环境为 Minecraft 1.21.1、NeoForge 21.1.251、Java 21 和 Net Music 1.5.2。模组 ID 为 `yunpumusic`，用于保持已有方块和存档兼容。

## 构建

```powershell
.\gradlew.bat build
```

Linux/macOS：`./gradlew build`。产物在 `build/libs/net-music-pro-1.0.2.jar`。Gradle 从 Modrinth Maven 下载 Net Music；首次构建需要联网。不要将 `build/`、`run/` 或 `libs/` 内的文件提交到 Git。

## 工程结构

- `src/main/java/com/yunpu/yunpumusic/api/provider/`：酷狗、网易云、QQ 音乐接口。
- `src/main/java/com/yunpu/yunpumusic/client/`：刻录台界面和唱片机歌词显示。
- `src/main/java/com/yunpu/yunpumusic/menu/`、`world/`、`network/`：唱片输入输出、方块实体和网络交互。
- `src/main/java/com/yunpu/yunpumusic/credentials/`：本地及服务端会话保存。
- `src/main/resources/`：语言文件、方块模型、贴图、配方和 NeoForge 元数据。
- `src/test/`：手动运行的平台接口探针；它们会请求第三方服务，不属于离线自动测试。

登录会话属于敏感数据。运行目录里的配置、存档及日志只用于本地测试，不应提交或公开。
