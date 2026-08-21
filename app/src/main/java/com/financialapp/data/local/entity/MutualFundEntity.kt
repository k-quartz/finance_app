package com.financialapp.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "mutual_funds", indices = [Index(value = ["code"], unique = true), Index(value = ["is_active", "is_deleted"])])
data class MutualFundEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "fund_id") val fundId: Long = 0L,
    val name: String,
    val code: String,
    @ColumnInfo(name = "currency_code") val currencyCode: String,
    @ColumnInfo(name = "is_active") val isActive: Boolean,
    @ColumnInfo(name = "is_deleted") val isDeleted: Boolean,
    @ColumnInfo(name = "created_at_epoch_ms") val createdAtEpochMs: Long,
    @ColumnInfo(name = "updated_at_epoch_ms") val updatedAtEpochMs: Long,
)