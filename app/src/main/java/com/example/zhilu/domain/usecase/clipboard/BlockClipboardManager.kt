package com.example.zhilu.domain.usecase.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BlockClipboardManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    fun copyBlock(data: BlockClipboardData) {
        val json = BlockClipboardSerializer.toJson(data)
        val clip = ClipData.newPlainText("zhilu-block", json)
        clipboard.setPrimaryClip(clip)
    }

    fun hasBlock(): Boolean {
        return readBlock() != null
    }

    fun readBlock(): BlockClipboardData? {
        val clip = clipboard.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        val text = clip.getItemAt(0).text?.toString() ?: return null
        return BlockClipboardSerializer.fromJson(text)
    }
}
