package com.financialapp.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "categories",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["category_id"],
            childColumns = ["parent_category_id"],
            onDelete = ForeignKey.NO_ACTION,
        )
    ],
    indices = [
        Index(value = ["parent_category_id", "name", "category_type"], unique = true),
        Index(value = ["is_active", "is_deleted"]),
        Index(value = ["parent_category_id", "sort_order"]),
    ],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "category_id")
    val categoryId: Long = 0L,
    @ColumnInfo(name = "parent_category_id")
    val parentCategoryId: Long? = null,
    @ColumnInfo(name = "category_type")
    val categoryType: CategoryType,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0,
    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,
    @ColumnInfo(name = "is_deleted")
    val isDeleted: Boolean = false,
    @ColumnInfo(name = "deleted_at_epoch_ms")
    val deletedAtEpochMs: Long? = null,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long,
    @ColumnInfo(name = "updated_at_epoch_ms")
    val updatedAtEpochMs: Long,
)
