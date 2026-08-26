package com.oxygen.ai.data.db

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun csvFromList(value: List<String>?): String = value?.joinToString("\u001f") ?: ""

    @TypeConverter
    fun csvToList(value: String?): List<String> =
        value?.split("\u001f")?.filter { it.isNotBlank() } ?: emptyList()
}
