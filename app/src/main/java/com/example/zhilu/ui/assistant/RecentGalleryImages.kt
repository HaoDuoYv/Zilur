package com.example.zhilu.ui.assistant

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 读相册所需的运行时权限。
 *
 * Android 13 起 `READ_EXTERNAL_STORAGE` 对媒体不再生效，换成细分的 `READ_MEDIA_IMAGES`。
 */
val galleryReadPermission: String
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

fun Context.canReadGallery(): Boolean =
    ContextCompat.checkSelfPermission(this, galleryReadPermission) ==
        PackageManager.PERMISSION_GRANTED

/**
 * 相册里最近的 [limit] 张图片，按加入时间倒序。
 *
 * **未授权时返回空表**——API 29 起 scoped storage 下，没有权限的查询只回本应用自己写入的图片，
 * 所以「空」在授权前后含义不同，调用方要靠 [Context.canReadGallery] 区分，不要只看空表。
 *
 * 只在 [enabled] 且相册内容变化后重新查询；读库走 IO 线程，不阻塞开弹层。
 */
@Composable
fun rememberRecentGalleryImages(enabled: Boolean, limit: Int = 8): List<Uri> {
    val context = LocalContext.current
    var images by remember { mutableStateOf(emptyList<Uri>()) }
    LaunchedEffect(enabled, limit) {
        images = if (enabled) queryRecentImages(context, limit) else emptyList()
    }
    return images
}

private suspend fun queryRecentImages(context: Context, limit: Int): List<Uri> =
    withContext(Dispatchers.IO) {
        runCatching {
            val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(MediaStore.Images.Media._ID)
            val sort = "${MediaStore.Images.Media.DATE_ADDED} DESC"

            val result = ArrayList<Uri>(limit)
            context.contentResolver.query(collection, projection, null, null, sort)?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                // 游标已按时间倒序，读到 limit 条就停，不去遍历整个媒体库。
                while (result.size < limit && cursor.moveToNext()) {
                    result += ContentUris.withAppendedId(collection, cursor.getLong(idColumn))
                }
            }
            result.toList()
        }.getOrDefault(emptyList())
    }
