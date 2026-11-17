# Verification

## TV 输入、多账号与翻译修复（2026-11-17，开发分支）

- 菜单键切换设置；返回优先回当前导航；刷新与导航同行，搜索框同卡片圆角且与导航/按钮同行。视频区隐藏按钮，左右/长按seek、OK播放暂停；外部32dp按钮栏，进度焦点采用独立深底外框。TV视频层改TextureView，等待最终GitHub APK实测。
- 多账号AES-GCM加密文档保存最多20个账号与当前选择，兼容旧单账号，新增/同ID更新/切换/独立移除；切换重建账号客户端与页面。合成多账号设备测试源码已增加，尚未执行最终包设备测试；没有第二个真实账号验证证据。
- Grok响应增加完整JSON/NDJSON/SSE聚合及四项回归，针对只显示首个译文字片段；最终真实接口与卡片布局需复测。
- 早期临时兼容构建（旧缓存AGP8.9/Kotlin2.1/SDK35）42JUnit零失败、Lint0错误31警告。该结果不覆盖后续多账号与最新修订。用户随后明确只使用GitHub构建，此后不再启动本地Gradle。
- 真实MiTV API28早期调试包覆盖安装保账号；刷新同行、卡片上下导航不误开设置、菜单显示设置、OK暂停及恢复、左右seek已观察。离线fixture只用于按键验证，无真实视频；真实首页重入78条。SurfaceView期间截图黑屏但Amlogic硬解/进度在运行，最终TextureView尚未验收。菜单关闭焦点恢复曾发现问题，已改叶子焦点记录待复测。
- 最终应用提交`136a5e3`由[GitHub Actions](https://github.com/TanCan1998/xview-tv/actions/runs/44036648128)构建签名成功；前次全量运行46JUnit零失败/零错误，最终影响范围运行Lint0错误42警告、124结构检查和4项APK元数据测试通过。APK2935542字节，SHA256 `5532fe01c9d2fbf0101aaed9f19b4253b0df2ac1d110d2a9a9d4e3efc9dac29b`，已覆盖安装MiTV保账号。
- 动态版本CI从`output-metadata.json`读取versionName/versionCode与APK路径，产物/签名文件名不再硬编码应用版本。双语README新增1920×1080组合预览，素材为自身图标与不可逆脱敏的TV/手机截图。
- TV最终验收：原账号恢复、账号管理页显示当前账号/添加入口；刷新导航同行、搜索框圆角且与导航同行；卡片→菜单显示设置→菜单关闭回原卡片；列表返回优先当前页导航；OK暂停可见，进度条深底/青框/浅色轨道可区分。32dp外部按钮栏实测占屏幕80px（40dp）。原生播放/快退快进按钮不显示。
- TextureView在MiTV RenderThread出现`glGenTextures GL_OUT_OF_MEMORY`，已回SurfaceView并缩小隐藏焦点靶为1dp；最终进程存活、当前日志无新增此类崩溃。电视截图仍黑色视频区，进度可见；不能据此宣称视频画面已完整验收。最终长按连续seek、外部倍速完整路径、真实译文和多真实账号切换未全面复测；合成设备测试源码尚未执行。
- 尚未合并main或更新Release。早期测试记录仅说明过程，以以上最终结果为准。

## 1.0.0 开源交付（2026-11-17，历史基线）

- 最终安装包由 [GitHub Actions #14](https://github.com/TanCan1998/xview-tv/actions/runs/44017925172) 构建，运行结论 success，源码提交 `683f759d24c90c980e930f7c9bec06b7a4a296e3`。最终公开初始提交随后只加入打码截图及交付文档，应用源码与此成功构建一致。
- AGP9.5.1、Gradle9.9.1、Kotlin/Compose编译器2.4.21、协程core/test1.11.0、Compose BOM2026.09.00、TV Material1.1.0、OkHttp5.5.1、ZXing3.5.4、JSON20260814。compileSdk37.2/target35/min23，JDK17、Build Tools36.0.0。Android模块使用内置Kotlin；根据新Compose要求升级编译SDK并修复可观察Locale的两项Lint错误。
- 所有Actions固定官方最新稳定版本的完整SHA；Node26.13.1。SDK安装使用Android CLI，旧sdkmanager弃用告警和assets.srcDir弃用配置已修正；Ubuntu固定24.04。CI按提交差异与下游依赖选择测试，版本/构建配置及未知基线保留全量，纯文档和截图跳过Gradle；6类选择回归通过。Gradle依赖缓存与--build-cache启用。
- 最终GitHub报告：38个JUnit，0失败/0错误；Lint0错误42警告，112结构检查、43网页断言及LAN加密互操作通过。警告主要为API属性、图标资源、KTX/新Compose建议；未声称零警告。编译另有多余空值判断和第三方原生库无法剥离符号提示，不影响构建成功。
- APK `xview-1.0.0.apk` 2890430字节，SHA256 `d2334ee541a9f4272bb1f0c28dbf45767ad33d722cc366460b31dd8bde0978b0`；MT确认1.0.0/versionCode1/min23/target35、2DEX/4259类、V1/V2/V3同证书；apksigner verify通过。CI使用仓库加密Secrets中的现有测试签名，未提交密钥；不是新建的正式生产签名身份。
- 本机手机与小米MiTV-ANSP0（Android9/API28、1920×1080）均覆盖安装该GitHub APK成功，保留账号和设置。手机虚拟屏1080×1920@420真实首页72条媒体、设置坐标触摸进入；TV启动Status ok、真实72条媒体/三列布局、遥控器下键移动焦点到第一卡片。此次未重测完整扫码、连续播放、全部导航或API23真机。
- 双语README使用相对路径引用手机首页、手机设置、TV首页3张实际运行图。账号身份、头像、帖子、缩略图等被不透明生成马赛克覆盖，包括焦点放大边缘；原图和节点树不入Git。手机虚拟屏close/release/stop成功，最终前台Minis、旋转0。
- 所有开发和Dependabot分支改动已整合，GitHub仅main；公开提交历史压缩为一次初始提交。历史备份置于仓库外，不上传。参考[分支整合](branch-integration.md)、[构建配置](github-build.md)。

下方为较早迭代的历史验收记录；其中“电视未更新/无远程CI”等描述仅适用于对应旧版本，以本节为当前交付结论。

## 当前版本（2026-11-17）

单包`io.github.tancan1998.xviewtv`，最低API23（Android6.0）、compile/target35，支持手机/平板与TV。此次使用本机android-virtual-screen与不注入屏幕输入的离屏设备组件测试；未派子代理，未更新电视。全部账号访问使用X网页接口，无付费API或浏览器导入产品入口。

## 手机刷新、主题语言与博主转发（当前）

- 手机首页/搜索结果/喜欢/关注/博主视频隐藏刷新按钮，使用共享到顶下拉刷新；TV保留按钮。首页真实拖动观察到“正在刷新”→真实80条媒体恢复，滑动未误开播放器；关注页最终真实拖动同样观察到刷新提示→7行博主恢复、无刷新按钮。
- 关注列表卡片与头像共用资源corner_creator；颜色、圆角、支持语言数组与所有UI文案均从core:designsystem资源读取。手机博主行使用普通触摸点击卡片，TV保留原焦点组件。
- 首次使用系统Locale列表匹配安装资源语言：简体中文zh-Hans/英文en，未支持语言英文回退；旧system迁移成具体匹配值，旧zh迁移zh-Hans，原明确语言保留。设置仅显示简体中文/English、无跟随系统。真实UI中文选中→英文→中文切换通过，语言匹配4项JVM回归通过；未改手机系统语言。
- 删除敏感内容开关、偏好访问和FilterMedia敏感过滤；旧hide_sensitive仅迁移删除，不影响内容展示。设置树确认无敏感选项，JVM确认敏感标记不再过滤媒体。
- 先本地接口实测UserTweets动态操作GET200，两页各21条帖子、第二页1条带视频的转发、游标前进，10设备检查通过后才接入博主页；正式来源包括原创与转发视频，保留原帖作者与播放源，新增转发/不可用原帖回归；真实UI首位博主第一页0视频、手动继续下一页后8条视频/可见播放卡片，空视频页保留继续加载。
- 最终优化包BUILD SUCCESSFUL 3m16s、Lint0错误11警告（额外一项为偏好迁移UseKtx建议）；38JUnit零失败、106结构、43Node/LAN加密回归通过，145项双语字符串同名覆盖。APK2726299字节、min23/target35、2DEX/4322类、V1V2，apksigner有效；SHA256 `aea835f494a333fc87c06fcd672a65e090bc7b533a05869fb888e058167c8ebd`。本机覆盖安装保留账号、测试包卸载；最终优化包黑盒确认无刷新按钮与真实下拉提示。电视未更新，TV遥控器/API23/长时压力未重新测试。

## 视频搜索、喜欢和关注（最新）

- 移除图片筛选与全屏图片浏览，首页仅展示视频/动图；保留视频封面与博主头像。新增视频/搜索/我的喜欢/关注入口，搜索 X 全站视频按最新排序，喜欢绑定当前账号，关注列表选择博主后进入其视频。
- 用户要求先本地测接口。无界面设备探针仅在显式 `verifyLibrary=true` 时访问已授权保存会话，凭据不导出；实际方法为 SearchTimeline POST（GET404）、Likes/Following/UserMedia GET，均获得HTTP200与游标前进。18项设备检查通过：搜索20→20、喜欢17→10、关注50→50、第二位博主7条视频；首位两页无视频但有游标，不能推断全部历史无视频。见[接口实测](video-library-api.md)。正式实现固定已实测方法，无404猜测回退。
- 固定方法复测搜索/喜欢成功，Following一次HTTP503；之后真实UI和最终优化包关注列表均加载成功。保留服务端临时失败边界，无演示或游客回退。
- 调试包坐标触摸验证全站输入/提交→20条视频、喜欢21条、关注真实博主→博主视频16条→播放器→视频页→关注列表恢复；最终优化包黑盒坐标再验搜索20条、喜欢21条、关注列表、无图片入口与账号恢复。无账号截图/响应交付。
- 播放器初始化硬解 `c2.qti.avc.decoder`，当前测试手机Telecom以 `GAIN_TRANSIENT / LOCK` 持有音频焦点，媒体请求无法获得焦点、00:00暂停；未绕过通话焦点，不能宣称本次连续播放通过。既有播放功能不作为本次视频播放成功证据。
- 最终 `:app:assembleOptimized :app:lintOptimized` + 4个JVM模块测试 BUILD SUCCESSFUL，3m23s；32JUnit零失败、101结构、43Node、LAN加密互操作通过，Lint0错误10警告。APK2756068字节，MT min23/target35、DEX2/类4313、V1/V2；apksigner有效（META-INF条目保护警告保留日志）。SHA256 `f3483c65931ed7303bf387f35c6bcb470e64fdd9c60136ef147d3d4ec925c796`。
- 本机已覆盖安装最终优化包并移除测试APK，保留账号；电视未更新，TV新版D-pad/API23真机/长时压力未测。分支与提交状态见交付说明。

## 手机触摸修复与加载反馈（之前版本）

- 用户反馈手机点击无响应。本轮已在旧优化包通过`tap_at`按实际按钮中心坐标复现：点击设置仍停首页。此前使用`node_action`的导航结果仅证明无障碍语义操作可用，不能作为手机触摸成功证据。
- TV Material按钮Surface保留焦点/遥控器/语义，新增共享`XButton`为手机补充pointer点击和按压反馈，使用最新回调、遵循disabled，滚动取消不会触发。所有设置、登录、代理、筛选、媒体卡片与播放器按钮统一接入；TV路径不增加触摸处理。
- 新增无假数据的微光卡片/缩略图骨架、旋转加载指示及indeterminate语义，覆盖初次加载/刷新、分页、会话恢复、账号验证、代理检测；完成/失败退出加载状态。视频缓冲继续使用Media3原生指示。手机不再强制抢筛选焦点。
- 修复优化包的实际坐标点击已覆盖设置→英文→中文、HTTP草稿模式、账号重新验证、刷新骨架、视频筛选、卡片打开、上一/下一视频、返回；卡片内滑动仍滚动、无误进入。真实账号重新验证返回成功；未保存测试代理草稿。
- 截图确认首页骨架布局，加载后恢复真实媒体；账号验证中显示旋转动画，结束后显示结果。无账号媒体截图交付。
- 最终`:app:assembleOptimized :app:lintOptimized`及4个JVM模块测试BUILD SUCCESSFUL，3m18s；27JUnit零失败、100结构、43Node、LAN加密互操作通过；Lint0错误10警告。
- 最新APK2717867字节（2.72MB），MT min23/target35、DEX2/类4271、V1/V2，apksigner有效。SHA-256：`3d793feffe5d3c9b9d79f218d57d0c22d1b116186c2626c727589843984d8f4d`。最终成品已覆盖安装并再次以坐标验证设置/双语切换。
- 本轮未更新电视或重新跑API23真机/长时heap；D-pad由既有TV组件处理，需新版TV实机回归。合并main与清理分支按本次交付记录执行。

## 功能与实际证据

- 129项中英文共享资源同名覆盖，设置提供跟随系统/简体中文/English并即时切换，不改系统语言。真实媒体流中英文文案与应用语言对应翻译目标均观察到。
- 手机1080×1920@420竖屏单列、1920×1080@320横屏多列和播放器实测；设置与筛选换行，输入宽度自适应。手机常驻设置，播放器打开时隐藏shell设置并移除背景网格输入，防止遮挡/误触；TV仍保留上键设置。未测大字体、折叠屏及完整TV矩阵。
- 手机设置直接填写默认遮蔽的完整Cookie，输入不进入rememberSaveable，验证开始/后台/离页清空。真实UI用合成缺少ct0的Cookie验证错误，原账号保留；真实Cookie解析和X身份匹配使用本机已授权保存会话，仅内存执行，设备组件测试通过。未通过UI重新填写真实Cookie并保存替换当前账号。
- 真实HLS视频进度持续增长、MP4清晰度入口、上一/下一视频跨帖切换与返回媒体流通过。队列来自当前筛选快照、跨帖跳过图片，首尾禁用不循环；边界另有JVM测试。
- 实际硬件codec为`c2.qti.avc.decoder`；Media3按硬件标志优先排序并允许平台初始化回退，不承诺所有格式都能硬解。
- 虚拟屏真实视频右侧长按日志`boost_begin speed=2.0`/`boost_end speed=1.0`，松手恢复。独立RPC双击受往返延迟影响，未作为双击成功证据；改为设备离屏组件MotionEvent测试验证左右双击/中心不seek、1.5→3→1.5倍、0.75→2→0.75倍取消恢复、重复清理。原生控件与TimeBar继续接收自身输入。
- 设置快退/快进分别提供5/10/15/30/60秒并持久保存，默认各10秒；UI切换后退5秒已验证。相同数值用于双击与Media3快进退按钮。
- TV内置扫码页静态及动态提示跟随手机浏览器中文/英文；加密协议未改。Node43与LAN forge/WebCrypto互操作/篡改拒绝通过；双语配对页未在真实TV重新扫码全流程验证。

## 构建与检查

ARM64 Alpine/JDK17/Gradle8.11.1/SDK35/固定ARM64 AAPT2，本地缓存离线单worker构建。API23首次完整`:app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :core:network:test :core:domain:test :core:provider:test :provider:demo:test`为BUILD SUCCESSFUL，3m13s。双语错误提示最终修订后再执行应用/设备测试包/Lint；最终结果与签名摘要见交付文件。

- JUnit27项、零失败；其中新增播放队列/首尾、寻址边界、倍速恢复、非法偏好值测试5项。
- 设备离屏组件8项通过：双击左/右/中心、长按释放/取消、清理、错误Cookie、真实身份验证。实测验证入口不启动Activity、不向屏幕注入输入，不读取其他应用。
- 结构100项通过，Node43项通过、LAN加密互操作通过，双语资源129项一致；diff检查及最终APK元数据见交付文件。
- 最终双语错误提示修订后`:app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug`再次BUILD SUCCESSFUL，2m3s；Lint0错误10警告（旧API属性、备份规则、图标目录和KTX建议），无新增阻断。
- 最终APK13784293字节，MT核实minSdk23/target35、V1/V2；apksigner verify成功。SHA-256：`4e45936165c56bf677035724a1ec4ba363c7f642d9f8afef8a86142cf0cf42a3`。Debug证书，不作为Release发布签名。
- 最终API23包重新安装后，显式真实会话选项8项通过；默认不读取会话/不联网的7项也通过，临时测试包已卸载。
- Lint、构建和设备组件输出见交付日志；远程CI、Release签名、广泛API/TV矩阵未执行。

设备组件调用（需先安装主包与测试包）：

```sh
adb shell am instrument -w io.github.tancan1998.xviewtv.test/io.github.tancan1998.xviewtv.PlaybackComponentInstrumentation
# 下列选项仅在明确授权沿用设备已保存会话时执行真实网络校验；默认不读凭据、不联网
adb shell am instrument -w -e verifySavedSession true io.github.tancan1998.xviewtv.test/io.github.tancan1998.xviewtv.PlaybackComponentInstrumentation
```

## 安装包体积优化（同日）

- Release与新增`optimized`构建启用R8代码优化/混淆与资源压缩；`optimized`非debuggable、使用既有本机debug证书，可覆盖之前测试包，保留中英文与全部ABI。未引入整包keep或禁用混淆。
- APK从13784293降到2717875字节，减少80.28%；MT统计DEX从30690704降到3927412字节，类18573→4259、DEX7→2。原生库与双语未裁掉；二维码页面、协议与forge脚本仍完整打包。基线profile增加约5.6KB。
- 最终`:app:assembleOptimized :app:lintOptimized`成功1m14s（首次R8 3m55s），Lint0错误10警告。MT min23/target35、V1/V2；apksigner验证成功。SHA-256：`9c72436093c1f153c5e2a6e70c3a1dd1e0ced6d68f7b5e354bb941ca692137cf`。
- 优化包已覆盖安装，真实账号冷启恢复、媒体缩略图/翻译、HLS连续22秒、上一/下一视频、返回、中文→英文→中文、语言持久化、主动重新验证会话成功；无此次应用运行崩溃。
- 尝试用既有androidTest直接调用优化包内部类失败：R8裁剪/重命名的Kotlin内部方法导致测试进程NoSuchMethodError。未通过增加生产keep弱化压缩；已移除测试构建实验并卸载临时测试包。之前8项组件测试仅代表未混淆API23构建，不能写成优化包组件全通过；优化包完整长按/双击设备自动化、TV真实扫码仍待独立黑盒矩阵。测试试验未改变最终APK SHA。
- 交付首选`xview-optimized.apk`；原Debug包仅用于开发诊断。Release正式证书和远程CI仍未配置。

## 边界

本次未做API23真机安装、更新后TV遥控器/媒体回归、SOCKS5媒体矩阵、软件codec回退故障注入、长时heap/GC/低内存压力、后台进度恢复、链接过期重解析。先前API28小米电视扫码/Keystore/HTTP代理/冷启/退出结果仅代表先前版本。维持媒体窗口160、图片LRU12MiB/2路采样、视频缓冲16MiB/5–15秒与IO释放策略，不声称已证明无泄漏。

临时图树与截图仅用于本机检查，不交付账号媒体截图；无Cookie或响应入仓。虚拟屏关闭、控制释放、桥停止，手机前台Minis且旋转0；最终清理以交付说明为准。
