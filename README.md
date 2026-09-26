# Net Music Pro

适用于 **Minecraft 1.21.1 / NeoForge** 的 [Net Music](https://github.com/TartaricAcid/NetMusic) 附属模组。新增双格高的「云谱刻录台」，可搜索酷狗、网易云音乐和 QQ 音乐的歌曲，并将歌曲写入 Net Music 唱片。

> 本项目不是上述音乐平台的官方客户端。歌曲能否获取和播放取决于平台接口、账号权限及 Net Music 的播放环境；请遵守相应平台的服务条款和版权规定。

## 功能

- 三个平台的搜索与扫码登录；界面显示可识别的账号 VIP / SVIP 状态和歌曲 VIP 标记。
- 输入槽支持空白唱片及已刻录唱片（覆盖写入），输出槽领取成品。选择歌曲后在刻录页点击「制作唱片」。
- 唱片名称显示平台颜色与 VIP 标记；唱片交由 Net Music 的 **Music Player** 播放，本模组为其补充同步歌词显示。
- 登录会话默认保存在本地。服务器管理员可以选择把凭据保存到存档供服务器使用。
- 附带 Blockbench 模型源文件 [`yunpu_burner.bbmodel`](yunpu_burner.bbmodel)。

## 安装

| 项目 | 要求 |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.251 或兼容的 21.1 版本 |
| Net Music | 1.5.2 或更高的 1.21.1 NeoForge 版本 |
| Java（自行构建时） | 21 |

将 Net Music 与 Net Music Pro 的 JAR 一起放进游戏的 `mods` 文件夹。多人游戏中，服务端及需要使用界面的客户端都应安装对应模组。已有存档的模组 ID 保持为 `yunpumusic`，请勿手动更改。

## 使用

云谱刻录台配方：避雷针在顶格，第二行依次为铁粒、红石、铁粒，第三行是三个铜锭。

1. 放置云谱刻录台，右键打开终端。
2. 选择音乐平台，按需扫码登录，搜索歌名或歌手并选择歌曲。
3. 打开「刻录唱片」，在输入槽放入空白或已有内容的 Net Music 唱片，确保输出槽为空。
4. 点击「制作唱片」，从输出槽取走成品并放入 Net Music 唱片机播放。

会员曲目需要对应平台的有效会员权限。平台接口可能改变，VIP 状态、歌词及音源的可用性也可能因曲目或账号而异。

## 构建

在 `mod` 目录运行：

```powershell
.\gradlew.bat build
```

Linux/macOS 使用 `./gradlew build`。生成的 JAR 位于 `mod/build/libs/`。构建脚本从 Modrinth Maven 下载 Net Music 依赖，不需要把上游 JAR 加入仓库。工程说明见 [`mod/README.md`](mod/README.md)。

## 隐私与安全

扫码登录所得的 Cookie / token 属于账号凭据。不要提交或分享 `config/yunpumusic/credentials.json`、存档里的 `serverconfig/yunpumusic/credentials.json`，也不要公开含凭据的日志或截图。服务端保存功能仅供有权限的管理员操作。

## 开源与致谢

本仓库原创代码与资源采用 [GPL-3.0-only](LICENSE) 许可。Net Music 是单独安装的上游依赖，其代码与素材遵循各自的许可；酷狗接口实现参考了 GPL-3.0 项目 [Mineradio](https://github.com/XxHuberrr/Mineradio)。详情见 [NOTICE.md](NOTICE.md)。

问题反馈：[GitHub Issues](https://github.com/xubochen520/Net-Music-Pro/issues)。
