package com.financialapp.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.financialapp.data.local.entity.ProductEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Upsert
    suspend fun upsert(product: ProductEntity): Long

    @Query("SELECT * FROM products ORDER BY product_id ASC")
    suspend fun getAll(): List<ProductEntity>

    @Query("SELECT * FROM products WHERE is_deleted = 0 ORDER BY name ASC")
    fun observeAll(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE is_deleted = 0 AND is_active = 1 ORDER BY name ASC")
    suspend fun getActiveProducts(): List<ProductEntity>

    @Query("SELECT * FROM products WHERE product_id = :productId")
    suspend fun getById(productId: Long): ProductEntity?

    @Query("SELECT * FROM products WHERE category_id = :categoryId AND is_deleted = 0 ORDER BY name ASC")
    suspend fun getProductsByCategory(categoryId: Long): List<ProductEntity>

    @Query("UPDATE products SET is_deleted = 1, deleted_at_epoch_ms = :deletedAtEpochMs, updated_at_epoch_ms = :updatedAtEpochMs WHERE product_id = :productId")
    suspend fun softDelete(productId: Long, deletedAtEpochMs: Long, updatedAtEpochMs: Long): Int
}
