package com.financialapp.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["account_id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["account_id"],
            childColumns = ["counterparty_account_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["category_id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.RESTRICT,
        )
    ],
    indices = [
        Index(value = ["account_id", "occurred_date_epoch_day"]),
        Index(value = ["account_id", "is_deleted", "occurred_date_epoch_day"]),
        Index(value = ["category_id", "occurred_date_epoch_day"]),
        Index(value = ["is_deleted", "occurred_date_epoch_day"]),
        Index(value = ["account_id", "transaction_type"]),
        Index(value = ["counterparty_account_id"]),
        Index(value = ["transfer_id"], unique = true),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "transaction_id")
    val transactionId: Long = 0L,
    @ColumnInfo(name = "account_id")
    val accountId: Long,
    @ColumnInfo(name = "counterparty_account_id")
    val counterpartyAccountId: Long? = null,
    @ColumnInfo(name = "transfer_id")
    val transferId: String? = null,
    @ColumnInfo(name = "category_id")
    val categoryId: Long? = null,
    @ColumnInfo(name = "transaction_type")
    val transactionType: TransactionType,
    @ColumnInfo(name = "amount_minor")
    val amountMinor: Long,
    @ColumnInfo(name = "currency_code")
    val currencyCode: String,
    @ColumnInfo(name = "occurred_date_epoch_day")
    val occurredDateEpochDay: Long,
    @ColumnInfo(name = "posted_at_epoch_ms")
    val postedAtEpochMs: Long,
    @ColumnInfo(name = "notes")
    val notes: String? = null,
    @ColumnInfo(name = "merchant_name")
    val merchantName: String? = null,
    @ColumnInfo(name = "is_deleted")
    val isDeleted: Boolean = false,
    @ColumnInfo(name = "deleted_at_epoch_ms")
    val deletedAtEpochMs: Long? = null,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long,
    @ColumnInfo(name = "updated_at_epoch_ms")
    val updatedAtEpochMs: Long,
)
