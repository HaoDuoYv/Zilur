package com.example.zhilu.domain.usecase

import android.content.Context
import android.net.Uri
import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.data.local.file.ArchiveManager
import com.example.zhilu.data.local.file.MediaFileManager
import com.example.zhilu.domain.model.Note
import com.example.zhilu.domain.repository.MediaRepository
import com.example.zhilu.domain.repository.NoteRepository
import com.example.zhilu.export.JsonExporter
import io.mockk.coEvery
import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ImportKnowledgeUseCaseTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val noteRepository: NoteRepository = mockk(relaxed = true)
    private val mediaRepository: MediaRepository = mockk(relaxed = true)
    private val mediaFileManager = MediaFileManager(context)
    private val useCase = ImportKnowledgeUseCase(context, noteRepository, mediaRepository, mediaFileManager)

    @Test
    fun importEmptyNote_succeeds() = runTest {
        val note = Note(title = "导入测试")
        val exportDir = File(context.cacheDir, "dtk_test_empty").apply {
            deleteRecursively()
            mkdirs()
        }
        File(exportDir, "note.json").writeText(JsonExporter.exportNote(note))
        val dtkFile = File(context.cacheDir, "test_empty.dtk")
        ArchiveManager.zip(exportDir, dtkFile).getOrThrow()
        exportDir.deleteRecursively()

        coEvery { noteRepository.insertNote(any()) } returns RepositoryResult.Success(Note(id = 1L))

        val result = useCase.import(Uri.fromFile(dtkFile))
        assertTrue("空笔记导入应成功", result.isSuccess)
    }
}
