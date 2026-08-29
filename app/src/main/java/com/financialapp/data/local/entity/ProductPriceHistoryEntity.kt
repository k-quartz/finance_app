package com.financialapp.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "product_price_history",
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["product_id"],
            childColumns = ["product_id"],
            onDelete = ForeignKey.RESTRICT,
        )
    ],
    indices = [
        Index(value = ["product_id", "as_of_date_epoch_day"], unique = true),
        Index(value = ["product_id", "is_deleted", "as_of_date_epoch_day"]),
        Index(value = ["is_deleted", "as_of_date_epoch_day"]),
    ],
)
data class ProductPriceHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "price_history_id")
    val priceHistoryId: Long = 0L,
    @ColumnInfo(name = "product_id")
    val productId: Long,
    @ColumnInfo(name = "as_of_date_epoch_day")
    val asOfDateEpochDay: Long,
    @ColumnInfo(name = "unit_price_minor")
    val unitPriceMinor: Long,
    @ColumnInfo(name = "currency_code")
    val currencyCode: String,
    @ColumnInfo(name = "source")
    val source: String? = null,
    @ColumnInfo(name = "notes")
    val notes: String? = null,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long,
    @ColumnInfo(name = "is_deleted")
    val isDeleted: Boolean = false,
    @ColumnInfo(name = "deleted_at_epoch_ms")
    val deletedAtEpochMs: Long? = null,
    @ColumnInfo(name = "updated_at_epoch_ms")
    val updatedAtEpochMs: Long = createdAtEpochMs,
)
