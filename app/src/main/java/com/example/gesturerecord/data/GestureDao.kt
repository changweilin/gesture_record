package com.example.gesturerecord.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface GestureDao {

    @Query("SELECT * FROM gesture_combinations ORDER BY orderIndex ASC")
    fun getAllCombinations(): Flow<List<GestureCombination>>

    @Insert
    suspend fun insertCombination(combination: GestureCombination): Long

    @Update
    suspend fun updateCombination(combination: GestureCombination)

    @Delete
    suspend fun deleteCombination(combination: GestureCombination)

    @Query("SELECT * FROM gesture_items WHERE combinationId = :combinationId")
    suspend fun getItemsForCombination(combinationId: Long): List<GestureItem>

    @Query("SELECT * FROM gesture_items WHERE combinationId = :combinationId AND slotIndex = :slotIndex LIMIT 1")
    suspend fun getItemForSlot(combinationId: Long, slotIndex: Int): GestureItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: GestureItem): Long

    @Delete
    suspend fun deleteItem(item: GestureItem)
}
