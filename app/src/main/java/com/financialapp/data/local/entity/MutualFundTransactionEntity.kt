package com.financialapp.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "mutual_fund_transactions",
    foreignKeys = [
        ForeignKey(entity = MutualFundEntity::class, parentColumns = ["fund_id"], childColumns = ["fund_id"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = AccountEntity::class, parentColumns = ["account_id"], childColumns = ["investment_account_id"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = AccountEntity::class, parentColumns = ["account_id"], childColumns = ["cash_account_id"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = TransactionEntity::class, parentColumns = ["transaction_id"], childColumns = ["linked_cash_transaction_id"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index(value = ["fund_id", "investment_account_id", "transaction_date_epoch_day"]), Index(value = ["investment_account_id"]), Index(value = ["cash_account_id"]), Index(value = ["linked_cash_transaction_id"], unique = true)],
)
data class MutualFundTransactionEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "investment_transaction_id") val investmentTransactionId: Long = 0L,
    @ColumnInfo(name = "fund_id") val fundId: Long,
    @ColumnInfo(name = "investment_account_id") val investmentAccountId: Long,
    @ColumnInfo(name = "cash_account_id") val cashAccountId: Long? = null,
    @ColumnInfo(name = "transaction_type") val transactionType: InvestmentTransactionType,
    @ColumnInfo(name = "units_minor") val unitsMinor: Long,
    @ColumnInfo(name = "units_scale") val unitsScale: Int,
    @ColumnInfo(name = "nav_minor") val navMinor: Long,
    @ColumnInfo(name = "total_amount_minor") val totalAmountMinor: Long,
    @ColumnInfo(name = "currency_code") val currencyCode: String,
    @ColumnInfo(name = "transaction_date_epoch_day") val transactionDateEpochDay: Long,
    @ColumnInfo(name = "linked_cash_transaction_id") val linkedCashTransactionId: Long? = null,
    val notes: String? = null,
    @ColumnInfo(name = "created_at_epoch_ms") val createdAtEpochMs: Long,
)