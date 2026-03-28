package com.example.gesturerecord.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(tableName = "gesture_combinations")
data class GestureCombination(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val orderIndex: Int
)

@Entity(
    tableName = "gesture_items",
    foreignKeys = [
        ForeignKey(
            entity = GestureCombination::class,
            parentColumns = ["id"],
            childColumns = ["combinationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("combinationId"), Index(value = ["combinationId", "slotIndex"], unique = true)]
)
data class GestureItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val combinationId: Long,
    val slotIndex: Int, // 0 to 8
    
    // Type of action: 0 = Path Gesture, 1 = UI Element Click
    val actionType: Int = 0, 
    
    // Original path data for actionType == 0
    val serializedPathData: String = "", 
    
    // Node selectors for actionType == 1
    val nodeText: String? = null,
    val nodeViewId: String? = null,
    val nodeClassName: String? = null,
    
    val durationMs: Long
)
