# 标签笔记数改为 DAO 侧聚合 —— 设计说明

日期：2026-10-02
状态：已实现，构建 / 单测 / Lint 全绿，实机复核通过（见文末验证结论）

## 背景

标签页的索引行会给每个标签补一行二级信息：「N 条笔记」（无笔记时为「暂无笔记」）。
这个数字此前是在 `TagsViewModel` 里**内存聚合**出来的：

```kotlin
noteRepository.getAllNotes().collect { result ->
    val counts = buildMap<Long, Int> {
        result.data.forEach { note -> note.tags.forEach { tag -> put(tag.id, (it[tag.id] ?: 0) + 1) } }
    }
    ...
}
```

当时选择内存聚合的理由是「标签数量远小于笔记数量，为此加一条专用查询不划算，也不值得让所有
测试替身跟着改」。这个判断在小数据量下成立，但它掩盖了一笔线性放大的代价。

## 改造前的代价

`NoteRepository.getAllNotes()` 取的是 `noteDao.getAll()`，而 `NoteRepositoryImpl` 会把每条笔记
`hydrate()` 一遍。`hydrate()` 内部是三次查询：

| 步骤 | 查询 |
|------|------|
| 1 | `noteCardDao.getByNoteIdOnce(noteId)` |
| 2 | `noteBlockDao.getByNoteIdOnce(noteId)` |
| 3 | `tagDao.getByNoteId(noteId)` |

于是「显示一个数字」的总代价是：

- **查询数**：`1 + 3N`（N = 未删除笔记数）
- **内存**：把全部笔记的卡片、区块、标签都构造成领域对象
- **触发频率**：`notes` 表**任何**一次写入都会让 `getAll()` 重新发射 → 整库重新 hydrate。
  改一条笔记的标题，标签页就要重算一次全量
- **时机**：标签页 `init` 里就订阅，用户即便不关心这个数字也已经付出代价

这是一笔典型的「为了副产物迁移了主数据集」。

## 方案

把计数下推到数据层，单条 SQL 算完。落在 `TagDao`，与既有的
`getByNoteId`（`tags JOIN note_tags`）放在一起，方向相反：

```sql
SELECT nt.tagId AS tagId, COUNT(*) AS noteCount
FROM note_tags nt
JOIN notes n ON n.id = nt.noteId
WHERE n.deletedAt IS NULL
GROUP BY nt.tagId
```

```kotlin
data class TagNoteCount(val tagId: Long, val noteCount: Int)   // 投影，与 TagDao 同文件

@Query("""...""")
fun countNotesPerTag(): Flow<List<TagNoteCount>>
```

链路：`TagsViewModel` → `TagRepository.getNoteCountsByTag()` → `TagRepositoryImpl` →
`TagDao.countNotesPerTag()`。

改造后代价：**1 条查询**，不构造任何笔记对象；`notes` 变化时只重跑聚合，不再 hydrate。

### 语义等价性

| 关注点 | 结论 |
|--------|------|
| 软删笔记 | 旧实现经 `getAllNotes()`，本身就带 `deletedAt IS NULL` 过滤；SQL 显式 `WHERE n.deletedAt IS NULL`，一致 |
| 重复计数 | `note_tags` 是 `(noteId, tagId)` 联合主键，同一对不会重复，`COUNT(*)` 即笔记条数 |
| 只 join `notes` 不 join `tags` | 理论上孤立关联会被计入，但 `note_tags.tagId` 有 `CASCADE` 外键，孤立行不存在 |
| 关联变更 / 软删 / 恢复 | 查询同时引用 `note_tags` 与 `notes`，Room 对两张表都注册观察，都会触发重新发射 |
| 值为 0 的标签 | `GROUP BY` 不产生空组，映射里查不到；界面侧沿用 `?: 0` 兜底为「暂无笔记」，与旧行为相同 |

### 接口改动的影响面

`TagRepository` 是纯抽象接口（本仓库所有 repository 接口都不带默认实现），新增
`getNoteCountsByTag(): Flow<RepositoryResult<Map<Long, Int>>>` 会让 **9 个手写测试替身**编译失败：

`TagsViewModelTest` / `HomeViewModelSearchTest` / `NoteViewModelAdvancedBlockTest` /
`NoteViewModelBlockOpsTest` / `NoteViewModelKnowledgeCardTest` / `NoteViewModelReadOnlyTest` /
`NoteViewModelReviewTest` / `NoteViewModelTagSelectionTest` / `NoteViewModelTodoTest`

这 9 处都已补上 `emptyMap()` 桩（`TagsViewModelTest` 的替身多了一个
`noteCountsByTag` 构造参数，以便真的喂数做断言）。

这是本次改造唯一值得犹豫的地方，两个候选处理方式都否掉了：

- **给接口加默认实现**：能让 9 个替身零改动，但等于让生产接口自带一段假实现，
  真正的生产实现漏写覆写时不会有任何编译期报错 —— 正是「静默跳过」那类缺陷的温床。
- **新建 `TagStatsRepository` 专放这条查询**：改动面确实更小，但会让「标签相关的读」散在
  两个 repository 里，后来人找标签数据时要先猜在哪边。为绕开测试改动而扭曲分层不划算。

替身全部补齐是机械的两行改动，一次性、可被编译器完整覆盖，比上面两个都更诚实。

## 验证口径

- `./gradlew :app:assembleDebug` —— 通过
- `./gradlew :app:testDebugUnitTest` —— 通过（新增 4 条：`TagsViewModelTest` 的
  `noteCountsAreTakenFromRepositoryAggregate`，以及 `TagRepositoryImplTest` 的映射 / 空结果 /
  DAO 失败兜底三条）
- `./gradlew :app:lintDebug` —— 通过，报告与改动前一致（即本次改动文件零新增告警）
- 生成代码核对：`TagDao_Impl` 里聚合流的失效表集合是 `{"note_tags", "notes"}`，
  两张表任一变化都会重新发射
- 实机（MuMu，1920×1080 帧）：标签页逐个核对 16 个标签，与 SQL 真值完全一致 ——
  14 个标签各「1 条笔记」，`OCR` / `卡片` 为「暂无笔记」
- 实机联动：把只挂了单个标签的笔记（`红黑树` / `数据结构`）移入回收站，
  `数据结构` 随即变「暂无笔记」；从回收站恢复后又回到「1 条笔记」。
  验证完成后数据已还原（`deletedAt` 回 `NULL`，全库仍为「14 个标签有笔记 / 16」，
  仅原有的 id=1 处于删除态）

`TagRepositoryImplTest` 只覆盖「投影行 → 以 tagId 为键的 Map」这一步与失败兜底；
真正的 `GROUP BY` 语义由 Room 生成的 SQL 保证，属于无法用 mockk 替代的部分。
