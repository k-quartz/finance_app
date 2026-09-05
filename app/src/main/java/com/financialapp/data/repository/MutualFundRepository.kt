package com.financialapp.data.repository

import androidx.room.withTransaction
import com.financialapp.data.local.dao.AccountDao
import com.financialapp.data.local.dao.MutualFundDao
import com.financialapp.data.local.dao.TransactionDao
import com.financialapp.data.local.database.AppDatabase
import com.financialapp.data.local.entity.AccountType
import com.financialapp.data.local.entity.InvestmentTransactionType
import com.financialapp.data.local.entity.MutualFundEntity
import com.financialapp.data.local.entity.MutualFundHoldingEntity
import com.financialapp.data.local.entity.MutualFundNavEntity
import com.financialapp.data.local.entity.MutualFundTransactionEntity
import com.financialapp.data.local.entity.TransactionType
import com.financialapp.domain.calculation.InvestmentCalculator
import com.financialapp.domain.model.Account
import com.financialapp.domain.model.AccountType as DomainAccountType
import com.financialapp.domain.model.FundUnits
import com.financialapp.domain.model.InvestmentTransactionType as DomainInvestmentTransactionType
import com.financialapp.domain.model.Money
import com.financialapp.domain.model.MutualFund
import com.financialapp.domain.model.MutualFundHolding
import com.financialapp.domain.model.MutualFundHoldingValue
import com.financialapp.domain.model.MutualFundPortfolioSummary
import com.financialapp.domain.model.MutualFundReturn
import com.financialapp.domain.model.MutualFundNavPoint
import com.financialapp.domain.model.MutualFundTransaction
import com.financialapp.domain.model.MutualFundValuation
import com.financialapp.domain.model.calculateInvestmentAmount
import com.financialapp.domain.model.calculateMutualFundReturn
import com.financialapp.domain.validation.InvestmentValidators
import java.time.Instant
import java.time.LocalDate

interface MutualFundRepository {
    suspend fun createFund(fund: MutualFund): Long
    suspend fun updateFund(fund: MutualFund): Long
    suspend fun deleteFund(fundId: Long, deletedAt: Instant): Boolean
    suspend fun getFund(fundId: Long): MutualFund?
    suspend fun getActiveFunds(): List<MutualFund>
    suspend fun recordPurchase(transaction: MutualFundTransaction): Long
    suspend fun recordRedemption(transaction: MutualFundTransaction): Long
    suspend fun getHolding(fundId: Long, investmentAccountId: Long): MutualFundHolding?
    suspend fun getPortfolioSummary(asOfDate: LocalDate): MutualFundPortfolioSummary
    suspend fun recordNav(fundId: Long, nav: Money, asOfDate: LocalDate, createdAt: Instant, source: String? = null, notes: String? = null): Long
    suspend fun valueAt(fundId: Long, investmentAccountId: Long, asOfDate: LocalDate): MutualFundValuation
    suspend fun calculateReturn(fundId: Long, investmentAccountId: Long, asOfDate: LocalDate): MutualFundReturn
    suspend fun getAllInvestmentTransactions(): List<MutualFundTransaction>
    suspend fun getAllNavHistory(): List<MutualFundNavPoint>
}

class LocalMutualFundRepository(
    private val database: AppDatabase,
    private val mutualFundDao: MutualFundDao,
    private val accountDao: AccountDao,
    private val transactionDao: TransactionDao,
) : MutualFundRepository {
    override suspend fun createFund(fund: MutualFund): Long {
        val normalized = fund.copy(name = fund.name.trim(), code = fund.code.trim().uppercase())
        InvestmentValidators.validateFund(normalized)
        require(!mutualFundDao.hasDuplicateNameOrCode(normalized.name, normalized.code, 0L)) {
            "A mutual fund with the same name or code already exists"
        }
        val now = System.currentTimeMillis()
        return mutualFundDao.insertFund(normalized.toEntity(now))
    }

    override suspend fun updateFund(fund: MutualFund): Long {
        require(fund.id > 0L) { "Existing mutual fund id is required" }
        val existing = mutualFundDao.getFund(fund.id) ?: throw IllegalArgumentException("Mutual fund does not exist")
        require(!existing.isDeleted) { "Deleted mutual funds cannot be updated" }
        require(!fund.isDeleted) { "Use delete to delete a mutual fund" }
        if (fund.name.isBlank()) throw IllegalArgumentException("Mutual fund name is required")
        if (fund.code.isBlank()) throw IllegalArgumentException("Mutual fund code is required")
        if (fund.currencyCode != "INR") throw IllegalArgumentException("Phase 10 supports INR only")
        val normalized = fund.copy(name = fund.name.trim(), code = fund.code.trim().uppercase())
        require(!mutualFundDao.hasDuplicateNameOrCode(normalized.name, normalized.code, normalized.id)) {
            "A mutual fund with the same name or code already exists"
        }
        val now = System.currentTimeMillis()
        require(
            mutualFundDao.updateFund(
                existing.copy(name = normalized.name, code = normalized.code, isActive = normalized.isActive, updatedAtEpochMs = now),
            ) == 1,
        ) { "Mutual fund update failed" }
        return fund.id
    }

    override suspend fun deleteFund(fundId: Long, deletedAt: Instant): Boolean {
        require(fundId > 0L) { "Mutual fund is required" }
        val existing = mutualFundDao.getFund(fundId) ?: throw IllegalArgumentException("Mutual fund does not exist")
        require(!existing.isDeleted) { "Mutual fund is already deleted" }
        return mutualFundDao.softDeleteFund(fundId, deletedAt.toEpochMilli()) == 1
    }

    override suspend fun getFund(fundId: Long): MutualFund? = mutualFundDao.getFund(fundId)?.takeUnless { it.isDeleted }?.toDomain()

    override suspend fun getActiveFunds(): List<MutualFund> = mutualFundDao.getActiveFunds().map { it.toDomain() }

    override suspend fun recordPurchase(transaction: MutualFundTransaction): Long = record(transaction, DomainInvestmentTransactionType.PURCHASE)

    override suspend fun recordRedemption(transaction: MutualFundTransaction): Long = record(transaction, DomainInvestmentTransactionType.REDEMPTION)

    private suspend fun record(transaction: MutualFundTransaction, expectedType: DomainInvestmentTransactionType): Long {
        require(transaction.type == expectedType) { "Investment transaction type does not match operation" }
        return database.withTransaction {
            val fund = mutualFundDao.getFund(transaction.fundId)?.toDomain() ?: throw IllegalArgumentException("Mutual fund does not exist")
            val investmentAccount = accountDao.getById(transaction.investmentAccountId)?.toDomain()
                ?: throw IllegalArgumentException("Investment account does not exist")
            val cashAccount = transaction.cashAccountId?.let { accountDao.getById(it)?.toDomain() ?: throw IllegalArgumentException("Cash account does not exist") }
            val linkedCash = transaction.linkedCashTransactionId?.let { transactionId ->
                transactionDao.getById(transactionId)?.takeUnless { it.isDeleted }
                    ?: throw IllegalArgumentException("Linked cash transaction does not exist")
            }
            val expectedCashType = if (expectedType == DomainInvestmentTransactionType.PURCHASE) TransactionType.EXPENSE else TransactionType.INCOME
            if (linkedCash != null) {
                require(linkedCash.transactionType == expectedCashType) { "Linked cash transaction direction is invalid" }
                require(linkedCash.accountId == transaction.cashAccountId) { "Linked cash transaction account is invalid" }
            }
            InvestmentValidators.validateTransaction(transaction, fund, investmentAccount, cashAccount, linkedCash?.let { Money(it.amountMinor, it.currencyCode) })
            ensureNavForTransactionDate(fund, transaction.nav, transaction.transactionDate, transaction.createdAt)
            val previous = mutualFundDao.getHolding(transaction.fundId, transaction.investmentAccountId)?.toDomain()
            val now = transaction.createdAt.toEpochMilli()
            val id = mutualFundDao.insertTransaction(transaction.toEntity(now))
            val next = InvestmentCalculator.reconcileHolding(previous, transaction, transaction.createdAt)
            val nextEntity = next.toEntity(previous?.let { mutualFundDao.getHolding(transaction.fundId, transaction.investmentAccountId) }?.holdingId ?: 0L)
            if (previous == null) mutualFundDao.insertHolding(nextEntity) else mutualFundDao.updateHolding(nextEntity)
            id
        }
    }

    override suspend fun getHolding(fundId: Long, investmentAccountId: Long): MutualFundHolding? =
        mutualFundDao.getHolding(fundId, investmentAccountId)?.toDomain()

    override suspend fun getPortfolioSummary(asOfDate: LocalDate): MutualFundPortfolioSummary {
        val activeFunds = mutualFundDao.getActiveFunds().map { it.toDomain() }.associateBy { it.id }
        val holdings = mutualFundDao.getHoldings().map { holdingEntity -> holdingEntity.toDomain() }
            .filter { activeFunds.containsKey(it.fundId) }
        val latestNavs = mutualFundDao.getLatestNavsOnOrBefore(asOfDate.toEpochDay())
            .groupBy { it.fundId }
            .mapValues { (_, entries) -> entries.first() }
        val holdingsWithValue = holdings.mapNotNull { holding ->
            val nav = latestNavs[holding.fundId]?.let { Money(it.navMinor, it.currencyCode) } ?: return@mapNotNull null
            MutualFundHoldingValue(
                fundId = holding.fundId,
                fundName = activeFunds[holding.fundId]?.name ?: return@mapNotNull null,
                investmentAccountId = holding.investmentAccountId,
                units = holding.units,
                nav = nav,
                value = calculateInvestmentAmount(nav, holding.units),
            )
        }.sortedWith(compareByDescending<MutualFundHoldingValue> { it.value.minorUnits }.thenBy { it.fundName }.thenBy { it.fundId })
        val totalValue = holdingsWithValue.fold(Money(0L, "INR")) { total, holding -> total + holding.value }
        return MutualFundPortfolioSummary(asOfDate, totalValue, holdingsWithValue)
    }

    override suspend fun recordNav(fundId: Long, nav: Money, asOfDate: LocalDate, createdAt: Instant, source: String?, notes: String?): Long {
        val fund = mutualFundDao.getFund(fundId)?.toDomain() ?: throw IllegalArgumentException("Mutual fund does not exist")
        require(!fund.isDeleted && fund.isActive) { "Inactive or deleted mutual funds cannot receive NAV" }
        require(nav.currencyCode == fund.currencyCode && nav.minorUnits > 0L) { "NAV must be positive and match fund currency" }
        val updated = mutualFundDao.updateNavForDate(fundId, asOfDate.toEpochDay(), nav.minorUnits, nav.currencyCode, source, notes, createdAt.toEpochMilli())
        if (updated > 0) return mutualFundDao.getLatestNav(fundId, asOfDate.toEpochDay())?.navId ?: 0L
        return mutualFundDao.insertNav(MutualFundNavEntity(fundId = fundId, asOfDateEpochDay = asOfDate.toEpochDay(), navMinor = nav.minorUnits, currencyCode = nav.currencyCode, source = source, notes = notes, createdAtEpochMs = createdAt.toEpochMilli()))
    }

    override suspend fun valueAt(fundId: Long, investmentAccountId: Long, asOfDate: LocalDate): MutualFundValuation {
        val fund = mutualFundDao.getFund(fundId)?.toDomain() ?: throw IllegalArgumentException("Mutual fund does not exist")
        val events = mutualFundDao.getTransactions(fundId, investmentAccountId).map(MutualFundTransactionEntity::toDomain).filter { !it.transactionDate.isAfter(asOfDate) }
        val holding = holdingAt(events, fundId, investmentAccountId, asOfDate)
        val nav = mutualFundDao.getLatestNav(fundId, asOfDate.toEpochDay())?.let { Money(it.navMinor, it.currencyCode) }
            ?: throw IllegalArgumentException("NAV is not available for the requested date")
        return MutualFundValuation(holding, nav, calculateInvestmentAmount(nav, holding.units), asOfDate)
    }

    override suspend fun calculateReturn(fundId: Long, investmentAccountId: Long, asOfDate: LocalDate): MutualFundReturn {
        val events = mutualFundDao.getTransactions(fundId, investmentAccountId).map(MutualFundTransactionEntity::toDomain).filter { !it.transactionDate.isAfter(asOfDate) }
        val valuation = valueAt(fundId, investmentAccountId, asOfDate)
        return calculateMutualFundReturn(events.filter { it.type == DomainInvestmentTransactionType.PURCHASE }, events.filter { it.type == DomainInvestmentTransactionType.REDEMPTION }, valuation.value)
    }

    override suspend fun getAllInvestmentTransactions(): List<MutualFundTransaction> =
        mutualFundDao.getAllTransactions().map(MutualFundTransactionEntity::toDomain)

    override suspend fun getAllNavHistory(): List<MutualFundNavPoint> =
        mutualFundDao.getAllNavHistory().map {
            MutualFundNavPoint(
                fundId = it.fundId,
                asOfDate = LocalDate.ofEpochDay(it.asOfDateEpochDay),
                nav = Money(it.navMinor, it.currencyCode),
            )
        }

    private suspend fun ensureNavForTransactionDate(
        fund: MutualFund,
        nav: Money,
        asOfDate: LocalDate,
        createdAt: Instant,
    ) {
        val epochDay = asOfDate.toEpochDay()
        val existing = mutualFundDao.getLatestNav(fund.id, epochDay)
        if (existing != null && existing.asOfDateEpochDay == epochDay) {
            return
        }
        mutualFundDao.insertNav(
            MutualFundNavEntity(
                fundId = fund.id,
                asOfDateEpochDay = epochDay,
                navMinor = nav.minorUnits,
                currencyCode = nav.currencyCode,
                source = "transaction",
                createdAtEpochMs = createdAt.toEpochMilli(),
            ),
        )
    }

    private fun holdingAt(events: List<MutualFundTransaction>, fundId: Long, accountId: Long, asOfDate: LocalDate): MutualFundHolding {
        var holding: MutualFundHolding? = null
        events.forEach { holding = InvestmentCalculator.reconcileHolding(holding, it, it.createdAt) }
        return holding ?: MutualFundHolding(fundId, accountId, FundUnits(0L, 6), Instant.EPOCH)
    }
}

private fun MutualFund.toEntity(now: Long) = MutualFundEntity(fundId = id, name = name.trim(), code = code.trim(), currencyCode = currencyCode, isActive = isActive, isDeleted = isDeleted, createdAtEpochMs = now, updatedAtEpochMs = now)
private fun MutualFundEntity.toDomain() = MutualFund(fundId, name, code, currencyCode, isActive, isDeleted)
private fun MutualFundHolding.toEntity(id: Long) = MutualFundHoldingEntity(id, fundId, investmentAccountId, units.minorUnits, units.scale, updatedAt.toEpochMilli())
private fun MutualFundHoldingEntity.toDomain() = MutualFundHolding(fundId, investmentAccountId, FundUnits(unitsMinor, unitsScale), Instant.ofEpochMilli(updatedAtEpochMs))
private fun MutualFundTransaction.toEntity(now: Long) = MutualFundTransactionEntity(id, fundId, investmentAccountId, cashAccountId, InvestmentTransactionType.valueOf(type.name), units.minorUnits, units.scale, nav.minorUnits, totalAmount.minorUnits, totalAmount.currencyCode, transactionDate.toEpochDay(), linkedCashTransactionId, notes, now)
private fun MutualFundTransactionEntity.toDomain() = MutualFundTransaction(investmentTransactionId, fundId, investmentAccountId, cashAccountId, DomainInvestmentTransactionType.valueOf(transactionType.name), FundUnits(unitsMinor, unitsScale), Money(navMinor, currencyCode), Money(totalAmountMinor, currencyCode), LocalDate.ofEpochDay(transactionDateEpochDay), linkedCashTransactionId, notes, Instant.ofEpochMilli(createdAtEpochMs))
private fun com.financialapp.data.local.entity.AccountEntity.toDomain() = Account(accountId, name, DomainAccountType.valueOf(accountType.name), Money(openingBalanceMinor, currencyCode), isActive, isDeleted, Instant.ofEpochMilli(createdAtEpochMs), Instant.ofEpochMilli(updatedAtEpochMs))
