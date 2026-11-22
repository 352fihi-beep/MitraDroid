package com.mitra.android.database

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.RoomDatabase
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "contact_memory")
data class ContactMemory(
    @PrimaryKey val contactId: String,
    val displayName: String = contactId,
    val memorySummary: String,
    val compressedTokenCount: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "sms_message")
data class SmsMessage(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val contactId: String,
    val isFromAi: Boolean,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isPhishingFlagged: Boolean = false,
    val deliveryStatus: String = "RECEIVED"
)

@Entity(tableName = "call_record")
data class CallRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val contactId: String,
    val direction: String, // INCOMING, OUTGOING
    val durationSeconds: Long = 0L,
    val transcript: String = "",
    val dtmfHistory: String = "",
    val inferenceMode: String = "CLOUD", // CLOUD, MEDIAPIPE, RULE_FALLBACK
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface MemoryDao {
    @Query("SELECT memorySummary FROM contact_memory WHERE contactId = :contactId")
    suspend fun getMemoryForContact(contactId: String): String?

    @Query("SELECT * FROM contact_memory WHERE contactId = :contactId")
    suspend fun getContactMemoryEntity(contactId: String): ContactMemory?

    @Query("SELECT * FROM contact_memory ORDER BY updatedAt DESC")
    fun getAllMemories(): Flow<List<ContactMemory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: ContactMemory)

    @Query("DELETE FROM contact_memory WHERE contactId = :contactId")
    suspend fun deleteMemory(contactId: String)

    @Query("DELETE FROM contact_memory")
    suspend fun clearAllMemories()
}

@Dao
interface SmsDao {
    @Query("SELECT * FROM sms_message WHERE contactId = :contactId ORDER BY timestamp ASC")
    fun getSmsThread(contactId: String): Flow<List<SmsMessage>>

    @Query("SELECT * FROM sms_message ORDER BY timestamp DESC")
    fun getAllMessages(): Flow<List<SmsMessage>>

    @Query("SELECT * FROM sms_message ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessagesSnapshot(limit: Int): List<SmsMessage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSms(sms: SmsMessage): Long

    @Query("DELETE FROM sms_message WHERE timestamp < :cutoffTimestamp")
    suspend fun deleteOldSmsBefore(cutoffTimestamp: Long): Int

    @Query("DELETE FROM sms_message")
    suspend fun clearAllSms()
}

@Dao
interface CallRecordDao {
    @Query("SELECT * FROM call_record ORDER BY timestamp DESC")
    fun getAllCalls(): Flow<List<CallRecord>>

    @Query("SELECT * FROM call_record WHERE contactId = :contactId ORDER BY timestamp DESC")
    fun getCallsForContact(contactId: String): Flow<List<CallRecord>>

    @Query("SELECT * FROM call_record ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentCallsSnapshot(limit: Int): List<CallRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCall(record: CallRecord): Long

    @Query("DELETE FROM call_record WHERE id = :id")
    suspend fun deleteCallById(id: Int)

    @Query("DELETE FROM call_record")
    suspend fun clearAllCalls()
}

@Database(
    entities = [ContactMemory::class, SmsMessage::class, CallRecord::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): MemoryDao
    abstract fun smsDao(): SmsDao
    abstract fun callRecordDao(): CallRecordDao
}
