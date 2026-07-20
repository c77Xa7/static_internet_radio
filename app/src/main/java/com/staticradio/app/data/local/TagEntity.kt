package com.staticradio.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class TagType { GENRE, COUNTRY, MOOD, STYLE }

@Entity(tableName = "tags", indices = [Index(value = ["name", "type"], unique = true)])
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: TagType
)
