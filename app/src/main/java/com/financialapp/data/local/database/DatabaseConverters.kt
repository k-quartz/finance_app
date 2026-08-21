package com.financialapp.data.local.database

import androidx.room.TypeConverter
import com.financialapp.data.local.entity.AccountType
import com.financialapp.data.local.entity.CategoryType
import com.financialapp.data.local.entity.TransactionType
import com.financialapp.data.local.entity.InvestmentTransactionType

class DatabaseConverters {
    @TypeConverter
    fun accountTypeToString(value: AccountType?): String? = value?.name

    @TypeConverter
    fun stringToAccountType(value: String?): AccountType? = value?.let(AccountType::valueOf)

    @TypeConverter
    fun categoryTypeToString(value: CategoryType?): String? = value?.name

    @TypeConverter
    fun stringToCategoryType(value: String?): CategoryType? = value?.let(CategoryType::valueOf)

    @TypeConverter
    fun transactionTypeToString(value: TransactionType?): String? = value?.name

    @TypeConverter
    fun stringToTransactionType(value: String?): TransactionType? = value?.let(TransactionType::valueOf)

    @TypeConverter
    fun investmentTransactionTypeToString(value: InvestmentTransactionType?): String? = value?.name

    @TypeConverter
    fun stringToInvestmentTransactionType(value: String?): InvestmentTransactionType? = value?.let(InvestmentTransactionType::valueOf)
}
