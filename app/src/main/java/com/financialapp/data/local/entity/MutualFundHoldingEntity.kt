package com.financialapp.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "mutual_fund_holdings",
    foreignKeys = [
        ForeignKey(entity = MutualFundEntity::class, parentColumns = ["fund_id"], childColumns = ["fund_id"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = AccountEntity::class, parentColumns = ["account_id"], childColumns = ["investment_account_id"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index(value = ["fund_id", "investment_account_id"], unique = true), Index(value = ["investment_account_id"])],
)
data class MutualFundHoldingEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "holding_id") val holdingId: Long = 0L,
    @ColumnInfo(name = "fund_id") val fundId: Long,
    @ColumnInfo(name = "investment_account_id") val investmentAccountId: Long,
    @ColumnInfo(name = "units_minor") val unitsMinor: Long,
    @ColumnInfo(name = "units_scale") val unitsScale: Int,
    @ColumnInfo(name = "updated_at_epoch_ms") val updatedAtEpochMs: Long,
)