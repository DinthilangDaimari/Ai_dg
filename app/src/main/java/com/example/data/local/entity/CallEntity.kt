package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class CallStatus {
    SCREENED,
    ANSWERED,
    MISSED,
    SPAM_BLOCKED,
    OUTGOING_AI,
    OUTGOING_HUMAN,
    OUTGOING_FAILED,
    DEMO_SIMULATOR
}

@Entity(tableName = "calls")
data class CallEntity(
    @PrimaryKey
    val id: String,
    val callerName: String,
    val phoneNumber: String,
    val timestamp: Long,
    val durationSeconds: Int,
    val status: String = CallStatus.SCREENED.name,
    val isSpam: Boolean = false,
    val spamScore: Int = 0,
    val category: String = "General",
    val urgency: String = "Medium",
    val summary: String = "",
    val reasonForCall: String = "",
    val keyDetailsJson: String = "[]",
    val actionsJson: String = "[]",
    val isContact: Boolean = false,
    val isDemo: Boolean = false
)
