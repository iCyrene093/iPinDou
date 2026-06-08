# iPinDou

安卓拼豆工具 app。拼豆（perler / fuse beads）是在方格板上按颜色代码摆放豆子、熨烫后形成像素图案的手作游戏；本项目提供从图片到拼豆图纸的移动端工作流。

## 功能

- 导入手机图片并按目标宽高生成拼豆图纸。
- 可选“自动去背景”：基于边缘连通背景色做轻量 flood-fill，适合白底、纯色底或近似纯色杂乱边缘。
- 使用内置 24 色拼豆色卡将图片量化，每格显示颜色代码。
- 支持手动点按/拖动修改格子颜色。
- 支持水平镜像、垂直镜像，方便反向熨烫和转印。
- 支持导出带网格与颜色代码的 PNG 图纸到系统图库。
- 实时统计图纸尺寸、总豆数和各颜色用量。

## 项目结构

- `app/src/main/java/com/ipindou/app/MainActivity.java`：主界面、图片导入、生成、镜像、导出与统计。
- `app/src/main/java/com/ipindou/app/PatternView.java`：可编辑拼豆图纸网格视图。
- `app/src/main/java/com/ipindou/app/core/`：可测试的核心拼豆色卡、图纸模型和图片量化/去背景逻辑。

## APK 构建

当前仓库提供了一个不依赖 Android Gradle Plugin / Android SDK 下载的离线 debug APK 构建任务，适合受限沙箱或临时验收：

```bash
gradle :app:assembleDebug
```

生成文件位于 `app/build/outputs/apk/debug/app-debug.apk`。该离线 APK 使用仓库内的手写构建器生成并签名；完整生产版仍建议在安装 Android SDK 后使用标准 Android 工具链构建。
