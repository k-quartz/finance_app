package com.financialapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.financialapp.data.local.entity.PurchaseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PurchaseDao {
    @Insert(onConflict = androidx.room.OnConflictStrategy.ABORT)
    suspend fun insert(purchase: PurchaseEntity): Long

    @Query("SELECT * FROM purchases ORDER BY purchase_id ASC")
    suspend fun getAll(): List<PurchaseEntity>

    @Upsert
    suspend fun upsert(purchase: PurchaseEntity): Long

    @Query("SELECT * FROM purchases WHERE is_deleted = 0 AND product_id = :productId ORDER BY purchase_date_epoch_day DESC")
    suspend fun getPurchasesForProduct(productId: Long): List<PurchaseEntity>

    @Query("SELECT * FROM purchases WHERE is_deleted = 0 AND purchase_date_epoch_day BETWEEN :startDay AND :endDay ORDER BY purchase_date_epoch_day DESC")
    fun observePurchasesForDateRange(startDay: Long, endDay: Long): Flow<List<PurchaseEntity>>

    @Query("SELECT * FROM purchases WHERE is_deleted = 0 AND purchase_date_epoch_day BETWEEN :startDay AND :endDay ORDER BY purchase_date_epoch_day DESC, purchase_id DESC")
    suspend fun getPurchasesForDateRange(startDay: Long, endDay: Long): List<PurchaseEntity>

    @Query("SELECT * FROM purchases WHERE purchase_id = :purchaseId")
    suspend fun getPurchaseById(purchaseId: Long): PurchaseEntity?

    @Query("UPDATE purchases SET is_deleted = 1, deleted_at_epoch_ms = :deletedAtEpochMs, updated_at_epoch_ms = :updatedAtEpochMs WHERE purchase_id = :purchaseId")
    suspend fun softDelete(purchaseId: Long, deletedAtEpochMs: Long, updatedAtEpochMs: Long): Int
}
