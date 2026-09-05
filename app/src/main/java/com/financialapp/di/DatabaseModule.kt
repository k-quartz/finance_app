package com.financialapp.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.financialapp.data.local.database.AppDatabase
import com.financialapp.data.local.database.FinancialDatabaseMigrations
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "financial_app.db")
            .addMigrations(
                FinancialDatabaseMigrations.MIGRATION_1_2,
                FinancialDatabaseMigrations.MIGRATION_2_3,
                FinancialDatabaseMigrations.MIGRATION_3_4,
                FinancialDatabaseMigrations.MIGRATION_4_5,
                FinancialDatabaseMigrations.MIGRATION_5_6,
                FinancialDatabaseMigrations.MIGRATION_6_7,
                FinancialDatabaseMigrations.MIGRATION_7_8,
            )
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                    database.execSQL(
                        "CREATE UNIQUE INDEX IF NOT EXISTS index_categories_root_name_category_type ON categories (name, category_type) WHERE parent_category_id IS NULL",
                    )
                }
            })
            .build()
}
