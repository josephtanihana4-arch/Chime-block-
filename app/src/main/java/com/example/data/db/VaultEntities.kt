package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "blocked_apps")
data class BlockedAppEntity(
    @PrimaryKey val packageName: String,
    val displayName: String,
    val category: String,
    val isBlocked: Boolean = true,
    val isCustom: Boolean = false,
    val blockAttemptsCount: Int = 0,
    val lastBlockedAt: Long? = null
)

@Entity(tableName = "savings_deposits")
data class SavingsDepositEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountCents: Long,
    val note: String,
    val monthNumber: Int,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "security_events")
data class SecurityEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventType: String,
    val title: String,
    val detail: String,
    val packageName: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
