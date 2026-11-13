package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scheduled_calls")
data class ScheduledCallEntity(
    @PrimaryKey
    val id: String,
    val recipientName: String,
    val phoneNumber: String,
    val scheduledTimestamp: Long,
    val agenda: String,
    val isCompleted: Boolean = false,
    val isCancelled: Boolean = false,
    val reminderMinutesBefore: Int = 10,
    val createdAt: Long = System.currentTimeMillis()
)
