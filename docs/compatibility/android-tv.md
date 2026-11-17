# Android 手机与 TV 兼容性

## 配置

| 配置 | 值 |
| --- | --- |
| minSdk | 23 / Android 6.0，应用与全部Android库一致 |
| compileSdk / targetSdk | 35 / Android 15 |
| Java / Kotlin toolchain | JDK17 |
| core library desugaring | desugar_jdk_libs 2.1.5，API23–25的java.time仍需脱糖 |

版本由`gradle/libs.versions.toml`统一维护。2026-11-17按用户要求将最低版本上调为API23，Android5.0/5.1不再支持。所有可安装版本都具备API23 Keystore AES-GCM接口；设备密钥失效或存储不可用仍明确失败，不回退明文。

同一APK提供LAUNCHER和LEANBACK_LAUNCHER，Leanback与触屏均不是安装必需功能。窄屏单列、宽屏多列，支持手机竖屏与横屏；TV保留D-pad焦点及上键设置。API级别、ABI、硬件解码和厂商系统差异分别验证，不能以minSdk声明代替设备认证。

## 实测与待测

- 本机Android手机：1080×1920@420竖屏、1920×1080@320横屏虚拟显示，中英文切换、设置、真实媒体流、硬件解码、视频切换和离屏组件测试。具体结果见[验证记录](../development/verification.md)。
- 小米MiTV-ANSP0 Android9/API28、armeabi-v7a：先前版本已验证正式包扫码、账号确认、加密存储、冷启、重新验证、本地退出、代理与D-pad。此次自适应版本未更新电视。

| 系统 | API | 待验证重点 |
| --- | --- | --- |
| Android6–7.1 | 23–25 | 最低版本安装、Keystore、脱糖、内存、输入法与遥控器 |
| Android8–9 | 26–28 | 新版TV焦点、媒体播放、硬件解码与生命周期 |
| Android10–11 | 29–30 | 手机与TV缩放、资源释放 |
| Android12–13 | 31–33 | 大字体、多窗口、显示与系统行为 |
| Android14–15 | 34–35 | target35边衬、返回、前后台与方向切换 |
| Android16及后续 | 36+ | 按真实设备回归，不保证未来系统行为 |

## 检查

```sh
python3 tools/scripts/verify_project.py
sh ./gradlew :core:network:test :core:domain:test :core:provider:test :provider:demo:test :app:lintDebug :app:assembleDebug
```

检查最终APK的minSdk23、target35与签名；不以本机Debug构建代替API23真机或所有TV认证。后续依赖升级检查最低SDK，禁止用`tools:overrideLibrary`掩盖运行时不兼容。

参考：[Compose for TV](https://developer.android.com/training/tv/playback/compose)、[Java API脱糖](https://developer.android.com/studio/write/java8-support)、[AndroidX TV](https://developer.android.com/jetpack/androidx/releases/tv)。
