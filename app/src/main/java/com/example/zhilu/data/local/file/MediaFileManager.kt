package com.example.zhilu.data.local.file

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.MimeTypeMap
import com.example.zhilu.domain.model.ImageBlockContent
import com.example.zhilu.domain.model.Media
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID

class MediaFileManager(private val context: Context) {

    private val imagesDir: File
        get() = File(context.filesDir, "images").apply { mkdirs() }

    fun importUriToInternal(uri: Uri): Result<String> = runCatching {
        val extension = uri.path?.substringAfterLast('.', "png")?.takeIf { it.isNotBlank() } ?: "png"
        val destFile = File(imagesDir, "img_${System.currentTimeMillis()}_${UUID.randomUUID()}.${extension}")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalArgumentException("无法打开输入流")
        Uri.fromFile(destFile).toString()
    }

    fun copyToCache(media: Media, cacheDir: File): Result<File> = runCatching {
        val sourceUri = Uri.parse(ImageBlockContent.displayUri(media.uri))
        val sourceFile = when (sourceUri.scheme) {
            "file" -> File(sourceUri.path ?: throw IllegalArgumentException("无效文件路径"))
            "content" -> {
                val extension = MimeTypeMap.getFileExtensionFromUrl(media.uri).takeIf { it.isNotBlank() } ?: "png"
                val destFile = File(cacheDir, "media_${media.id}.${extension}")
                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                } ?: throw IllegalArgumentException("无法读取媒体 URI")
                destFile
            }
            else -> throw IllegalArgumentException("不支持的 URI scheme: ${sourceUri.scheme}")
        }
        if (sourceFile.parentFile?.absolutePath == cacheDir.absolutePath) {
            sourceFile
        } else {
            val extension = sourceFile.extension.takeIf { it.isNotBlank() } ?: "png"
            val destFile = File(cacheDir, "media_${media.id}.${extension}")
            sourceFile.inputStream().use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            destFile
        }
    }

    fun toBase64(file: File): Result<String> = runCatching {
        val bytes = FileInputStream(file).use { it.readBytes() }
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension) ?: "image/png"
        "data:$mime;base64,${android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)}"
    }

    fun bitmapToBase64Png(bitmap: Bitmap): String {
        val output = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        val bytes = output.toByteArray()
        return "data:image/png;base64,${android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)}"
    }
}
