package com.financialapp.data.repository

import com.financialapp.data.local.dao.AccountDao
import com.financialapp.data.local.entity.AccountEntity
import com.financialapp.domain.model.Account
import com.financialapp.domain.model.AccountType
import com.financialapp.domain.model.Money
import com.financialapp.domain.validation.FinancialValidators
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface AccountRepository {
    suspend fun create(account: Account): Long
    fun observeAll(): Flow<List<Account>>
    suspend fun getById(accountId: Long): Account?
    suspend fun getActiveAccounts(): List<Account>
    suspend fun activate(accountId: Long): Boolean
    suspend fun deactivate(accountId: Long): Boolean
    suspend fun delete(accountId: Long): Boolean
}

class LocalAccountRepository(
    private val accountDao: AccountDao,
) : AccountRepository {
    override suspend fun create(account: Account): Long {
        require(account.id == 0L) { "New account must not have an id" }
        FinancialValidators.validateAccount(account)
        return accountDao.upsert(account.toEntity())
    }

    override fun observeAll(): Flow<List<Account>> = accountDao.observeAll().map { accounts -> accounts.map(AccountEntity::toDomain) }

    override suspend fun getById(accountId: Long): Account? = accountDao.getById(accountId)?.toDomain()

    override suspend fun getActiveAccounts(): List<Account> = accountDao.getActiveAccounts().map(AccountEntity::toDomain)

    override suspend fun activate(accountId: Long): Boolean {
        val account = accountDao.getById(accountId) ?: throw IllegalArgumentException("Account does not exist")
        require(!account.isDeleted) { "Deleted accounts cannot be activated" }
        return accountDao.setActive(accountId, true, System.currentTimeMillis()) == 1
    }

    override suspend fun deactivate(accountId: Long): Boolean {
        val account = accountDao.getById(accountId) ?: throw IllegalArgumentException("Account does not exist")
        require(!account.isDeleted) { "Deleted accounts cannot be deactivated" }
        return accountDao.setActive(accountId, false, System.currentTimeMillis()) == 1
    }

    override suspend fun delete(accountId: Long): Boolean {
        val account = accountDao.getById(accountId) ?: throw IllegalArgumentException("Account does not exist")
        require(!account.isDeleted) { "Account is already deleted" }
        val now = System.currentTimeMillis()
        return accountDao.softDelete(accountId, now, now) == 1
    }
}

private fun Account.toEntity() = AccountEntity(
    accountType = com.financialapp.data.local.entity.AccountType.valueOf(type.name),
    name = name.trim(),
    currencyCode = openingBalance.currencyCode,
    openingBalanceMinor = openingBalance.minorUnits,
    isActive = isActive,
    isDeleted = isDeleted,
    createdAtEpochMs = createdAt.toEpochMilli(),
    updatedAtEpochMs = updatedAt.toEpochMilli(),
)

private fun AccountEntity.toDomain() = Account(
    id = accountId,
    name = name,
    type = AccountType.valueOf(accountType.name),
    openingBalance = Money(openingBalanceMinor, currencyCode),
    isActive = isActive,
    isDeleted = isDeleted,
    createdAt = java.time.Instant.ofEpochMilli(createdAtEpochMs),
    updatedAt = java.time.Instant.ofEpochMilli(updatedAtEpochMs),
)
