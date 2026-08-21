package com.financialapp.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "purchases",
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["product_id"],
            childColumns = ["product_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["transaction_id"],
            childColumns = ["transaction_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["category_id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.RESTRICT,
        )
    ],
    indices = [
        Index(value = ["product_id", "purchase_date_epoch_day"]),
        Index(value = ["transaction_id"]),
        Index(value = ["category_id", "purchase_date_epoch_day"]),
        Index(value = ["merchant_name"]),
    ],
)
data class PurchaseEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "purchase_id")
    val purchaseId: Long = 0L,
    @ColumnInfo(name = "product_id")
    val productId: Long,
    @ColumnInfo(name = "transaction_id")
    val transactionId: Long? = null,
    @ColumnInfo(name = "category_id")
    val categoryId: Long? = null,
    @ColumnInfo(name = "merchant_name")
    val merchantName: String? = null,
    @ColumnInfo(name = "quantity_minor")
    val quantityMinor: Long,
    @ColumnInfo(name = "quantity_scale")
    val quantityScale: Int,
    @ColumnInfo(name = "unit")
    val unit: String,
    @ColumnInfo(name = "unit_price_minor")
    val unitPriceMinor: Long,
    @ColumnInfo(name = "total_amount_minor")
    val totalAmountMinor: Long,
    @ColumnInfo(name = "currency_code")
    val currencyCode: String,
    @ColumnInfo(name = "purchase_date_epoch_day")
    val purchaseDateEpochDay: Long,
    @ColumnInfo(name = "notes")
    val notes: String? = null,
    @ColumnInfo(name = "is_deleted")
    val isDeleted: Boolean = false,
    @ColumnInfo(name = "deleted_at_epoch_ms")
    val deletedAtEpochMs: Long? = null,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long,
    @ColumnInfo(name = "updated_at_epoch_ms")
    val updatedAtEpochMs: Long,
)
