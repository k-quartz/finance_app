package com.financialapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import androidx.room.Transaction
import com.financialapp.data.local.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Upsert
    suspend fun upsert(category: CategoryEntity): Long

    @Insert(onConflict = androidx.room.OnConflictStrategy.ABORT)
    suspend fun insert(category: CategoryEntity): Long

    @Query("SELECT EXISTS(SELECT 1 FROM categories WHERE ((parent_category_id IS NULL AND :parentCategoryId IS NULL) OR parent_category_id = :parentCategoryId) AND LOWER(name) = LOWER(:name) AND category_type = :categoryType AND category_id != :excludedCategoryId)")
    suspend fun hasDuplicate(parentCategoryId: Long?, name: String, categoryType: String, excludedCategoryId: Long): Boolean

    @Transaction
    suspend fun insertIfUnique(category: CategoryEntity): Long {
        require(!hasDuplicate(category.parentCategoryId, category.name, category.categoryType.name, 0L)) {
            "A category with the same name and type already exists at this level"
        }
        return insert(category)
    }

    @Update
    suspend fun update(category: CategoryEntity): Int

    @Upsert
    suspend fun upsertAll(categories: List<CategoryEntity>)

    @Query("SELECT * FROM categories WHERE is_deleted = 0 ORDER BY category_type, sort_order, name ASC, category_id ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY category_type, parent_category_id, sort_order, name ASC, category_id ASC")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE category_id = :categoryId")
    suspend fun getById(categoryId: Long): CategoryEntity?

    @Query("SELECT * FROM categories WHERE parent_category_id = :parentCategoryId AND is_deleted = 0 ORDER BY sort_order, name ASC, category_id ASC")
    suspend fun getChildren(parentCategoryId: Long): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE parent_category_id IS NULL AND is_deleted = 0 ORDER BY category_type, sort_order, name ASC, category_id ASC")
    suspend fun getRoots(): List<CategoryEntity>

    @Query("SELECT COUNT(*) FROM categories WHERE parent_category_id = :parentCategoryId")
    suspend fun countChildren(parentCategoryId: Long): Int

    @Query("UPDATE categories SET is_active = :isActive, updated_at_epoch_ms = :updatedAtEpochMs WHERE category_id = :categoryId AND is_deleted = 0")
    suspend fun setActive(categoryId: Long, isActive: Boolean, updatedAtEpochMs: Long): Int

    @Query("UPDATE categories SET sort_order = :sortOrder, updated_at_epoch_ms = :updatedAtEpochMs WHERE category_id = :categoryId AND is_deleted = 0")
    suspend fun setSortOrder(categoryId: Long, sortOrder: Int, updatedAtEpochMs: Long): Int

    @Transaction
    suspend fun setSortOrders(categoryIds: List<Long>, updatedAtEpochMs: Long) {
        categoryIds.forEachIndexed { index, categoryId ->
            require(setSortOrder(categoryId, index, updatedAtEpochMs) == 1) { "Category reorder failed" }
        }
    }

    @Query("UPDATE categories SET is_active = 0, is_deleted = 1, deleted_at_epoch_ms = :deletedAtEpochMs, updated_at_epoch_ms = :updatedAtEpochMs WHERE category_id = :categoryId AND is_deleted = 0")
    suspend fun softDelete(categoryId: Long, deletedAtEpochMs: Long, updatedAtEpochMs: Long): Int
}
