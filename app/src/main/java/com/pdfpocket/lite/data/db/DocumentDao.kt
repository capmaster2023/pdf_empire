package com.pdfpocket.lite.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {

    @Query("SELECT * FROM documents ORDER BY lastOpenedAt DESC LIMIT 10")
    fun recents(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE isFavorite = 1 ORDER BY name COLLATE NOCASE")
    fun favorites(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents")
    fun all(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE uri = :uri")
    suspend fun byUri(uri: String): DocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(document: DocumentEntity)

    @Query("UPDATE documents SET lastPage = :page WHERE uri = :uri")
    suspend fun updateLastPage(uri: String, page: Int)

    @Query("UPDATE documents SET pageCount = :count WHERE uri = :uri")
    suspend fun updatePageCount(uri: String, count: Int)

    @Query("UPDATE documents SET isFavorite = :favorite WHERE uri = :uri")
    suspend fun setFavorite(uri: String, favorite: Boolean)

    @Query("DELETE FROM documents WHERE uri = :uri")
    suspend fun delete(uri: String)

    @Query("DELETE FROM documents")
    suspend fun clear()
}
