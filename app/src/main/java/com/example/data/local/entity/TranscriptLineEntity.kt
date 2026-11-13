package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class TranscriptSpeaker {
    CALLER,
    AI,
    USER
}

@Entity(
    tableName = "transcript_lines",
    indices = [Index(value = ["callId"])]
)
data class TranscriptLineEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val callId: String,
    val speaker: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)
