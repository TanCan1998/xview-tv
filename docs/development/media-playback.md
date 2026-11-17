# 开源播放与媒体渲染

## 选型
- 引擎与标准控制层：AndroidX Media3 1.5.1 (`ExoPlayer` + `PlayerView`)，Apache-2.0。无需外部播放器 App；图片和音视频使用相同 OkHttp 代理策略。
- TV 交互参考：[Just Player](https://github.com/moneytoo/Player)，固定参考提交 `6df39ae74464ec8b9cb8bb3992ee3ab508513a11` (2026-11-12)，根许可证 Unlicense。阅读其 PlayerActivity 的 PlayerView、控制菜单、轨道选择和 release 流程；没有打包 Just Player APK，也没有复制其 FFmpeg/亮度/音量手势模块。
- 播放暂停、快进退、进度条、倍速、音频/字幕轨道由 Media3 开源控件提供，不自制输送/拖动逻辑。
- 清晰度：X MP4 variants按URL里的分辨率标注，切换保留进度/倍速/播放状态；无分辨率时如实显示码率。HLS提供自动轨道，不杜撰不存在的清晰度。
- 硬件优先：`MediaCodecSelector`对平台支持的codec按`hardwareAccelerated`稳定降序；关闭外部扩展解码器并允许平台初始化回退。实际codec通过AnalyticsListener仅在debug显示名称，不保证设备不支持的格式也能硬解。
- 手机手势只处理视频空白表面，原生按钮/TimeBar保持可点。右侧外40%长按暂时加速至max(2,原速×2)，上限4；UP/CANCEL/多指/后台/切质量/销毁均恢复。双击左右外40%跳转，中心20%不跳转。快进退独立设置5/10/15/30/60秒，也用于原生按钮。
- 视频导航使用当前筛选feed快照，跨帖跳过图片，上一/下一首尾禁用；返回恢复原帖子与滚动焦点。
- `PlaybackComponentInstrumentation`使用离屏View合成事件，不注入任何屏幕、不启动Activity，验证双击、长按释放/取消及已保存会话的Cookie解析与同源身份验证。调用见verification.md。
- 最低版本为API23（Android 6.0），不为引入大型全格式扩展提升系统下限。

## 有界生命周期
- `LazyVerticalGrid` 稳定帖子ID key、固定 `contentType` 支持组合复用，不一次创建整个列表控件；仅可见卡片请求自动翻译。
- 单一可见区域分页触发；窗口最多160条、去重、游标循环停止；刷新和账号/代理变化销毁旧窗口。空媒体页允许手动继续。
- 图片12MiB LRU、最多2路解码，最长边采样到1280，来源仅https pbs/video.twimg.com；网络与响应大小有界。
- 单个前台播放器，缓冲目标16MiB、5–15秒；释放时先解除PlayerView绑定/Listener，再stop/clear/release。后台/暂停页停止持有播放器。
- OkHttp取消/响应关闭/TLS连接池清理在IO线程执行，避免onDispose主线程TLS close异常。
- 调试包记录codec名称、手势数值事件及活跃计数，不记录Cookie、账号或媒体URL，不持有全局Context/View/Player。

## X 原生翻译
- 目标语言来自当前应用配置（默认跟随系统），区分简体/繁体中文；同语言跳过，原文可切换查看。
- X同源Grok翻译，404/405时尝试X原生strato翻译。没有第三方API/key，不发送到其他站点。
- 可见帖串行翻译、账号内有界缓存160项；401/403/429进入冷却，不刷请求。失败保留原文。
- 按当前X网页main与HomeTimeline按需分块发现公开web queryId/features，不执行网页JS，不固化账号凭据。

实际已跑和未跑项目见 [verification.md](verification.md)，不能把内存上限等同于已证明无泄漏。
