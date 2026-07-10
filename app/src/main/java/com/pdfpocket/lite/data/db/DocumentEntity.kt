package com.pdfpocket.lite.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey val uri: String,
    val name: String,
    val sizeBytes: Long,
    val pageCount: Int,
    val lastPage: Int,
    val lastOpenedAt: Long,
    val isFavorite: Boolean
)
