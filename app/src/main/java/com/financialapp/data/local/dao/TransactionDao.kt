package com.financialapp.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Insert
import androidx.room.Upsert
import androidx.room.Transaction
import com.financialapp.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

data class AccountBalanceAggregate(
    val effectMinor: Long?,
)

data class ActiveAccountBalanceAggregate(
    val accountId: Long,
    val effectMinor: Long?,
)

@Dao
interface TransactionDao {
    @Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    suspend fun insert(transaction: TransactionEntity): Long

    @Query("SELECT * FROM transactions ORDER BY transaction_id ASC")
    suspend fun getAll(): List<TransactionEntity>

    @Upsert
    suspend fun upsert(transaction: TransactionEntity): Long

    @Query("SELECT * FROM transactions WHERE transfer_id = :transferId LIMIT 1")
    suspend fun getByTransferId(transferId: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE transaction_id = :transactionId LIMIT 1")
    suspend fun getById(transactionId: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE is_deleted = 0 AND account_id = :accountId ORDER BY occurred_date_epoch_day DESC, created_at_epoch_ms DESC, transaction_id DESC")
    suspend fun getTransactionsForAccount(accountId: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE is_deleted = 0 AND category_id = :categoryId ORDER BY occurred_date_epoch_day DESC")
    suspend fun getTransactionsForCategory(categoryId: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE is_deleted = 0 AND occurred_date_epoch_day BETWEEN :startDay AND :endDay ORDER BY occurred_date_epoch_day DESC, created_at_epoch_ms DESC, transaction_id DESC")
    fun observeTransactionsForDateRange(startDay: Long, endDay: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE is_deleted = 0 AND occurred_date_epoch_day BETWEEN :startDay AND :endDay ORDER BY occurred_date_epoch_day DESC, created_at_epoch_ms DESC, transaction_id DESC")
    suspend fun getTransactionsForDateRange(startDay: Long, endDay: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE is_deleted = 0 AND account_id = :accountId AND occurred_date_epoch_day BETWEEN :startDay AND :endDay ORDER BY occurred_date_epoch_day DESC, created_at_epoch_ms DESC, transaction_id DESC")
    suspend fun getTransactionsForAccountAndDateRange(accountId: Long, startDay: Long, endDay: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE is_deleted = 0 AND account_id = :accountId AND transaction_type = :transactionType AND amount_minor = :amountMinor AND occurred_date_epoch_day = :occurredDateEpochDay AND ((category_id IS NULL AND :categoryId IS NULL) OR category_id = :categoryId) AND transaction_id != :excludedTransactionId ORDER BY created_at_epoch_ms ASC, transaction_id ASC")
    suspend fun findPotentialDuplicates(accountId: Long, transactionType: String, amountMinor: Long, occurredDateEpochDay: Long, categoryId: Long?, excludedTransactionId: Long): List<TransactionEntity>

    @Query("""
        SELECT SUM(
            CASE
                WHEN t.transaction_type = 'TRANSFER' AND t.account_id = :accountId THEN -t.amount_minor
                WHEN t.transaction_type = 'TRANSFER' AND t.counterparty_account_id = :accountId AND a.account_type = 'CREDIT_CARD' THEN -t.amount_minor
                WHEN t.transaction_type = 'TRANSFER' AND t.counterparty_account_id = :accountId THEN t.amount_minor
                WHEN t.transaction_type = 'INCOME' AND a.account_type = 'CREDIT_CARD' THEN -t.amount_minor
                WHEN t.transaction_type = 'INCOME' THEN t.amount_minor
                WHEN t.transaction_type = 'EXPENSE' AND a.account_type = 'CREDIT_CARD' THEN t.amount_minor
                WHEN t.transaction_type = 'EXPENSE' THEN -t.amount_minor
                ELSE 0
            END
        ) AS effectMinor
        FROM transactions t
        INNER JOIN accounts a ON a.account_id = :accountId
        WHERE t.is_deleted = 0 AND (t.account_id = :accountId OR t.counterparty_account_id = :accountId)
    """)
    suspend fun getAccountBalanceAggregate(accountId: Long): AccountBalanceAggregate

    @Query("""
        SELECT a.account_id AS accountId,
            SUM(
                CASE
                    WHEN t.transaction_type = 'TRANSFER' AND t.account_id = a.account_id THEN -t.amount_minor
                    WHEN t.transaction_type = 'TRANSFER' AND t.counterparty_account_id = a.account_id AND a.account_type = 'CREDIT_CARD' THEN -t.amount_minor
                    WHEN t.transaction_type = 'TRANSFER' AND t.counterparty_account_id = a.account_id THEN t.amount_minor
                    WHEN t.transaction_type = 'INCOME' AND a.account_type = 'CREDIT_CARD' THEN -t.amount_minor
                    WHEN t.transaction_type = 'INCOME' THEN t.amount_minor
                    WHEN t.transaction_type = 'EXPENSE' AND a.account_type = 'CREDIT_CARD' THEN t.amount_minor
                    WHEN t.transaction_type = 'EXPENSE' THEN -t.amount_minor
                    ELSE 0
                END
            ) AS effectMinor
        FROM accounts a
        LEFT JOIN transactions t
            ON t.is_deleted = 0 AND (t.account_id = a.account_id OR t.counterparty_account_id = a.account_id)
        WHERE a.is_deleted = 0 AND a.is_active = 1
        GROUP BY a.account_id
        ORDER BY a.account_id ASC
    """)
    suspend fun getActiveAccountBalanceAggregates(): List<ActiveAccountBalanceAggregate>

    @Query("UPDATE transactions SET is_deleted = 1, deleted_at_epoch_ms = :deletedAtEpochMs, updated_at_epoch_ms = :updatedAtEpochMs WHERE transaction_id = :transactionId")
    suspend fun softDelete(transactionId: Long, deletedAtEpochMs: Long, updatedAtEpochMs: Long): Int

    @Transaction
    suspend fun correctTransaction(originalTransactionId: Long, replacement: TransactionEntity, correctedAtEpochMs: Long): Long {
        val original = getById(originalTransactionId)
        require(original != null && !original.isDeleted) { "Active transaction does not exist" }
        require(softDelete(originalTransactionId, correctedAtEpochMs, correctedAtEpochMs) == 1) { "Transaction correction failed" }
        val replacementId = insert(replacement)
        require(replacementId != -1L) { "Corrected transaction insertion failed" }
        return replacementId
    }
}
