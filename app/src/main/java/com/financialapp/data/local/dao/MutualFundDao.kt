package com.financialapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.financialapp.data.local.entity.MutualFundEntity
import com.financialapp.data.local.entity.MutualFundHoldingEntity
import com.financialapp.data.local.entity.MutualFundNavEntity
import com.financialapp.data.local.entity.MutualFundTransactionEntity

@Dao
interface MutualFundDao {
    @Insert(onConflict = androidx.room.OnConflictStrategy.ABORT)
    suspend fun insertFund(fund: MutualFundEntity): Long

    @Query("SELECT * FROM mutual_funds ORDER BY fund_id ASC")
    suspend fun getAllFunds(): List<MutualFundEntity>

    @Query("SELECT * FROM mutual_funds WHERE fund_id = :fundId")
    suspend fun getFund(fundId: Long): MutualFundEntity?

    @Query("SELECT * FROM mutual_funds WHERE is_deleted = 0 AND is_active = 1 ORDER BY name ASC")
    suspend fun getActiveFunds(): List<MutualFundEntity>

    @Query("SELECT * FROM mutual_fund_transactions ORDER BY transaction_date_epoch_day DESC, investment_transaction_id DESC")
    suspend fun getAllTransactionsOrdered(): List<MutualFundTransactionEntity>

    @Insert(onConflict = androidx.room.OnConflictStrategy.ABORT)
    suspend fun insertTransaction(transaction: MutualFundTransactionEntity): Long

    @Query("SELECT * FROM mutual_fund_transactions ORDER BY investment_transaction_id ASC")
    suspend fun getAllTransactions(): List<MutualFundTransactionEntity>

    @Query("SELECT * FROM mutual_fund_transactions WHERE fund_id = :fundId AND investment_account_id = :accountId ORDER BY transaction_date_epoch_day ASC, investment_transaction_id ASC")
    suspend fun getTransactions(fundId: Long, accountId: Long): List<MutualFundTransactionEntity>

    @Query("SELECT * FROM mutual_fund_holdings WHERE fund_id = :fundId AND investment_account_id = :accountId LIMIT 1")
    suspend fun getHolding(fundId: Long, accountId: Long): MutualFundHoldingEntity?

    @Query("SELECT * FROM mutual_fund_holdings ORDER BY fund_id ASC, investment_account_id ASC")
    suspend fun getHoldings(): List<MutualFundHoldingEntity>

    @Insert(onConflict = androidx.room.OnConflictStrategy.ABORT)
    suspend fun insertHolding(holding: MutualFundHoldingEntity): Long

    @Query("SELECT * FROM mutual_fund_holdings ORDER BY holding_id ASC")
    suspend fun getAllHoldingsList(): List<MutualFundHoldingEntity>

    @Update
    suspend fun updateHolding(holding: MutualFundHoldingEntity): Int

    @Insert(onConflict = androidx.room.OnConflictStrategy.ABORT)
    suspend fun insertNav(nav: MutualFundNavEntity): Long

    @Query("SELECT * FROM mutual_fund_nav_history ORDER BY nav_id ASC")
    suspend fun getAllNavHistory(): List<MutualFundNavEntity>

    @Query("SELECT * FROM mutual_fund_nav_history ORDER BY as_of_date_epoch_day DESC, nav_id DESC")
    suspend fun getNavHistoryOrdered(): List<MutualFundNavEntity>

    @Query("SELECT * FROM mutual_fund_nav_history WHERE fund_id = :fundId AND as_of_date_epoch_day <= :dateEpochDay ORDER BY as_of_date_epoch_day DESC LIMIT 1")
    suspend fun getLatestNav(fundId: Long, dateEpochDay: Long): MutualFundNavEntity?

    @Query("SELECT * FROM mutual_fund_nav_history WHERE as_of_date_epoch_day <= :dateEpochDay ORDER BY as_of_date_epoch_day DESC, fund_id ASC")
    suspend fun getLatestNavsOnOrBefore(dateEpochDay: Long): List<MutualFundNavEntity>
}