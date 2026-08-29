package com.financialapp.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.financialapp.data.local.dao.AccountDao
import com.financialapp.data.local.dao.CategoryDao
import com.financialapp.data.local.dao.ProductDao
import com.financialapp.data.local.dao.ProductPriceHistoryDao
import com.financialapp.data.local.dao.PurchaseDao
import com.financialapp.data.local.dao.TransactionDao
import com.financialapp.data.local.dao.MutualFundDao
import com.financialapp.data.local.entity.AccountEntity
import com.financialapp.data.local.entity.CategoryEntity
import com.financialapp.data.local.entity.ProductEntity
import com.financialapp.data.local.entity.ProductPriceHistoryEntity
import com.financialapp.data.local.entity.PurchaseEntity
import com.financialapp.data.local.entity.TransactionEntity
import com.financialapp.data.local.entity.MutualFundEntity
import com.financialapp.data.local.entity.MutualFundHoldingEntity
import com.financialapp.data.local.entity.MutualFundTransactionEntity
import com.financialapp.data.local.entity.MutualFundNavEntity

@Database(
    version = 9,
    exportSchema = true,
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        ProductEntity::class,
        PurchaseEntity::class,
        ProductPriceHistoryEntity::class,
        MutualFundEntity::class,
        MutualFundHoldingEntity::class,
        MutualFundTransactionEntity::class,
        MutualFundNavEntity::class,
    ],
)
@TypeConverters(DatabaseConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun productDao(): ProductDao
    abstract fun purchaseDao(): PurchaseDao
    abstract fun productPriceHistoryDao(): ProductPriceHistoryDao
    abstract fun mutualFundDao(): MutualFundDao
}
