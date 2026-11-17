# XView 本地接口实测（2026-11-17）

使用本机 XView 已保存且已授权的会话，在应用进程内直接运行 `XWebClient`；不启动页面，不导出 Cookie、不记录请求头、变量中的账号 ID、游标、响应正文或用户内容。当前账号实测，不承诺其他账号/未来网页版本永久可用。

| 操作 | 动态发现的 queryId | 实测方法 | 首次结果 | 分页结果 |
|---|---|---|---|---|
| SearchTimeline | vLHjqbDv7TPFMBkIIO1nOA | POST 200；GET 404 | 20 条视频 | 20 条，游标前进 |
| Likes | ZjyvI1V9gajq5eclHCObzA | GET 200 | 17 条视频 | 10 条，游标前进 |
| Following | eDqlbOS46zda8jDT8dEQ_Q | GET 200 | 50 位博主 | 50 位，游标前进 |
| UserTweets | D6z-FJI2pKuwEeYYxVIGbg | GET 200 | 首位21条帖子 | 下一页21条，确认1条可播放转发视频，游标前进 |

搜索使用 `(nature) filter:videos`、`product=Latest`、`querySource=typed_query`。喜欢与关注均绑定已保存当前账号，博主媒体使用关注结果 ID；count、cursor 等参数经上述分页实测。features/fieldToggles 和操作 ID 从当前 X 网页代码发现，不把本文 ID 写死进实现。

SearchTimeline 的成功 POST 使用 JSON 对象：`variables`、`features`、`fieldToggles`、`queryId`。Likes/Following/UserTweets 的成功 GET 将前三个对象作为 URL 编码参数。正式实现按实测固定方法，不再遇 404 盲试另一方法。

只保留视频/动图；喜欢页和博主媒体可能出现一页无视频但存在下一页，应继续提供加载更多，不能将其视为账号没有任何视频。首位博主两页无视频，不能证明该博主所有历史媒体都无视频。

本地探针 18 项通过（7 项既有组件 + 11 项视频库/分页）。完整脱敏日志为本次交付的 `logs/api-probe.log`；固定方法回归 `logs/api-fixed-method.log` 中搜索/喜欢成功，Following 一次 HTTP503；随后最终优化包 UI 关注列表加载成功。服务端可临时失败，不能把一次成功理解为永久稳定。

博主视频当前使用UserTweets完整时间线再仅保留视频/动图，包含博主转发。原UserMedia媒体栏不能覆盖转发，已不作为博主视频来源；转发保留原帖ID/作者/视频源并带转发标记。2026-11-17本机显式verifyReposts=true：UserTweets GET200，两页21/21，第二页确认1条转发视频，10项设备检查通过。凭据/账号ID/响应未导出。
