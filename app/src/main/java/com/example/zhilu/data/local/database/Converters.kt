package com.example.zhilu.data.local.database

import androidx.room.TypeConverter
import com.example.zhilu.domain.model.BlockType

class Converters {
    @TypeConverter
    fun fromBlockType(blockType: BlockType): Int = blockType.value

    @TypeConverter
    fun toBlockType(value: Int): BlockType = BlockType.fromValue(value)
}
