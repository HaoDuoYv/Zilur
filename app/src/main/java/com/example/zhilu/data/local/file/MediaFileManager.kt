package com.example.zhilu.data.local.file

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.webkit.MimeTypeMap
import com.example.zhilu.domain.model.ImageBlockContent
import com.example.zhilu.domain.model.Media
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID

class MediaFileManager(private val context: Context) {

    private val imagesDir: File
        get() = File(context.filesDir, "images").apply { mkdirs() }

    /**
     * 尽量把 SAF 授予的临时读取权限持久化。
     * picker 返回的 content:// 授权默认只在本次回调期间有效，异步导入时可能已失效，
     * 导致图片「选了却没反应」——因此拿到 URI 后先尝试持久化。
     */
    fun persistReadPermission(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    fun importUriToInternal(uri: Uri): Result<String> = runCatching {
        persistReadPermission(uri)
        val destFile = File(imagesDir, "img_${System.currentTimeMillis()}_${UUID.randomUUID()}.${
            resolveExtension(uri)
        }")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalArgumentException("无法打开输入流")
        Uri.fromFile(destFile).toString()
    }

    /**
     * 推断落盘扩展名：优先 URI 路径里的后缀，其次按 MIME 反推。
     * picker 返回的 content:// 常常没有后缀（形如 .../media/1000000033），
     * 早期实现一律写成 .png，会让后续按扩展名判类型的地方误判。
     */
    private fun resolveExtension(uri: Uri): String {
        val fromPath = uri.path?.substringAfterLast('.', "")
            ?.takeIf { it.isNotBlank() && it.length <= 5 && it.all { c -> c.isLetterOrDigit() } }
        if (fromPath != null) return fromPath.lowercase()
        val fromMime = context.contentResolver.getType(uri)
            ?.substringAfter('/')
            ?.substringBefore('+')
            ?.takeIf { it.isNotBlank() && it.all { c -> c.isLetterOrDigit() } }
        return fromMime?.lowercase() ?: "png"
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

    /**
     * 把图片 URI 读取为 base64 data URL，供多模态模型识图使用。
     * 超过 [maxBytes] 时降采样并按 JPEG 压缩，避免 token 超限。
     */
    fun uriToBase64DataUrl(uri: Uri, maxBytes: Int = 1_500_000): Result<String> = runCatching {
        val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalArgumentException("无法读取图片")
        val (bytes, actualMime) = if (rawBytes.size <= maxBytes) {
            rawBytes to resolveImageMime(uri)
        } else {
            compressImageBytes(rawBytes, maxBytes) to "image/jpeg"
        }
        "data:$actualMime;base64,${android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)}"
    }

    /**
     * 推断图片 MIME。file:// URI 在 ContentResolver 里查不到类型，
     * 早期实现会一律回落成 image/jpeg，若实际是 PNG/WEBP 就会声明与字节不符。
     */
    private fun resolveImageMime(uri: Uri): String {
        context.contentResolver.getType(uri)?.let { if (it.startsWith("image/")) return it }
        val extension = uri.path?.substringAfterLast('.', "")?.lowercase().orEmpty()
        return when (extension) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            "heic", "heif" -> "image/heic"
            else -> "image/jpeg"
        }
    }

    private fun compressImageBytes(bytes: ByteArray, maxBytes: Int): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sampleSize = 1
        while (bounds.outWidth / sampleSize > 1600 || bounds.outHeight / sampleSize > 1600) {
            sampleSize *= 2
        }
        val bitmap = BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sampleSize }
        ) ?: return bytes
        return try {
            var quality = 85
            var out: ByteArray
            do {
                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
                out = stream.toByteArray()
                quality -= 10
            } while (out.size > maxBytes && quality > 30)
            out
        } finally {
            bitmap.recycle()
        }
    }
}
