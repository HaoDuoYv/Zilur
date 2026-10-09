# 主题预览

目录里是两类东西，别混：

1. **从源码生成的预览**——值全部取自 `ui/theme/ThemePalettes.kt` 的 `palettePaint()`，
   所以和真机永远同源，不会出现"预览好看、真机是另一个色"。
2. **真机截图**——MuMu 模拟器上跑出来的 `device-*.png`，用来验收"实际观感 + 各页适配"。
   这类是手截的，改配色后要重截。

| 文件 | 类别 | 内容 |
| --- | --- | --- |
| `palette-preview.html` | 生成 | 两套外观 × 浅深，全部色值 + 圆角阶梯 + **对比度实测表** |
| `animal-island-light.png` | 生成 | 动森 · 浅色的 HTML 渲染图 |
| `device-animal-light-notes.png` | 真机 | 动森浅色 · 笔记页 |
| `device-animal-dark-notes.png` | 真机 | 动森深色 · 笔记页 |
| `device-animal-light-appearance.png` | 真机 | 动森浅色 · 外观设置页 |
| `device-animal-dark-appearance.png` | 真机 | 动森深色 · 外观设置页 |
| `device-animal-ui-*.png` | 真机 | 首页 / 搜索框 / 助手 / ＋ 面板 / 我的 各页适配 |
| `animal-island-component-audit.md` | 审计 | 对照 `AnimalIslandUI` 组件库的组件化替换清单、实施判据与执行顺序 |

## 重新生成

```bash
# 1. 从 Kotlin 源码生成 HTML（值取自 palettePaint()）
./gradlew :app:testDebugUnitTest --tests "com.example.zhilu.ui.theme.ThemePreviewGeneratorTest" --no-daemon
# 产物：app/build/theme-preview/index.html
```

截图（可选，需要 Edge/Chrome）：

```powershell
$edge = "C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"
$uri = "file:///" + ((Resolve-Path "app\build\theme-preview\index.html").Path -replace "\\","/")
& $edge --headless=new --disable-gpu --user-data-dir="$env:TEMP\edgeprof" `
  --window-size=1100,2400 --screenshot="$env:TEMP\theme.png" $uri
```

## 为什么需要它

配色是**视觉**产物，"看起来对不对"没法靠断言回答。而设备不一定随时可用，
所以把色值摊成一份可渲染的页面：换配色时能立刻发现"某一档糊了"，
不必先装到设备上再一个个页面翻。

`AccentPaletteTest` 负责**卡住底线**（文字 4.5 / 图形 3.0），这一页负责**让你看见**。
两者互补：前者会红，后者会难看。
