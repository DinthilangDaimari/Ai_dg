package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.CallEntity
import com.example.data.local.entity.TranscriptLineEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CallDao {

    @Query("SELECT * FROM calls ORDER BY timestamp DESC")
    fun getAllCalls(): Flow<List<CallEntity>>

    @Query("SELECT * FROM calls WHERE id = :id LIMIT 1")
    fun getCallById(id: String): Flow<CallEntity?>

    @Query("SELECT * FROM calls WHERE id = :id LIMIT 1")
    suspend fun getCallByIdOnce(id: String): CallEntity?

    @Query("SELECT * FROM transcript_lines WHERE callId = :callId ORDER BY timestamp ASC, id ASC")
    fun getTranscriptForCall(callId: String): Flow<List<TranscriptLineEntity>>

    @Query("SELECT * FROM transcript_lines WHERE callId = :callId ORDER BY timestamp ASC, id ASC")
    suspend fun getTranscriptForCallOnce(callId: String): List<TranscriptLineEntity>

    @Query("""
        SELECT * FROM calls 
        WHERE callerName LIKE '%' || :query || '%' 
           OR phoneNumber LIKE '%' || :query || '%' 
           OR summary LIKE '%' || :query || '%' 
           OR category LIKE '%' || :query || '%'
        ORDER BY timestamp DESC
    """)
    fun searchCalls(query: String): Flow<List<CallEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCall(call: CallEntity)

    @Update
    suspend fun updateCall(call: CallEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTranscriptLine(line: TranscriptLineEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTranscriptLines(lines: List<TranscriptLineEntity>)

    @Query("DELETE FROM calls WHERE id = :id")
    suspend fun deleteCall(id: String)

    @Query("DELETE FROM transcript_lines WHERE callId = :callId")
    suspend fun deleteTranscriptForCall(callId: String)

    @Query("DELETE FROM calls")
    suspend fun clearAllCalls()

    @Query("DELETE FROM transcript_lines")
    suspend fun clearAllTranscripts()

    @Query("SELECT COUNT(*) FROM calls WHERE status = 'SCREENED' AND isDemo = 0")
    fun getScreenedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM calls WHERE (isSpam = 1 OR status = 'SPAM_BLOCKED') AND isDemo = 0 AND status != 'DEMO_SIMULATOR'")
    fun getSpamBlockedCount(): Flow<Int>

    // Scheduled Calls Queries
    @Query("SELECT * FROM scheduled_calls ORDER BY scheduledTimestamp ASC")
    fun getAllScheduledCalls(): Flow<List<com.example.data.local.entity.ScheduledCallEntity>>

    @Query("SELECT * FROM scheduled_calls WHERE isCompleted = 0 AND isCancelled = 0 ORDER BY scheduledTimestamp ASC")
    fun getUpcomingScheduledCalls(): Flow<List<com.example.data.local.entity.ScheduledCallEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScheduledCall(call: com.example.data.local.entity.ScheduledCallEntity)

    @Update
    suspend fun updateScheduledCall(call: com.example.data.local.entity.ScheduledCallEntity)

    @Query("DELETE FROM scheduled_calls WHERE id = :id")
    suspend fun deleteScheduledCall(id: String)

    // Voice Notes Queries
    @Query("SELECT * FROM voice_notes ORDER BY timestamp DESC")
    fun getAllVoiceNotes(): Flow<List<com.example.data.local.entity.VoiceNoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVoiceNote(note: com.example.data.local.entity.VoiceNoteEntity)

    @Query("DELETE FROM voice_notes WHERE id = :id")
    suspend fun deleteVoiceNote(id: String)

    @Query("DELETE FROM voice_notes")
    suspend fun clearAllVoiceNotes()
}
