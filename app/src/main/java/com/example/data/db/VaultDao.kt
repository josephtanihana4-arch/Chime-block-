package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {

    @Query("SELECT * FROM blocked_apps ORDER BY isBlocked DESC, displayName ASC")
    fun observeBlockedApps(): Flow<List<BlockedAppEntity>>

    @Query("SELECT packageName FROM blocked_apps WHERE isBlocked = 1")
    fun observeActiveBlockedPackageNames(): Flow<List<String>>

    @Query("SELECT * FROM blocked_apps WHERE packageName = :packageName LIMIT 1")
    suspend fun getBlockedApp(packageName: String): BlockedAppEntity?

    @Query("SELECT packageName FROM blocked_apps WHERE isBlocked = 1")
    suspend fun getActiveBlockedPackageNames(): List<String>

    @Query("SELECT COUNT(*) FROM blocked_apps")
    suspend fun countBlockedApps(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBlockedApp(app: BlockedAppEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBlockedAppsIfMissing(apps: List<BlockedAppEntity>)

    @Query("UPDATE blocked_apps SET isBlocked = :isBlocked WHERE packageName = :packageName")
    suspend fun setAppBlocked(packageName: String, isBlocked: Boolean)

    @Query("UPDATE blocked_apps SET blockAttemptsCount = blockAttemptsCount + 1, lastBlockedAt = :timestamp WHERE packageName = :packageName")
    suspend fun incrementBlockAttempts(packageName: String, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM blocked_apps WHERE packageName = :packageName AND isCustom = 1")
    suspend fun deleteCustomBlockedApp(packageName: String)

    @Query("SELECT * FROM savings_deposits ORDER BY createdAt DESC")
    fun observeDeposits(): Flow<List<SavingsDepositEntity>>

    @Query("SELECT COALESCE(SUM(amountCents), 0) FROM savings_deposits")
    fun observeTotalSavedCents(): Flow<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeposit(deposit: SavingsDepositEntity)

    @Query("DELETE FROM savings_deposits WHERE id = :id")
    suspend fun deleteDepositById(id: Long)

    @Query("SELECT * FROM security_events ORDER BY createdAt DESC LIMIT 100")
    fun observeSecurityEvents(): Flow<List<SecurityEventEntity>>

    @Query("SELECT COALESCE(SUM(blockAttemptsCount), 0) FROM blocked_apps")
    fun observeTotalBlockedAttempts(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSecurityEvent(event: SecurityEventEntity)
}
