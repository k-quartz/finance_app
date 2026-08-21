package com.financialapp.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "mutual_fund_nav_history",
    foreignKeys = [ForeignKey(entity = MutualFundEntity::class, parentColumns = ["fund_id"], childColumns = ["fund_id"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["fund_id", "as_of_date_epoch_day"], unique = true)],
)
data class MutualFundNavEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "nav_id") val navId: Long = 0L,
    @ColumnInfo(name = "fund_id") val fundId: Long,
    @ColumnInfo(name = "as_of_date_epoch_day") val asOfDateEpochDay: Long,
    @ColumnInfo(name = "nav_minor") val navMinor: Long,
    @ColumnInfo(name = "currency_code") val currencyCode: String,
    val source: String? = null,
    val notes: String? = null,
    @ColumnInfo(name = "created_at_epoch_ms") val createdAtEpochMs: Long,
)