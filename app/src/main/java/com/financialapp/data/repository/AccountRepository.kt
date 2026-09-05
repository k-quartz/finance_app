package com.financialapp.data.repository

import com.financialapp.data.local.dao.AccountDao
import com.financialapp.data.local.entity.AccountEntity
import kotlinx.coroutines.flow.Flow

interface AccountRepository {
    suspend fun upsert(account: AccountEntity): Long
    fun observeAll(): Flow<List<AccountEntity>>
    suspend fun getById(accountId: Long): AccountEntity?
    suspend fun getActiveAccounts(): List<AccountEntity>
    suspend fun softDelete(accountId: Long, deletedAtEpochMs: Long, updatedAtEpochMs: Long): Int
}

class LocalAccountRepository(
    private val accountDao: AccountDao,
) : AccountRepository {
    override suspend fun upsert(account: AccountEntity): Long = accountDao.upsert(account)

    override fun observeAll(): Flow<List<AccountEntity>> = accountDao.observeAll()

    override suspend fun getById(accountId: Long): AccountEntity? = accountDao.getById(accountId)

    override suspend fun getActiveAccounts(): List<AccountEntity> = accountDao.getActiveAccounts()

    override suspend fun softDelete(accountId: Long, deletedAtEpochMs: Long, updatedAtEpochMs: Long): Int =
        accountDao.softDelete(accountId, deletedAtEpochMs, updatedAtEpochMs)
}
