package com.financialapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.OnConflictStrategy
import com.financialapp.data.local.entity.ProductPriceHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductPriceHistoryDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(priceEntry: ProductPriceHistoryEntity): Long

    @Query("SELECT * FROM product_price_history ORDER BY price_history_id ASC")
    suspend fun getAll(): List<ProductPriceHistoryEntity>

    @Query("SELECT * FROM product_price_history WHERE product_id = :productId AND is_deleted = 0 ORDER BY as_of_date_epoch_day DESC")
    suspend fun getHistoryForProduct(productId: Long): List<ProductPriceHistoryEntity>

    @Query("SELECT * FROM product_price_history WHERE product_id IN (:productIds) AND is_deleted = 0 ORDER BY product_id ASC, as_of_date_epoch_day DESC")
    suspend fun getHistoryForProducts(productIds: List<Long>): List<ProductPriceHistoryEntity>

    @Query("SELECT * FROM product_price_history WHERE product_id = :productId AND is_deleted = 0 AND as_of_date_epoch_day = :dateEpochDay LIMIT 1")
    suspend fun getPriceForDate(productId: Long, dateEpochDay: Long): ProductPriceHistoryEntity?

    @Query("SELECT * FROM product_price_history WHERE product_id = :productId AND is_deleted = 0 AND as_of_date_epoch_day <= :dateEpochDay ORDER BY as_of_date_epoch_day DESC LIMIT 1")
    suspend fun getLatestPriceBeforeOrOnDate(productId: Long, dateEpochDay: Long): ProductPriceHistoryEntity?

    @Query("SELECT * FROM product_price_history WHERE product_id = :productId AND is_deleted = 0 AND as_of_date_epoch_day < :dateEpochDay ORDER BY as_of_date_epoch_day DESC LIMIT 1")
    suspend fun getPreviousPriceBeforeDate(productId: Long, dateEpochDay: Long): ProductPriceHistoryEntity?

    @Query("SELECT * FROM product_price_history WHERE is_deleted = 0 AND as_of_date_epoch_day BETWEEN :startDay AND :endDay ORDER BY as_of_date_epoch_day DESC")
    fun observePriceHistoryRange(startDay: Long, endDay: Long): Flow<List<ProductPriceHistoryEntity>>
}
