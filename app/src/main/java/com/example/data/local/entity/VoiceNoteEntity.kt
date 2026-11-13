package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "voice_notes")
data class VoiceNoteEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val audioPath: String = "",
    val transcription: String = "",
    val summary: String = "",
    val durationSeconds: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val tagsJson: String = "[]"
)
