package com.financialapp.di

import com.financialapp.data.local.database.AppDatabase
import com.financialapp.data.repository.AccountRepository
import com.financialapp.data.repository.BackupRepository
import com.financialapp.data.repository.CategoryRepository
import com.financialapp.data.repository.DashboardRepository
import com.financialapp.data.repository.LocalAccountRepository
import com.financialapp.data.repository.LocalBackupRepository
import com.financialapp.data.repository.LocalDashboardRepository
import com.financialapp.data.repository.LocalCategoryRepository
import com.financialapp.data.repository.LocalProductPriceHistoryRepository
import com.financialapp.data.repository.LocalProductRepository
import com.financialapp.data.repository.LocalPurchaseRepository
import com.financialapp.data.repository.FinancialRepository
import com.financialapp.data.repository.LocalFinancialRepository
import com.financialapp.data.repository.ProductPriceHistoryRepository
import com.financialapp.data.repository.ProductRepository
import com.financialapp.data.repository.PurchaseRepository
import com.financialapp.data.repository.LocalMutualFundRepository
import com.financialapp.data.repository.MutualFundRepository
import java.time.Clock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideAccountRepository(database: AppDatabase): AccountRepository =
        LocalAccountRepository(database.accountDao())

    @Provides
    @Singleton
    fun provideCategoryRepository(database: AppDatabase): CategoryRepository =
        LocalCategoryRepository(database)

    @Provides
    @Singleton
    fun provideFinancialRepository(database: AppDatabase): FinancialRepository =
        LocalFinancialRepository(database.accountDao(), database.categoryDao(), database.transactionDao())

    @Provides
    @Singleton
    fun provideProductRepository(database: AppDatabase): ProductRepository =
        LocalProductRepository(database.productDao(), database.categoryDao())

    @Provides
    @Singleton
    fun providePurchaseRepository(database: AppDatabase, financialRepository: FinancialRepository): PurchaseRepository =
        LocalPurchaseRepository(database, financialRepository)

    @Provides
    @Singleton
    fun provideProductPriceHistoryRepository(database: AppDatabase): ProductPriceHistoryRepository =
        LocalProductPriceHistoryRepository(database.productDao(), database.productPriceHistoryDao())

    @Provides
    @Singleton
    fun provideMutualFundRepository(database: AppDatabase): MutualFundRepository =
        LocalMutualFundRepository(database, database.mutualFundDao(), database.accountDao(), database.transactionDao())

    @Provides
    @Singleton
    fun provideDashboardRepository(
        financialRepository: FinancialRepository,
        categoryRepository: CategoryRepository,
        mutualFundRepository: MutualFundRepository,
        productRepository: ProductRepository,
        productPriceHistoryRepository: ProductPriceHistoryRepository,
    ): DashboardRepository =
        LocalDashboardRepository(
            financialRepository = financialRepository,
            categoryRepository = categoryRepository,
            mutualFundRepository = mutualFundRepository,
            productRepository = productRepository,
            productPriceHistoryRepository = productPriceHistoryRepository,
        )

    @Provides
    @Singleton
    fun provideBackupRepository(database: AppDatabase, clock: Clock): BackupRepository =
        LocalBackupRepository(
            database = database,
            accountDao = database.accountDao(),
            categoryDao = database.categoryDao(),
            transactionDao = database.transactionDao(),
            productDao = database.productDao(),
            productPriceHistoryDao = database.productPriceHistoryDao(),
            purchaseDao = database.purchaseDao(),
            mutualFundDao = database.mutualFundDao(),
            clock = clock,
        )
}
