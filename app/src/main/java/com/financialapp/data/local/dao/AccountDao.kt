package com.financialapp.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.financialapp.data.local.entity.AccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Upsert
    suspend fun upsert(account: AccountEntity): Long

    @Query("SELECT * FROM accounts ORDER BY account_id ASC")
    suspend fun getAll(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE is_deleted = 0 ORDER BY updated_at_epoch_ms DESC")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE account_id = :accountId")
    suspend fun getById(accountId: Long): AccountEntity?

    @Query("SELECT * FROM accounts WHERE is_deleted = 0 AND is_active = 1 ORDER BY name ASC")
    suspend fun getActiveAccounts(): List<AccountEntity>

    @Query("UPDATE accounts SET is_active = :isActive, updated_at_epoch_ms = :updatedAtEpochMs WHERE account_id = :accountId AND is_deleted = 0")
    suspend fun setActive(accountId: Long, isActive: Boolean, updatedAtEpochMs: Long): Int

    @Query("UPDATE accounts SET is_deleted = 1, deleted_at_epoch_ms = :deletedAtEpochMs, updated_at_epoch_ms = :updatedAtEpochMs WHERE account_id = :accountId")
    suspend fun softDelete(accountId: Long, deletedAtEpochMs: Long, updatedAtEpochMs: Long): Int
}
