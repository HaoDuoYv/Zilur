# 动物岛组件替换审计（对照 AnimalIslandUI）

> 日期：2026-10-09
> 对照基线：`E:\study\gitproject\ui\AnimalIslandUI` —— Android Compose 版在 `animalislandui/src/main/java/ml/liuyuhong/animalislandui/`（18 个组件 + theme），形态规格与设计铁律见该库 `SKILL.md`。
> 审计范围：`app/src/main/java/com/example/zhilu/ui/` 全量界面（component / home / note / search / review / reminder / settings / assistant / trash / camera / create / navigation）。

## 一、结论摘要

1. **通用交互件尚未统一收口**：按钮（21 文件）、加载指示（12 处）、弹窗（7 处）、输入框（4 处纯默认）、勾选（3 处）、下拉菜单（12 处）、底部弹层（9 处）、分割线（6 处）仍在各屏**逐处手写**，同一语义存在多套画法。
2. **语义最完整的三个缺口**：弹窗（对标 `AnimalModal` 的 blob 外形）、按钮（对标 `AnimalButton` 的「双层立体键」）、输入框（对标 `AnimalInput` 的胶囊 + 底部厚度）。
3. **正路不是直接引入库代码**：库是**动森单主题硬编码**（如 `BgColorContent=#F7F3DF`），直接 import 会破坏纸墨外观。正确做法是**按库的形态规格，把散落用法收敛到受 `palettePaint()` 驱动的双主题 wrapper**——这与既有 `AppCard` / `AppSwitch` / `AppSheetAction` / `TagChip` / `BouncingDots` 的范式一致。
4. 统一判据：组件的「有无材质（描边/厚度/立体键）」一律读 `PalettePaint.componentBorder != Color.Unspecified`，**不要各自比较 `ThemePalette.ANIMAL_ISLAND`**。

## 二、组件库能力速览（Compose 版）

| 组件 | 形态要点 | 可对标的应用缺口 |
| --- | --- | --- |
| `AnimalButton` | 双层立体键：按钮面上移 5dp，底下同色厚度层；按压厚度 5→1dp；pill 50dp；PRIMARY/DANGER 两种 | 全站 Button/OutlinedButton/TextButton |
| `AnimalInput` | 胶囊 50dp + 2.5dp 描边；聚焦转 `#FFCC00` 黄 + 黄光晕；输入层下方厚度层 | 纯默认 OutlinedTextField 4 处 |
| `AnimalSwitch` | 轨道 52×28 + 2.5dp 描边；手柄 y=-2dp 浮起 + 底部 3dp 实心厚度 | 已有 `AppSwitch` 覆盖（无需再换） |
| `AnimalCheckbox` | 方框 18/22/28dp、圆角 8dp、2~3dp 描边；勾号弹簧弹出 | TodoBlock / TagManageSheet / TagDialogs |
| `AnimalModal` | 整圈三次贝塞尔 **blob 外形**；标题 28sp；右下 Cancel+Confirm；右上飘出式关闭钮 | 7 处 M3 AlertDialog |
| `AnimalCollapse` | 2dp 描边 + 18dp 圆角容器；头部主色圆钮 ＋/－ 旋转；叶子装饰 | 设置页可折叠分组（当前无此形态） |
| `AnimalSelect` | 触发条 45dp 胶囊 + 描边；菜单 12dp 圆角 + 1dp 描边 | 12 处 DropdownMenu |
| `AnimalTabs` | 2dp 描边 + 20dp 圆角容器；选中项 15% 主色底 + 叶子摆动 | 已启发 `SegmentedToggle` / `ReviewSubTabs` |
| `AnimalCard` | 20dp 圆角 + 暖褐投影；TITLE 型四角不等 | 已由 `AppCard` 覆盖 |
| `AnimalDivider` | 5 型装饰线（位图资源） | 已由 `ZhiLuDivider`/`WaveDivider` 现画替代 |
| `AnimalLoading` / `IslandAnimation` | 全屏加载：黑底 + 岛屿动画 + 擦除转场 | 过重，仅借鉴动势；小徽章不适用 |
| `AnimalCodeBlock` | 代码块语法高亮 | 应用代码块配色已取库原值 |
| 其余 | `AnimalIcon`(10 图标)、`AnimalTable`、`AnimalTime`、`NookPhone`、`AnimalFooter`、`AnimalTypewriter` | 暂无对标点 |

## 三、应用现状盘点

### 3.1 已收敛的自研组件（两态表现已有，作为新 wrapper 的范式）

| 组件 | 两态表现 | 状态 |
| --- | --- | --- |
| `AppCard` | 纸墨白纸+阴影 / 动森大圆角+描边+暖褐投影；`irregular` 四角不等 | ✅ 范式 |
| `AppSwitch` | 纸墨 M3 原样 / 动森自绘胶囊+浮起手柄 | ✅ 范式 |
| `AppSheetAction` / `QuietAction` | 弹层整宽动作 / 行内次要文字动作 | ✅ |
| `TagChip` / `SegmentedToggle` / `ZhiLuDivider` / `WaveDivider` | 胶囊 / 分段控件 / 发丝线 / 波浪线 | ✅ |
| `ToySurface` / `PressScale` / `BouncingDots` / `GeneratingIndicator` | 立体厚度底盘 / 按压缩放 / 动森三球 / 生成态双态 | ✅ |
| 本次重构新增 `ReviewSubTabs` / `ReviewHeatmapCard` / `ReviewStatsGrid` / `ReviewPlanCard` / `TodoTaskCard` | 复习中心三段式；统一 `AppCard` 外壳 | ✅（卡内主按钮列为 AppButton 收敛对象） |

### 3.2 尚未收敛的原生 M3 用法（按类型）

**① 加载/进度指示（12 处）**
- `CircularProgressIndicator`：`assistant/AiMessageBubble.kt:107`、`camera/CameraChrome.kt:92`、`note/NoteEditScreen.kt:571`、`note/latex/LatexRenderer.kt:257`、`navigation/GlobalAiStatusBar.kt:55`、`note/NoteSaveStatus.kt:57`、`note/ReviewSheet.kt:63`、`note/blocks/ImageBlockView.kt:62` —— 均手工传色，**未走 `GeneratingIndicator` 的动森三球分支**。
- `LinearProgressIndicator`：`trash/TrashScreen.kt:86`、`review/ReviewCenterScreen.kt:104`、`assistant/AssistantScreen.kt:625`、`settings/SettingsScreen.kt:113` —— 纯 M3 默认四段色块条。

**② 弹窗（7 处，全部纯 M3 默认）**
`home/HomeDialogs.kt:14`、`search/TagDialogs.kt:42,74,101`、`trash/TrashDialogs.kt:16,38`、`settings/DataSection.kt:65`、`review/TodoDialogs.kt:20`。

**③ 输入框（6 处，其中 4 处纯默认）**
- 已双态（提取复用即可）：`settings/AiConfigFormFields.kt:83`（`AiTextField`）、`home/HomeSearchField.kt:86`（纸墨/动森两分支）。
- 纯默认/半默认：`search/TagManageSheet.kt:101`、`search/TagDialogs.kt:46`、`note/TodoBlock.kt:61,93`、`settings/ReviewIntervalScreen.kt:107`。

**④ 按钮（约 21 文件）**
- `TextButton`（对话框次要动作，约 17 处）、`Button`（6 处）、`OutlinedButton`（含本次复习中心卡片 5 处）、`FilledTonalButton`（1 处）——**逐处手写 shape**，同一语义在对话框（TextButton）、列表行（QuietAction）、弹层（AppSheetAction）三套并存。

**⑤ 勾选（3 处）** `note/TodoBlock.kt:154`、`search/TagManageSheet.kt:208`、`search/TagDialogs.kt:124`。

**⑥ 分割线（6 处绕过 ZhiLuDivider）** `assistant/AiMessageContent.kt:217,341,356`、`note/NoteHeaderEditors.kt:130`、`note/knowledge/KnowledgeCardItem.kt:189`、`note/TodoBlock.kt:130`。

**⑦ 胶囊（2 处）** `settings/AiConfigFormFields.kt:45`（供应商预设）、`home/HomeScreen.kt:338`（历史搜索词）。

**⑧ 其他** `DropdownMenu` ×12；`ModalBottomSheet` ×9（形状不随外观）；`TopAppBar`（`component/AppTopBar.kt`，动森下缺圆角/底边线）；`SnackbarHost`（`navigation/AppShell.kt:167`，无自定义视觉）；`ListItem`（`assistant/AssistantScreen.kt:600`）。

## 四、替换建议清单

| # | 现状 | 对标 | 建议做法 | 落点 |
| --- | --- | --- | --- | --- |
| 1 | 纯默认 OutlinedTextField ×4 | `AnimalInput` | 把 `AiTextField` 的双态实现提取为 `AppTextField`（动森：`componentBorder` 描边 + 20dp 圆角；纸墨：M3 默认），替换 4 处 | `ui/component/AppTextField.kt` |
| 2 | 进度/加载 12 处 | `IslandAnimation`（仅借鉴） | ① 生成类 8 处统一走 `GeneratingIndicator` 双态；② 页面级 4 条新增 `AppProgressBar`（动森加描边+圆角，纸墨 M3） | `GeneratingIndicator.kt`、`ui/component/AppProgressBar.kt` |
| 3 | Button 系 21 文件 | `AnimalButton` | 新增 `AppButton`（primary/tonal/outline/quiet 变体）：动森用 `ToySurface` 立体键（按压厚度 5→1dp），纸墨保持平面；对话框/卡片动作统一入口 | `ui/component/AppButton.kt` |
| 4 | AlertDialog ×7 | `AnimalModal` | 新增 `AppDialog`：纸墨保留 M3 观感；动森套 blob 外形（`GenericShape` 贝塞尔）+ 暖褐描边 + 右上飘出关闭钮 | `ui/component/AppDialog.kt` |
| 5 | Checkbox/RadioButton ×3 | `AnimalCheckbox` | 新增 `AppCheckbox` / `AppRadioButton`：8dp 圆角方框 + 描边 + 勾号弹簧；照抄 `AppSwitch` 的双态骨架 | `ui/component/` |
| 6 | HorizontalDivider ×6 | `AnimalDivider`（位图，不用） | 直接改调 `ZhiLuDivider` / `WaveDivider`（纯规范化，零风险） | 6 个调用点 |
| 7 | FilterChip/AssistChip ×2 | `AnimalTabs` 配色 | 收敛为 `AppChip`（复用 `TagChip` 的双态描边语言） | `ui/component/AppChip.kt` |
| 8 | DropdownMenu ×12 | `AnimalSelect` 菜单 | 包一层 `AppDropdownMenu`：菜单 12dp 圆角 + 描边随外观 | `ui/component/AppDropdownMenu.kt` |
| 9 | ModalBottomSheet ×9 | `AnimalModal`/`AnimalCard` | 弹层形状随外观（纸墨 M3 默认；动森大圆角+描边）；**先定形状规范再动手**，回归面最大 | `Theme.kt` / 各弹层 |
| 10 | AppTopBar | `AnimalTabs` 容器语言 | 动森下补圆角 + 底边线即可（微调现有封装） | `AppTopBar.kt` |

## 五、优先执行（按 价值×成本 排序）

1. **AppTextField**（对标 AnimalInput）——已有成熟先例，提取即用，4 处受益。
2. **加载指示收口**——现成 `BouncingDots`，属「已有轮子没用上」，12 处受益、风险极低。
3. **AppButton**（对标 AnimalButton）——覆盖面最广，一次收敛 21 文件，是整套风格的灵魂。
4. **AppDialog**（对标 AnimalModal）——7 处集中、语义一一对应，动森态辨识度收益最大。
5. **AppCheckbox**（对标 AnimalCheckbox）——规格最明确，照抄 AppSwitch 骨架。
6. **Divider 规范化**——6 处改调用，零风险顺手做。
7. **AppChip**——与 TagChip 同源，收尾式小活。
8. **AppDropdownMenu**——数量多、单处收益小，可排期。
9. **ModalBottomSheet 形状**——先规范后实施，回归面大。
10. **AppTopBar 微调**——一行式补丁。

## 六、实施时的判据（硬规则）

- 双态判据唯一来源：`PalettePaint.componentBorder != Color.Unspecified`，禁止散落比较 `ThemePalette.ANIMAL_ISLAND`。
- 圆角取 `MaterialTheme.shapes`（`ShapeTokens`），不写死；立体厚度统一走 `ToySurface`。
- 颜色只走语义 token；本页审计中唯一「固定色」例外是复习热力图绿阶（`HeatmapLevelPalette`，用户指定不跟随主题）。
- 新组件一律 `remember` 常量避免组合期分配，交互反馈统一 `pressScale` + haptics 既有模式。

## 七、本次不动的地方及理由

- **BasicTextField 自研编辑器**（`note/blocks/*`、`TagPickerInline`、`CardHeader` 等）：编辑器地基，非「原生组件未包装」问题。
- **相机页 chrome**（`camera/*`）：特殊语境（取景器叠加层），沿用现有实现。
- **`AnimalLoading` 全屏动画 / `NookPhone` / `AnimalFooter` / `AnimalTime`**：无对标场景或过重，不引入。
- **复习热力图固定绿阶**：刻意偏离主题色的产品决策（2026-10-09 用户指定），非欠账。
