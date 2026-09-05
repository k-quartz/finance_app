package com.financialapp.data.repository

import com.financialapp.data.local.dao.AccountDao
import com.financialapp.data.local.dao.CategoryDao
import com.financialapp.data.local.dao.TransactionDao
import com.financialapp.data.local.dao.ActiveAccountBalanceAggregate
import com.financialapp.data.local.entity.AccountEntity
import com.financialapp.data.local.entity.AccountType
import com.financialapp.data.local.entity.CategoryEntity
import com.financialapp.data.local.entity.CategoryType
import com.financialapp.data.local.entity.TransactionEntity
import com.financialapp.data.local.entity.TransactionType
import com.financialapp.domain.model.Account
import com.financialapp.domain.model.AccountBalance
import com.financialapp.domain.model.Category
import com.financialapp.domain.model.FinancialTransaction
import com.financialapp.domain.model.Money
import com.financialapp.domain.model.Transfer
import com.financialapp.domain.model.DuplicateTransaction
import com.financialapp.domain.model.TransactionSummary
import com.financialapp.domain.model.ReportingPeriod
import com.financialapp.domain.model.TransactionAnalyticsReport
import com.financialapp.domain.model.RecurringExpensePattern
import com.financialapp.domain.calculation.TransactionAnalytics
import com.financialapp.domain.calculation.RecurringExpenseDetector
import java.time.Instant
import java.time.LocalDate
import com.financialapp.domain.validation.FinancialValidators

interface FinancialRepository {
    suspend fun createAccount(account: Account): Long
    suspend fun createCategory(category: Category): Long
    suspend fun createTransaction(transaction: FinancialTransaction): Long
    suspend fun createTransfer(transfer: Transfer): Long
    suspend fun calculateAccountBalance(accountId: Long): AccountBalance
    suspend fun getActiveAccountBalances(): List<AccountBalance>
    suspend fun getTransactions(accountId: Long): List<FinancialTransaction>
    suspend fun getTransactions(startDate: LocalDate, endDate: LocalDate): List<FinancialTransaction>
    suspend fun summarize(startDate: LocalDate, endDate: LocalDate, accountId: Long? = null): TransactionSummary
    suspend fun analyze(period: ReportingPeriod, accountId: Long? = null): TransactionAnalyticsReport
    suspend fun detectRecurringExpenses(startDate: LocalDate, endDate: LocalDate, accountId: Long? = null): List<RecurringExpensePattern>
    suspend fun findPotentialDuplicates(transaction: FinancialTransaction): List<DuplicateTransaction>
    suspend fun correctTransaction(originalTransactionId: Long, replacement: FinancialTransaction): Long
    suspend fun deleteTransaction(transactionId: Long, deletedAt: Instant): Boolean
    suspend fun updateAccount(account: Account): Long
    suspend fun deleteAccount(accountId: Long, deletedAt: Instant): Boolean
}

class LocalFinancialRepository(
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao,
) : FinancialRepository {
    override suspend fun createAccount(account: Account): Long {
        FinancialValidators.validateAccount(account)
        return accountDao.upsert(
            AccountEntity(
                accountId = account.id,
                accountType = AccountType.valueOf(account.type.name),
                name = account.name,
                currencyCode = account.openingBalance.currencyCode,
                openingBalanceMinor = account.openingBalance.minorUnits,
                isActive = account.isActive,
                isDeleted = account.isDeleted,
                createdAtEpochMs = account.createdAt.toEpochMilli(),
                updatedAtEpochMs = account.updatedAt.toEpochMilli(),
            ),
        )
    }

    override suspend fun createCategory(category: Category): Long {
        require(category.id == 0L) { "New category must not have an id" }
        require(!category.isDeleted) { "New category cannot be deleted" }
        val ancestors = categoryAncestors(category.parentId)
        FinancialValidators.validateCategory(category, ancestors.firstOrNull(), ancestors.drop(1))
        return categoryDao.insertIfUnique(
            CategoryEntity(
                categoryId = category.id,
                parentCategoryId = category.parentId,
                categoryType = CategoryType.valueOf(category.type.name),
                name = category.name,
                sortOrder = category.sortOrder,
                isActive = category.isActive,
                isDeleted = category.isDeleted,
                createdAtEpochMs = System.currentTimeMillis(),
                updatedAtEpochMs = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun createTransaction(transaction: FinancialTransaction): Long {
        val account = accountDao.getById(transaction.accountId)
            ?: throw IllegalArgumentException("Account does not exist")
        if (!account.isActive || account.isDeleted) throw IllegalArgumentException("Inactive accounts cannot receive new transactions")
        val category = transaction.categoryId?.let { categoryDao.getById(it)?.toDomain() }
            ?: if (transaction.categoryId != null) throw IllegalArgumentException("Category does not exist") else null
        val ancestors = category?.let { categoryAncestors(it.parentId) } ?: emptyList()
        FinancialValidators.validateTransaction(transaction, category, ancestors)
        if (transaction.amount.currencyCode != account.currencyCode) {
            throw IllegalArgumentException("Transaction currency must match account currency")
        }
        return transactionDao.insert(transaction.toEntity())
    }

    override suspend fun createTransfer(transfer: Transfer): Long {
        FinancialValidators.validateTransfer(transfer)
        val source = accountDao.getById(transfer.sourceAccountId)
            ?: throw IllegalArgumentException("Source account does not exist")
        val destination = accountDao.getById(transfer.destinationAccountId)
            ?: throw IllegalArgumentException("Destination account does not exist")
        if (!source.isActive || source.isDeleted) throw IllegalArgumentException("Source account is inactive or deleted")
        if (!destination.isActive || destination.isDeleted) throw IllegalArgumentException("Destination account is inactive or deleted")
        if (source.currencyCode != destination.currencyCode || source.currencyCode != transfer.amount.currencyCode) {
            throw IllegalArgumentException("Transfer accounts and amount must use the same currency")
        }
        val existing = transactionDao.getByTransferId(transfer.transferId)
        if (existing != null) return existing.transactionId
        val transactionId = transactionDao.insert(
            TransactionEntity(
                accountId = transfer.sourceAccountId,
                counterpartyAccountId = transfer.destinationAccountId,
                transferId = transfer.transferId,
                transactionType = TransactionType.TRANSFER,
                amountMinor = transfer.amount.minorUnits,
                currencyCode = transfer.amount.currencyCode,
                occurredDateEpochDay = transfer.transactionDate.toEpochDay(),
                postedAtEpochMs = transfer.createdAt.toEpochMilli(),
                createdAtEpochMs = transfer.createdAt.toEpochMilli(),
                updatedAtEpochMs = transfer.createdAt.toEpochMilli(),
            ),
        )
        if (transactionId != -1L) return transactionId
        return transactionDao.getByTransferId(transfer.transferId)?.transactionId
            ?: throw IllegalStateException("Transfer insertion conflict without an existing transfer")
    }

    override suspend fun calculateAccountBalance(accountId: Long): AccountBalance {
        val account = accountDao.getById(accountId) ?: throw IllegalArgumentException("Account does not exist")
        val aggregate = transactionDao.getAccountBalanceAggregate(accountId).effectMinor ?: 0L
        return AccountBalance(accountId, Money(Math.addExact(account.openingBalanceMinor, aggregate), account.currencyCode), com.financialapp.domain.model.AccountType.valueOf(account.accountType.name))
    }

    override suspend fun getActiveAccountBalances(): List<AccountBalance> {
        val accounts = accountDao.getActiveAccounts()
        val aggregatesByAccount = transactionDao.getActiveAccountBalanceAggregates().associateBy { it.accountId }
        return accounts.map { account ->
            val aggregate = aggregatesByAccount[account.accountId]?.effectMinor ?: 0L
            AccountBalance(
                accountId = account.accountId,
                amount = Money(Math.addExact(account.openingBalanceMinor, aggregate), account.currencyCode),
                accountType = com.financialapp.domain.model.AccountType.valueOf(account.accountType.name),
            )
        }
    }

    override suspend fun getTransactions(accountId: Long): List<FinancialTransaction> {
        require(accountId > 0) { "Account is required" }
        return transactionDao.getTransactionsForAccount(accountId).map { it.toDomain() }
    }

    override suspend fun getTransactions(startDate: LocalDate, endDate: LocalDate): List<FinancialTransaction> {
        require(!endDate.isBefore(startDate)) { "End date cannot precede start date" }
        return transactionDao.getTransactionsForDateRange(startDate.toEpochDay(), endDate.toEpochDay()).map { it.toDomain() }
    }

    override suspend fun summarize(startDate: LocalDate, endDate: LocalDate, accountId: Long?): TransactionSummary {
        val transactions = if (accountId == null) {
            getTransactions(startDate, endDate)
        } else {
            require(accountId > 0) { "Account is required" }
            transactionDao.getTransactionsForAccountAndDateRange(accountId, startDate.toEpochDay(), endDate.toEpochDay()).map { it.toDomain() }
        }
        var income = 0L
        var expense = 0L
        val categoryTotals = linkedMapOf<Long, Long>()
        transactions.forEach { transaction ->
            when (transaction.type) {
                com.financialapp.domain.model.TransactionType.INCOME -> income = Math.addExact(income, transaction.amount.minorUnits)
                com.financialapp.domain.model.TransactionType.EXPENSE -> {
                    expense = Math.addExact(expense, transaction.amount.minorUnits)
                    transaction.categoryId?.let { categoryId ->
                        categoryTotals[categoryId] = Math.addExact(categoryTotals[categoryId] ?: 0L, transaction.amount.minorUnits)
                    }
                }
                com.financialapp.domain.model.TransactionType.TRANSFER -> Unit
            }
        }
        return TransactionSummary(Money(income, "INR"), Money(expense, "INR"), categoryTotals.mapValues { Money(it.value, "INR") })
    }

    override suspend fun analyze(period: ReportingPeriod, accountId: Long?): TransactionAnalyticsReport {
        val transactions = if (accountId == null) {
            getTransactions(period.startDate, period.endDate)
        } else {
            require(accountId > 0) { "Account is required" }
            transactionDao.getTransactionsForAccountAndDateRange(accountId, period.startDate.toEpochDay(), period.endDate.toEpochDay()).map { it.toDomain() }
        }
        return TransactionAnalytics.analyze(transactions, period)
    }

    override suspend fun detectRecurringExpenses(startDate: LocalDate, endDate: LocalDate, accountId: Long?): List<RecurringExpensePattern> {
        require(!endDate.isBefore(startDate)) { "End date cannot precede start date" }
        val transactions = if (accountId == null) {
            getTransactions(startDate, endDate)
        } else {
            require(accountId > 0) { "Account is required" }
            transactionDao.getTransactionsForAccountAndDateRange(accountId, startDate.toEpochDay(), endDate.toEpochDay()).map { it.toDomain() }
        }
        return RecurringExpenseDetector.detect(transactions, startDate, endDate)
    }

    override suspend fun findPotentialDuplicates(transaction: FinancialTransaction): List<DuplicateTransaction> {
        val category = transaction.categoryId?.let { categoryDao.getById(it)?.toDomain() }
            ?: if (transaction.categoryId != null) throw IllegalArgumentException("Category does not exist") else null
        val ancestors = category?.let { categoryAncestors(it.parentId) } ?: emptyList()
        FinancialValidators.validateTransaction(transaction.copy(id = 0L), category, ancestors)
        return transactionDao.findPotentialDuplicates(
            transaction.accountId,
            transaction.type.name,
            transaction.amount.minorUnits,
            transaction.transactionDate.toEpochDay(),
            transaction.categoryId,
            transaction.id,
        ).map { it.toDomain() }
            .filter { normalizeMerchant(it.merchantOrPayee) == normalizeMerchant(transaction.merchantOrPayee) }
            .map { DuplicateTransaction(it, setOf("accountId", "type", "amount", "categoryId", "transactionDate", "merchantOrPayee")) }
    }

    override suspend fun correctTransaction(originalTransactionId: Long, replacement: FinancialTransaction): Long {
        require(originalTransactionId > 0) { "Original transaction is required" }
        require(replacement.id == 0L) { "Corrected transaction must be new" }
        val original = transactionDao.getById(originalTransactionId)
            ?: throw IllegalArgumentException("Original transaction does not exist")
        require(!original.isDeleted) { "Original transaction is already deleted" }
        require(original.transactionType != com.financialapp.data.local.entity.TransactionType.TRANSFER) {
            "Transfers cannot be corrected as income or expense transactions"
        }
        val account = accountDao.getById(replacement.accountId) ?: throw IllegalArgumentException("Account does not exist")
        if (!account.isActive || account.isDeleted) throw IllegalArgumentException("Inactive accounts cannot receive new transactions")
        val category = replacement.categoryId?.let { categoryDao.getById(it)?.toDomain() }
            ?: if (replacement.categoryId != null) throw IllegalArgumentException("Category does not exist") else null
        val ancestors = category?.let { categoryAncestors(it.parentId) } ?: emptyList()
        FinancialValidators.validateTransaction(replacement, category, ancestors)
        require(replacement.amount.currencyCode == account.currencyCode) { "Transaction currency must match account currency" }
        return transactionDao.correctTransaction(originalTransactionId, replacement.toEntity(), replacement.createdAt.toEpochMilli())
    }

    override suspend fun deleteTransaction(transactionId: Long, deletedAt: Instant): Boolean {
        require(transactionId > 0) { "Transaction is required" }
        val existing = transactionDao.getById(transactionId) ?: throw IllegalArgumentException("Transaction does not exist")
        require(!existing.isDeleted) { "Transaction is already deleted" }
        val deletedAtMs = deletedAt.toEpochMilli()
        return transactionDao.softDelete(transactionId, deletedAtMs, deletedAtMs) == 1
    }

    override suspend fun updateAccount(account: Account): Long {
        require(account.id > 0L) { "Existing account id is required" }
        val existing = accountDao.getById(account.id) ?: throw IllegalArgumentException("Account does not exist")
        require(!existing.isDeleted) { "Deleted accounts cannot be updated" }
        require(!account.isDeleted) { "Use delete to delete an account" }
        FinancialValidators.validateAccount(account)
        return accountDao.upsert(
            AccountEntity(
                accountId = account.id,
                accountType = AccountType.valueOf(account.type.name),
                name = account.name,
                currencyCode = account.openingBalance.currencyCode,
                openingBalanceMinor = account.openingBalance.minorUnits,
                isActive = account.isActive,
                isDeleted = existing.isDeleted,
                createdAtEpochMs = existing.createdAtEpochMs,
                updatedAtEpochMs = account.updatedAt.toEpochMilli(),
                notes = existing.notes,
            ),
        )
    }

    override suspend fun deleteAccount(accountId: Long, deletedAt: Instant): Boolean {
        require(accountId > 0) { "Account is required" }
        val existing = accountDao.getById(accountId) ?: throw IllegalArgumentException("Account does not exist")
        require(!existing.isDeleted) { "Account is already deleted" }
        val deletedAtMs = deletedAt.toEpochMilli()
        return accountDao.softDelete(accountId, deletedAtMs, deletedAtMs) == 1
    }

    private suspend fun categoryAncestors(parentId: Long?): List<Category> {
        val ancestors = mutableListOf<Category>()
        val visited = mutableSetOf<Long>()
        var currentId = parentId
        while (currentId != null) {
            if (!visited.add(currentId)) throw IllegalArgumentException("Category hierarchy contains a cycle")
            val current = categoryDao.getById(currentId) ?: throw IllegalArgumentException("Parent category does not exist")
            require(current.isActive && !current.isDeleted) { "Parent category must be active and not deleted" }
            ancestors += current.toDomain()
            currentId = current.parentCategoryId
        }
        return ancestors
    }

    private fun normalizeMerchant(value: String?): String? = value?.trim()?.lowercase()?.replace(Regex("\\s+"), " ")
}

private fun FinancialTransaction.toEntity(): TransactionEntity = TransactionEntity(
    transactionId = id,
    accountId = accountId,
    categoryId = categoryId,
    transactionType = TransactionType.valueOf(type.name),
    amountMinor = amount.minorUnits,
    currencyCode = amount.currencyCode,
    occurredDateEpochDay = transactionDate.toEpochDay(),
    postedAtEpochMs = createdAt.toEpochMilli(),
    notes = notes,
    merchantName = merchantOrPayee,
    transferId = transferId,
    createdAtEpochMs = createdAt.toEpochMilli(),
    updatedAtEpochMs = createdAt.toEpochMilli(),
)

private fun CategoryEntity.toDomain(): Category = Category(
    id = categoryId,
    parentId = parentCategoryId,
    type = com.financialapp.domain.model.CategoryType.valueOf(categoryType.name),
    name = name,
    sortOrder = sortOrder,
    isActive = isActive,
    isDeleted = isDeleted,
)

private fun TransactionEntity.toDomain(): FinancialTransaction = FinancialTransaction(
    id = transactionId,
    accountId = accountId,
    type = com.financialapp.domain.model.TransactionType.valueOf(transactionType.name),
    amount = Money(amountMinor, currencyCode),
    categoryId = categoryId,
    transactionDate = LocalDate.ofEpochDay(occurredDateEpochDay),
    createdAt = Instant.ofEpochMilli(createdAtEpochMs),
    notes = notes,
    merchantOrPayee = merchantName,
    transferId = transferId,
)
