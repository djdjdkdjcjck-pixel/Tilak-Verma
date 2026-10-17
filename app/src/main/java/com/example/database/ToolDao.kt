package com.example.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ToolDao {
    @Query("SELECT * FROM created_tools ORDER BY updatedAt DESC")
    fun getAllTools(): Flow<List<CreatedToolEntity>>

    @Query("SELECT * FROM created_tools WHERE id = :id LIMIT 1")
    suspend fun getToolById(id: String): CreatedToolEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTool(tool: CreatedToolEntity)

    @Update
    suspend fun updateTool(tool: CreatedToolEntity)

    @Query("DELETE FROM created_tools WHERE id = :id")
    suspend fun deleteTool(id: String)
}
