# iPinDou

安卓拼豆工具 app。拼豆（perler / fuse beads）是在方格板上按颜色代码摆放豆子、熨烫后形成像素图案的手作游戏；本项目提供从图片到拼豆图纸的移动端工作流。

## 功能

- 导入手机图片并按目标宽高生成拼豆图纸。
- 可选“自动去背景”：基于边缘连通背景色做轻量 flood-fill，适合白底、纯色底或近似纯色杂乱边缘。
- 使用 MARD 291 个核心标准色将图片按感知色差量化，每格显示对应 MARD 色号；自动去背景时会额外用透明格标记被移除的背景。
- 支持手动点按/拖动修改格子颜色，并可在图纸区域双指缩放查看细节。
- 支持水平镜像、垂直镜像，方便反向熨烫和转印。
- “生成并保存图纸”会在生成后立即把带网格与颜色代码的 PNG 图纸保存到系统图库，仍可通过“导出PNG”手动再次导出。
- 实时统计图纸尺寸、总豆数和各颜色用量。

## 项目结构

- `app/src/main/java/com/ipindou/app/MainActivity.java`：主界面、图片导入、生成、镜像、导出与统计。
- `app/src/main/java/com/ipindou/app/PatternView.java`：可编辑拼豆图纸网格视图。
- `app/src/main/java/com/ipindou/app/core/`：可测试的核心拼豆色卡、图纸模型和图片量化/去背景逻辑。

## APK 构建

默认构建命令仍然是：

```bash
gradle :app:assembleDebug
```

在已安装 Android SDK platform 且 Android Gradle Plugin 可用（可联网解析或已在 Gradle 缓存中）的环境中，该任务会应用标准 `com.android.application` 插件，编译 `MainActivity`、`PatternView`、资源文件和 `app/src/main/java/com/ipindou/app/core/` 中的核心图纸引擎，生成完整 debug APK。

如果当前机器没有可用 Android SDK platform，或无法获得 Android Gradle Plugin，`:app:assembleDebug` 才会回退到仓库内的离线构建器，生成一个仅用于受限沙箱冒烟验证的极简 APK。也可以显式运行离线任务：

```bash
gradle :app:assembleManualDebug
```

生成文件位于 `app/build/outputs/apk/debug/app-debug.apk`。如需强制离线回退，可设置 `-Pipindou.forceManualApk=true` 或环境变量 `IPINDOU_FORCE_MANUAL_APK=true`；如需在诊断环境中强制尝试 Android 工具链，可设置 `-Pipindou.forceAndroidBuild=true` 或 `IPINDOU_FORCE_ANDROID_BUILD=true`。
