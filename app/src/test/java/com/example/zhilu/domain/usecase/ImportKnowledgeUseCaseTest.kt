package com.example.zhilu.domain.usecase

import android.content.Context
import android.net.Uri
import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.data.local.file.ArchiveManager
import com.example.zhilu.data.local.file.MediaFileManager
import com.example.zhilu.domain.model.Block
import com.example.zhilu.domain.model.BlockType
import com.example.zhilu.domain.model.Media
import com.example.zhilu.domain.model.Note
import com.example.zhilu.domain.model.Tag
import com.example.zhilu.domain.repository.MediaRepository
import com.example.zhilu.domain.repository.NoteRepository
import com.example.zhilu.domain.repository.TagRepository
import com.example.zhilu.export.DtkExporter
import com.example.zhilu.export.JsonExporter
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ImportKnowledgeUseCaseTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val noteRepository: NoteRepository = mockk(relaxed = true)
    private val tagRepository: TagRepository = mockk(relaxed = true)
    private val mediaRepository: MediaRepository = mockk(relaxed = true)
    private val mediaFileManager = MediaFileManager(context)
    private val useCase = ImportKnowledgeUseCase(
        context,
        noteRepository,
        tagRepository,
        mediaRepository,
        mediaFileManager
    )

    @Test
    fun importEmptyNote_succeeds() = runTest {
        val dtkFile = buildDtk(
            notes = listOf(Note(title = "导入测试")),
            mediaUris = emptyList()
        )
        coEvery { noteRepository.insertNote(any()) } returns RepositoryResult.Success(Note(id = 1L))

        val result = useCase.import(Uri.fromFile(dtkFile))

        assertTrue("空笔记导入应成功", result.isSuccess)
    }

    /**
     * **本次改造的核心断言**：图片按 mediaId 接回新落盘的 uri。
     *
     * 旧实现靠 `block.content.contains("media/<文件名>")` 认领，而块里存的是源设备的内部
     * 绝对路径（`.../files/images/img_a.png`），永远不含 `media/media_7.png` ——
     * mediaId 回落 0、uri 指向源设备，换台机器就是裂图。
     */
    @Test
    fun import_joinsImageByMediaIdToNewLocalUri() = runTest {
        val newUri = "file:///data/user/0/com.example.zhilu/files/images/img_new.png"
        val manager = mockk<MediaFileManager>()
        every { manager.importUriToInternal(any()) } returns Result.success(newUri)
        val localUseCase = ImportKnowledgeUseCase(
            context, noteRepository, tagRepository, mediaRepository, manager
        )

        val dtkFile = buildDtk(
            notes = listOf(
                Note(
                    title = "带图",
                    blocks = listOf(
                        Block(
                            type = BlockType.IMAGE,
                            content = "7|file:///old-device/files/images/img_a.png",
                            sortOrder = 0
                        )
                    )
                )
            ),
            mediaUris = listOf(7L to "media/media_7.png")
        )
        coEvery { mediaRepository.insertMedia(any()) } returns RepositoryResult.Success(99L)
        val inserted = slot<Note>()
        coEvery { noteRepository.insertNote(capture(inserted)) } returns
            RepositoryResult.Success(Note(id = 1L))

        val result = localUseCase.import(Uri.fromFile(dtkFile))

        assertTrue("导入应成功", result.isSuccess)
        val imageBlock = inserted.captured.contentBlocks.single { it.type == BlockType.IMAGE }
        assertEquals(
            "图片块必须换成「新 mediaId|新本机 uri」",
            "99|$newUri",
            imageBlock.content
        )
        assertEquals(1, result.getOrThrow().mediaCount)
    }

    /**
     * 标签必须**按名字认领**：备份里的 `tag.id` 是源设备的主键，直接拿来插
     * `note_tags` 会挂到本机同 id 的无关标签，或留下一行指向不存在标签的悬空记录。
     */
    @Test
    fun import_resolvesTagsByNameInsteadOfBackupId() = runTest {
        val dtkFile = buildDtk(
            notes = listOf(
                Note(title = "带标签", tags = listOf(Tag(id = 42L, name = "线性代数", color = 1)))
            ),
            mediaUris = emptyList()
        )
        coEvery { tagRepository.getTagByName("线性代数") } returns
            RepositoryResult.Success(Tag(id = 5L, name = "线性代数", color = 1))
        val inserted = slot<Note>()
        coEvery { noteRepository.insertNote(capture(inserted)) } returns
            RepositoryResult.Success(Note(id = 1L))

        val result = useCase.import(Uri.fromFile(dtkFile))

        assertTrue(result.isSuccess)
        assertEquals(
            "应挂到本机已有的同名标签（id=5），而不是备份里的 42",
            listOf(5L),
            inserted.captured.tags.map { it.id }
        )
    }

    @Test
    fun import_rejectsNewerDtkVersion() = runTest {
        val exportDir = newExportDir("version")
        File(exportDir, "note.json").writeText(
            JsonExporter.exportNotes(
                notes = listOf(Note(title = "未来版本")),
                dtkVersion = DtkExporter.DTK_VERSION + 1
            )
        )
        val dtkFile = File(context.cacheDir, "future.dtk")
        ArchiveManager.zip(exportDir, dtkFile).getOrThrow()
        exportDir.deleteRecursively()

        val result = useCase.import(Uri.fromFile(dtkFile))

        assertTrue("高于支持版本的包必须拒绝，而不是部分导入", result.isFailure)
    }

    /** 造一个结构正确的 `.dtk`：`note.json` + `media/<文件名>` 实体文件。 */
    private fun buildDtk(notes: List<Note>, mediaUris: List<Pair<Long, String>>): File {
        val exportDir = newExportDir("build")
        val media = mediaUris.map { (id, uri) -> Media(id = id, uri = uri, size = 9L) }
        File(exportDir, "note.json").writeText(
            JsonExporter.exportNotes(notes, media, dtkVersion = DtkExporter.DTK_VERSION)
        )
        mediaUris.forEach { (_, uri) ->
            val file = File(exportDir, uri)
            file.parentFile?.mkdirs()
            file.writeText("img-bytes")
        }
        val dtkFile = File(context.cacheDir, "import_test.dtk")
        ArchiveManager.zip(exportDir, dtkFile).getOrThrow()
        exportDir.deleteRecursively()
        return dtkFile
    }

    private fun newExportDir(tag: String): File =
        File(context.cacheDir, "dtk_import_${tag}_${System.nanoTime()}").apply {
            deleteRecursively()
            mkdirs()
        }
}
